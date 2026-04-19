# httptape-testcontainers-kotlin

Kotlin DSL for configuring [httptape](https://github.com/VibeWarden/httptape) Testcontainers.

Provides an idiomatic `httptape { ... }` builder on top of the Java core.

## Installation

> **Note:** v0.1.0 is not yet published to Maven Central.

### Gradle

```kotlin
testImplementation("dev.httptape:httptape-testcontainers-kotlin:0.1.0")
```

This transitively includes `httptape-testcontainers`.

## Usage

```kotlin
val container = httptape {
    fixtures("fixtures/")
    matcherConfig("httptape.config.json")
    sseTiming = SseTimingMode.REALTIME
    cors = true
    delay = 100.milliseconds
}
container.start()

val url = container.baseUrl  // extension property
```

## DSL scope

- `fixtures(classpathRoot)` -- load fixtures from classpath
- `fixturesPath(path)` -- mount host directory
- `matcherConfig(classpathPath)` / `matcherConfig(path)` -- matcher config
- `sseTiming` -- SSE timing mode (var)
- `cors` -- CORS enabled (var)
- `delay` -- response delay as `kotlin.time.Duration` (var)
- `errorRate` -- error fraction (var)
- `fallbackStatus` -- fallback HTTP status (var)
- `replayHeader(key, value)` -- custom response header
