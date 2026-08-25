# Architecture

Controller Clock has three pieces:

1. `ControllerClockRootAction` exposes the read-only `/controller-clock/sync` endpoint and the standalone `/controller-clock/` page.
2. `ControllerClockPageDecorator` injects the clock assets and bootstrap data into normal Jenkins pages.
3. `controller-clock.js` synchronizes against the controller, advances locally with `performance.now()`, and renders the banner.

The controller remains authoritative for the displayed instant. The browser only estimates time progression between resyncs. Timezone details come from the controller JVM, the current user’s Jenkins timezone override, and the browser’s own timezone APIs.
