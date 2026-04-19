# httptape-jvm

Java, Kotlin, and Kotest extensions for [httptape](https://github.com/VibeWarden/httptape) -- the HTTP traffic recording, sanitization, and replay tool.

This SDK provides Testcontainers integrations that collapse ~50 lines of manual container setup into ~5 lines.

## Modules

| Module | Description | Language |
|---|---|---|
| `httptape-testcontainers` | Core `HttptapeContainer` with fluent Java API | Java 21+ |
| `httptape-testcontainers-kotlin` | Kotlin DSL (`httptape { ... }` builder) on top of the Java core | Kotlin |
| `httptape-testcontainers-kotest` | Kotest `MountableExtension` for lifecycle management | Kotlin / Kotest 6 |

## Installation

> **Note:** v0.1.0 is not yet published to Maven Central. The snippets below are placeholders for when it is.

### Gradle (Kotlin DSL)

```kotlin
// Java projects
testImplementation("dev.httptape:httptape-testcontainers:0.1.0")

// Kotlin projects
testImplementation("dev.httptape:httptape-testcontainers-kotlin:0.1.0")

// Kotest projects
testImplementation("dev.httptape:httptape-testcontainers-kotest:0.1.0")
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

## Quick start

### Java

```java
var container = new HttptapeContainer()
    .withFixturesFromClasspath("fixtures/")
    .withSseTiming(SseTimingMode.REALTIME);
container.start();

String baseUrl = container.getBaseUrl();
```

### Kotlin DSL

```kotlin
val container = httptape {
    fixtures("fixtures/")
    matcherConfig("httptape.config.json")
    sseTiming = SseTimingMode.REALTIME
}
container.start()
```

### Kotest extension

```kotlin
class MyTest : FreeSpec({
    val httptape = install(httptapeExtension {
        fixtures("fixtures/")
        matcherConfig("httptape.config.json")
    })

    "my test" {
        val url = httptape.baseUrl
        // make HTTP requests against the mock server
    }
})
```

## Development

### Prerequisites

- JDK 21+
- Docker (for integration tests)

### Building

```bash
./gradlew build
```

### Composite build (for SDK development against demos)

If you have this repo checked out as a sibling to the main `httptape` repo, you can use Gradle composite builds to test the SDK against the demos without publishing:

```bash
# In the demo project's settings.gradle.kts:
val sdkDir = file("../../../httptape-jvm")
if (sdkDir.exists()) {
    includeBuild(sdkDir) {
        dependencySubstitution {
            substitute(module("dev.httptape:httptape-testcontainers"))
                .using(project(":testcontainers"))
            substitute(module("dev.httptape:httptape-testcontainers-kotlin"))
                .using(project(":testcontainers-kotlin"))
            substitute(module("dev.httptape:httptape-testcontainers-kotest"))
                .using(project(":testcontainers-kotest"))
        }
    }
}
```

For Maven-based projects, publish to local Maven first:

```bash
./gradlew publishToMavenLocal
```

## License

Apache 2.0 -- see [LICENSE](LICENSE).
