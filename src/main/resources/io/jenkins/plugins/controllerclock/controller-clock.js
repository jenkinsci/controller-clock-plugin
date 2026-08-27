(function () {
  const GLOBAL_CONTROL_ID = 'controller-clock-global';
  const GLOBAL_VALUE_ID = 'controller-clock-global-value';
  const WIDGET_ID = 'controller-clock';
  const RESYNC_INTERVAL_MS = 5 * 60 * 1000;
  const STALE_AFTER_MS = 10 * 60 * 1000;

  function labelsFromWidget(widget) {
    const dataset = widget.dataset || {};
    return {
      controllerTime: dataset.labelControllerTime || 'Controller time',
      synchronizing: dataset.labelSynchronizing || 'Synchronizing...',
      unknown: dataset.labelUnknown || 'unknown',
    };
  }

  function createState() {
    const widget = document.getElementById(WIDGET_ID);

    if (!widget) {
      return null;
    }

    return {
      labels: labelsFromWidget(widget),
      globalControl: null,
      globalValue: null,
      widget: widget,
      controllerSyncUrl: widget.getAttribute('data-sync-url') || '',
      lastSyncEpochMs: null,
      lastSyncPerfMs: null,
      syncState: 'waiting',
      syncTimer: null,
      tickTimer: null,
      controllerMeta: null,
      visibleHandler: null,
      destroyed: false,
    };
  }

  function resolveWidgetParts(state) {
    const widget = state.widget;
    state.globalControl = widget.querySelector('#' + GLOBAL_CONTROL_ID);
    state.globalValue = widget.querySelector('#' + GLOBAL_VALUE_ID);
    return Boolean(state.globalControl && state.globalValue);
  }

  function controllerButtonLabel(state, controllerNow, controllerZoneId) {
    if (controllerNow == null) {
      return state.labels.controllerTime + ': ' + state.labels.synchronizing;
    }

    const zoneSuffix = controllerZoneId && controllerZoneId !== state.labels.unknown
      ? ' (' + controllerZoneId + ')'
      : '';
    return state.labels.controllerTime + ': ' + formatControllerTime(controllerNow, controllerZoneId) + zoneSuffix;
  }

  function updateControlMetadata(state, controllerNow, controllerZoneId) {
    const label = controllerButtonLabel(state, controllerNow, controllerZoneId);
    state.globalControl.setAttribute('title', label);
    state.globalControl.setAttribute('aria-label', label);
  }

  function currentControllerNow(state) {
    if (state.lastSyncEpochMs == null || state.lastSyncPerfMs == null) {
      return null;
    }
    return state.lastSyncEpochMs + (performance.now() - state.lastSyncPerfMs);
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

  function effectiveSyncState(state) {
    if (state.syncState === 'current'
        && state.lastSyncPerfMs != null
        && performance.now() - state.lastSyncPerfMs > STALE_AFTER_MS) {
      return 'stale';
    }
    return state.syncState;
  }

  function render(state) {
    const controllerNow = currentControllerNow(state);
    const controllerZoneId = state.controllerMeta && state.controllerMeta.controllerTimeZoneId
      ? state.controllerMeta.controllerTimeZoneId
      : state.labels.unknown;
    const timeText = controllerNow == null ? state.labels.synchronizing : formatControllerTime(controllerNow, controllerZoneId);

    state.globalValue.textContent = timeText;
    updateControlMetadata(state, controllerNow, controllerZoneId);

    if (state.widget) {
      state.widget.setAttribute('data-sync-state', effectiveSyncState(state));
    }
  }

  function sync(state) {
    if (!state.controllerSyncUrl) {
      state.syncState = 'unavailable';
      render(state);
      return Promise.resolve(false);
    }

    const t0 = performance.now();
    return fetch(state.controllerSyncUrl, {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
      },
    }).then(function (response) {
      const t1 = performance.now();
      if (!response.ok) {
        if (state.lastSyncEpochMs == null) {
          state.syncState = 'unavailable';
        }
        render(state);
        return false;
      }

      return response.json().then(function (payload) {
        const epochMs = Number(payload.epochMillis);
        if (!Number.isFinite(epochMs)) {
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
        render(state);
        return true;
      });
    }).catch(function () {
      if (state.lastSyncEpochMs == null) {
        state.syncState = 'unavailable';
      }
      render(state);
      return false;
    });
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

    state.syncTimer = setTimeout(function () {
      if (state.destroyed || document.hidden) {
        return;
      }
      sync(state).then(function () {
        scheduleTimers(state);
      });
    }, RESYNC_INTERVAL_MS);
  }

  function destroy(state) {
    state.destroyed = true;
    clearTimers(state);
    if (state.visibleHandler) {
      document.removeEventListener('visibilitychange', state.visibleHandler);
      state.visibleHandler = null;
    }
    if (window.__controllerClockState === state) {
      window.__controllerClockState = null;
    }
  }

  function start(state) {
    if (!state || !resolveWidgetParts(state)) {
      return;
    }

    state.visibleHandler = function () {
      if (document.hidden) {
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

    if (!document.hidden) {
      sync(state).then(function () {
        scheduleTimers(state);
      });
    } else {
      render(state);
    }
  }

  function init() {
    try {
      if (window.__controllerClockState) {
        return;
      }
      const state = createState();
      if (!state) {
        return;
      }

      window.__controllerClockState = state;
      start(state);
    } catch (error) {
      if (window.__controllerClockState) {
        window.__controllerClockState = null;
      }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
}());
