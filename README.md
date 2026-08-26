# Controller Clock

Jenkins plugin that adds a controller clock to the global header. It stays synced to the Jenkins controller, not the browser's local clock.

## Privacy and network behavior

Controller Clock is a local Jenkins feature. It does not send telemetry, analytics, or any other outbound Internet traffic. The browser only calls the Jenkins controller on the same origin to synchronize clock state.

## Build and test

- `mvn clean verify` builds the plugin and runs the test suite.
- `mvn hpi:run -Dport=8090` starts a local Jenkins controller for manual verification.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
