# httptape-testcontainers

Java core module providing `HttptapeContainer` -- a Testcontainers wrapper for the [httptape](https://github.com/VibeWarden/httptape) HTTP mock server.

## Installation

> **Note:** v0.1.0 is not yet published to Maven Central.

### Gradle

```kotlin
testImplementation("dev.httptape:httptape-testcontainers:0.1.0")
```

### Maven

```xml
<dependency>
    <groupId>dev.httptape</groupId>
    <artifactId>httptape-testcontainers</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Usage

```java
var container = new HttptapeContainer()
    .withFixturesFromClasspath("fixtures/")
    .withSseTiming(SseTimingMode.REALTIME)
    .withCors(true);
container.start();

String baseUrl = container.getBaseUrl();
// make HTTP requests against baseUrl
```

## API

- `withFixturesFromClasspath(String)` -- auto-discover JSON fixtures from classpath
- `withFixturesPath(Path)` -- mount a host directory as fixtures
- `withMatcherConfig(Path)` / `withMatcherConfigFromClasspath(String)` -- matcher config
- `withSseTiming(SseTimingMode)` -- SSE replay timing
- `withCors(boolean)` -- CORS headers
- `withDelay(Duration)` -- fixed response delay
- `withErrorRate(double)` -- fraction of HTTP 500 responses
- `withFallbackStatus(int)` -- status when no fixture matches
- `withReplayHeader(String, String)` -- custom response headers
- `getBaseUrl()` -- server URL (after start)
- `getPort()` -- mapped port (after start)
