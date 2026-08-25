# Contributing

## Build

- `mvn clean verify` runs the full plugin build and test suite.
- `mvn hpi:run -Dport=8090` starts a local Jenkins controller with this plugin installed.

## Code style

- Use Java 17 language features only.
- Keep source under `src/main/java/io/jenkins/plugins/controllerclock`.
- Keep Jelly, CSS, and JavaScript small and scoped to this plugin.

## Tests

- Add JenkinsRule tests for behavior that depends on Jenkins security or rendering.
- Prefer deterministic date-based assertions in unit tests rather than sleeps.
