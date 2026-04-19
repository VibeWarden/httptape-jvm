# httptape-testcontainers-kotest

[Kotest](https://kotest.io/) extension for [httptape](https://github.com/VibeWarden/httptape) Testcontainers lifecycle management.

Uses Kotest's `MountableExtension` pattern with `install()` for ergonomic per-spec container lifecycle.

## Installation

> **Note:** v0.1.0 is not yet published to Maven Central.

### Gradle

```kotlin
testImplementation("dev.httptape:httptape-testcontainers-kotest:0.1.0")
```

This transitively includes `httptape-testcontainers-kotlin` and `httptape-testcontainers`.

## Usage

```kotlin
class MyApiTest : FreeSpec({
    val httptape = install(httptapeExtension {
        fixtures("fixtures/")
        matcherConfig("httptape.config.json")
    })

    "should return expected response" {
        val url = httptape.baseUrl
        // make HTTP requests against the mock server
    }
})
```

## Lifecycle

The container is:
1. Created during `install()` (configuration only)
2. Started before the spec runs (`beforeSpec`)
3. Stopped after all tests complete (`afterSpec`)

One container is shared across all test cases in the spec class.
