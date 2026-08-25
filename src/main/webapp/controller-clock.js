(function () {
  const BOOTSTRAP_ID = 'controller-clock-bootstrap';
  const WIDGET_ID = 'controller-clock';
  const RESYNC_INTERVAL_MS = 5 * 60 * 1000;
  const STALE_AFTER_MS = 10 * 60 * 1000;

  function labelsFromBootstrap(bootstrap) {
    const dataset = bootstrap.dataset || {};
    return {
      synchronizing: dataset.labelSynchronizing || 'Synchronizing...',
      waiting: dataset.labelWaiting || 'Waiting for controller time.',
      syncCurrent: dataset.labelSyncCurrent || 'Current',
      syncStale: dataset.labelSyncStale || 'Stale',
      syncUnavailable: dataset.labelSyncUnavailable || 'Unavailable',
      syncWaiting: dataset.labelSyncWaiting || 'Waiting',
      unknown: dataset.labelUnknown || 'unknown',
    };
  }

  function findFooterMount() {
    return document.querySelector('.page-footer__links');
  }

  function mountWidget(widget) {
    const footerLinks = findFooterMount();
    if (!footerLinks) {
      return false;
    }
    if (widget.parentNode !== footerLinks) {
      footerLinks.prepend(widget);
    }
    return true;
  }

  function createState() {
    const bootstrap = document.getElementById(BOOTSTRAP_ID);
    const widget = document.getElementById(WIDGET_ID);
    const trigger = document.getElementById('controller-clock-trigger');
    const value = document.getElementById('controller-clock-value');
    const template = widget ? widget.querySelector('template') : null;
    const customTemplate = template && template.content
      ? template.content.querySelector('template[data-dropdown-type="CUSTOM"]')
      : null;
    const popupTemplateContent = customTemplate && customTemplate.content
      ? customTemplate.content
      : null;
    const popupTemplateTime = popupTemplateContent
      ? popupTemplateContent.querySelector('#controller-clock-popup-time')
      : null;
    const popupTemplateStatus = popupTemplateContent
      ? popupTemplateContent.querySelector('#controller-clock-popup-status')
      : null;

    if (!bootstrap || !widget || !trigger || !value || !template || !customTemplate || !popupTemplateTime || !popupTemplateStatus) {
      return null;
    }

    return {
      bootstrap: bootstrap,
      labels: labelsFromBootstrap(bootstrap),
      widget: widget,
      trigger: trigger,
      value: value,
      popupTemplateContent: popupTemplateContent,
      popupTemplateTime: popupTemplateTime,
      popupTemplateStatus: popupTemplateStatus,
      controllerSyncUrl: bootstrap.getAttribute('data-sync-url') || '',
      lastSyncEpochMs: null,
      lastSyncPerfMs: null,
      syncState: 'waiting',
      syncFailed: false,
      syncTimer: null,
      tickTimer: null,
      hidden: document.hidden,
      controllerMeta: null,
      visibleHandler: null,
      mountObserver: null,
      destroyed: false,
    };
  }

  function currentControllerNow(state) {
    if (state.lastSyncEpochMs == null || state.lastSyncPerfMs == null) {
      return null;
    }
    return state.lastSyncEpochMs + (performance.now() - state.lastSyncPerfMs);
  }

  function formatOffset(minutes, unknownLabel) {
    if (typeof minutes !== 'number' || Number.isNaN(minutes)) {
      return unknownLabel;
    }
    const sign = minutes >= 0 ? '+' : '-';
    const total = Math.abs(minutes);
    const hours = String(Math.floor(total / 60)).padStart(2, '0');
    const mins = String(total % 60).padStart(2, '0');
    return `UTC${sign}${hours}:${mins}`;
  }

  function browserTimeZone(labels) {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone || labels.unknown;
    } catch (error) {
      return labels.unknown;
    }
  }

  function browserUtcOffset() {
    return -new Date().getTimezoneOffset();
  }

  function formatControllerTime(epochMs, controllerZoneId) {
    const date = new Date(epochMs);
    const options = {
      hour12: false,
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    };
    try {
      return new Intl.DateTimeFormat(undefined, Object.assign({ timeZone: controllerZoneId }, options)).format(date);
    } catch (error) {
      try {
        return date.toLocaleString([], options);
      } catch (fallbackError) {
        return String(epochMs);
      }
    }
  }

  function syncStatusText(state, stale) {
    if (state.lastSyncEpochMs == null) {
      if (state.syncFailed) {
        return state.labels.syncUnavailable;
      }
      return state.syncState === 'waiting' ? state.labels.syncWaiting : state.labels.syncUnavailable;
    }
    return stale ? state.labels.syncStale : state.labels.syncCurrent;
  }

  function updatePopupState(state, timeText, statusText) {
    state.value.textContent = timeText;
    if (state.popupTemplateContent) {
      const templateTime = state.popupTemplateContent.querySelector('#controller-clock-popup-time');
      if (templateTime) {
        templateTime.textContent = timeText;
      }
      const templateStatus = state.popupTemplateContent.querySelector('#controller-clock-popup-status');
      if (templateStatus) {
        templateStatus.textContent = statusText;
      }
    }
    if (state.popupTemplateTime) {
      state.popupTemplateTime.textContent = timeText;
    }
    if (state.popupTemplateStatus) {
      state.popupTemplateStatus.textContent = statusText;
    }

    const tippyRef = state.trigger._tippy;
    if (tippyRef && tippyRef.popper) {
      const liveTime = tippyRef.popper.querySelector('#controller-clock-popup-time');
      if (liveTime) {
        liveTime.textContent = timeText;
      }
      const liveStatus = tippyRef.popper.querySelector('#controller-clock-popup-status');
      if (liveStatus) {
        liveStatus.textContent = statusText;
      }
    }
  }

  function render(state) {
    const controllerNow = currentControllerNow(state);
    const controllerZoneId = state.controllerMeta && state.controllerMeta.controllerTimeZoneId
      ? state.controllerMeta.controllerTimeZoneId
      : state.labels.unknown;
    const controllerUtcOffset = state.controllerMeta && typeof state.controllerMeta.controllerUtcOffsetMinutes === 'number'
      ? state.controllerMeta.controllerUtcOffsetMinutes
      : null;
    const browserZoneId = browserTimeZone(state.labels);
    const browserOffset = browserUtcOffset();
    const stale = state.lastSyncPerfMs != null && (performance.now() - state.lastSyncPerfMs) > STALE_AFTER_MS;
    const timeText = controllerNow == null ? state.labels.synchronizing : formatControllerTime(controllerNow, controllerZoneId);
    const statusText = syncStatusText(state, stale);

    updatePopupState(state, timeText, statusText);

    state.widget.setAttribute('data-stale', String(stale));
    state.widget.setAttribute('data-sync-state', state.lastSyncEpochMs == null
      ? state.syncState
      : (stale ? 'stale' : 'current'));
    state.widget.setAttribute('data-controller-zone', controllerZoneId);
    state.widget.setAttribute('data-controller-offset', formatOffset(controllerUtcOffset, state.labels.unknown));
    state.widget.setAttribute('data-browser-zone', browserZoneId);
    state.widget.setAttribute('data-browser-offset', formatOffset(browserOffset, state.labels.unknown));
  }

  async function sync(state) {
    if (!state.controllerSyncUrl) {
      state.syncFailed = true;
      state.syncState = 'unavailable';
      render(state);
      return false;
    }

    const t0 = performance.now();
    try {
      const response = await fetch(state.controllerSyncUrl, {
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {
          Accept: 'application/json',
        },
      });
      const t1 = performance.now();
      if (!response.ok) {
        state.syncFailed = true;
        if (state.lastSyncEpochMs == null) {
          state.syncState = 'unavailable';
        }
        render(state);
        return false;
      }

      const payload = await response.json();
      const epochMs = Number(payload.epochMillis);
      if (!Number.isFinite(epochMs)) {
        state.syncFailed = true;
        if (state.lastSyncEpochMs == null) {
          state.syncState = 'unavailable';
        }
        render(state);
        return false;
      }

      state.lastSyncEpochMs = epochMs + ((t1 - t0) / 2);
      state.lastSyncPerfMs = t1;
      state.controllerMeta = payload;
      state.syncState = 'current';
      state.syncFailed = false;
      render(state);
      return true;
    } catch (error) {
      state.syncFailed = true;
      if (state.lastSyncEpochMs == null) {
        state.syncState = 'unavailable';
      }
      render(state);
      return false;
    }
  }

  function clearTimers(state) {
    if (state.tickTimer) {
      clearInterval(state.tickTimer);
      state.tickTimer = null;
    }
    if (state.syncTimer) {
      clearTimeout(state.syncTimer);
      state.syncTimer = null;
    }
  }

  function scheduleTimers(state) {
    clearTimers(state);
    if (state.destroyed || document.hidden) {
      return;
    }

    state.tickTimer = setInterval(function () {
      if (!state.destroyed && !document.hidden) {
        render(state);
      }
    }, 1000);

    state.syncTimer = setTimeout(async function () {
      if (state.destroyed || document.hidden) {
        return;
      }
      await sync(state);
      scheduleTimers(state);
    }, RESYNC_INTERVAL_MS);
  }

  function destroy(state) {
    state.destroyed = true;
    clearTimers(state);
    if (state.mountObserver) {
      state.mountObserver.disconnect();
      state.mountObserver = null;
    }
    if (state.visibleHandler) {
      document.removeEventListener('visibilitychange', state.visibleHandler);
      state.visibleHandler = null;
    }
    if (window.__controllerClockState === state) {
      window.__controllerClockState = null;
    }
  }

  async function start(state) {
    if (!state) {
      return;
    }

    state.visibleHandler = function () {
      state.hidden = document.hidden;
      if (state.hidden) {
        clearTimers(state);
        return;
      }
      sync(state).then(function () {
        scheduleTimers(state);
      });
    };

    document.addEventListener('visibilitychange', state.visibleHandler);
    window.addEventListener('pagehide', function () {
      destroy(state);
    }, { once: true });

    if (!mountWidget(state.widget)) {
      const observer = new MutationObserver(function () {
        if (mountWidget(state.widget)) {
          observer.disconnect();
          state.mountObserver = null;
        }
      });
      state.mountObserver = observer;
      observer.observe(document.documentElement, { childList: true, subtree: true });
    }

    if (!document.hidden) {
      await sync(state);
      scheduleTimers(state);
    } else {
      render(state);
    }
  }

  function init() {
    if (window.__controllerClockState) {
      return;
    }
    const state = createState();
    if (!state) {
      return;
    }

    window.__controllerClockState = state;
    start(state);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
}());
