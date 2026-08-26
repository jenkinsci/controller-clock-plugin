# Architecture

Controller Clock has three parts:

1. `ControllerClockRootAction` is the `@Extension` `RootAction` that puts the clock in the Jenkins global header and serves the read-only `/controller-clock/sync` endpoint.
2. `ControllerClockRootAction/action.jelly` renders the clock chip in the header and loads `controller-clock.css` and `controller-clock.js` with `st:adjunct`.
3. `controller-clock.js` syncs against the controller, advances locally with `performance.now()`, and redraws the chip once a second.

The controller is the source of truth for the displayed time. Between syncs, the browser estimates progress by storing `payload.epochMillis + roundTripMs / 2` together with a `performance.now()` reading, so changes to the user's clock do not affect the display. The widget exposes its sync state through `data-sync-state` (`waiting`, `current`, `stale`, `unavailable`); anything other than `current` uses the secondary text color.

Timezone details come from the controller JVM and, if present, the current user's Jenkins timezone override.
