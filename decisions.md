# httptape-jvm Architecture Decision Records

## ADR-001: v0.1 SDK architecture — three-module Testcontainers integration

**Date**: 2026-04-18
**Issues**: #1, #3, #4, #5, #13
**Status**: Accepted

### Context

httptape is a Go binary that records, sanitizes, and replays HTTP traffic. JVM users
currently set up a `GenericContainer` manually, assembling CLI flags, fixture mounts,
and wait strategies by hand. This produces 50+ lines of boilerplate per test suite.

`httptape-jvm` is a sibling repository providing a JVM SDK that collapses that
boilerplate. The SDK ships three modules:

1. **Java core** (`httptape-testcontainers`) -- `HttptapeContainer` extending
   `GenericContainer` with a fluent Java API.
2. **Kotlin DSL** (`httptape-testcontainers-kotlin`) -- idiomatic `httptape { ... }`
   builder with lambda receiver, layered on the Java core.
3. **Kotest extension** (`httptape-testcontainers-kotest`) -- `install(httptapeExtension { ... })`
   lifecycle extension for Kotest, reusing the Kotlin DSL scope.

v0.1 is validated via Gradle composite build against the existing httptape demos
before any Maven Central publishing occurs.

### Decision

#### Verified dependency versions

| Dependency | Version | Source |
|---|---|---|
| Kotlin | 2.3.20 | github.com/JetBrains/kotlin/releases (latest stable) |
| Gradle | 9.4.1 | gradle.org/releases (latest stable) |
| Testcontainers | 2.0.4 | github.com/testcontainers/testcontainers-java/releases |
| Kotest | 6.1.11 | github.com/kotest/kotest/releases |
| JUnit Platform | 5.14.3 | JUnit 5 latest stable (test runner only) |
| JDK toolchain | 21 | Locked decision: broadest audience |

#### Locked constants

| Constant | Value |
|---|---|
| Maven group ID | `dev.httptape` |
| Default httptape image | `ghcr.io/vibewarden/httptape:0.13.1` |
| Container exposed port | `8081` |
| Container fixtures path | `/fixtures` |
| Container config path | `/config/httptape.config.json` |

Note: Issue #1 references `group=io.httptape` and JVM toolchain 25. Those are
superseded by the locked decisions in the user direction: `dev.httptape` and JDK 21.

#### Repository layout

```
httptape-jvm/
  settings.gradle.kts
  build.gradle.kts               # root: shared conventions
  gradle.properties               # group, version, JDK toolchain
  gradle/
    wrapper/
      gradle-wrapper.jar
      gradle-wrapper.properties   # pinned to Gradle 9.4.1
    libs.versions.toml            # version catalog
  gradlew
  gradlew.bat
  .gitignore
  .github/
    workflows/
      build.yml                   # CI: build + test on PR + push to main
  README.md
  LICENSE                         # already exists (Apache 2.0)
  decisions.md                    # this file
  testcontainers/
    build.gradle.kts
    src/
      main/java/dev/httptape/testcontainers/
        HttptapeContainer.java
        SseTimingMode.java
      test/java/dev/httptape/testcontainers/
        HttptapeContainerTest.java
  testcontainers-kotlin/
    build.gradle.kts
    src/
      main/kotlin/dev/httptape/testcontainers/kotlin/
        HttptapeDsl.kt
        HttptapeContainerScope.kt
        Extensions.kt
      test/kotlin/dev/httptape/testcontainers/kotlin/
        HttptapeDslTest.kt
  testcontainers-kotest/
    build.gradle.kts
    src/
      main/kotlin/dev/httptape/testcontainers/kotest/
        HttptapeExtension.kt
      test/kotlin/dev/httptape/testcontainers/kotest/
        HttptapeExtensionTest.kt
```

#### Version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "2.3.20"
testcontainers = "2.0.4"
kotest = "6.1.11"
junit-platform = "5.14.3"

[libraries]
testcontainers-core = { module = "org.testcontainers:testcontainers", version.ref = "testcontainers" }
kotest-runner = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-framework-api = { module = "io.kotest:kotest-framework-api", version.ref = "kotest" }
junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit-platform" }
junit-jupiter-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit-platform" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

#### Root `gradle.properties`

```properties
group=dev.httptape
version=0.1.0-SNAPSHOT
org.gradle.jvmargs=-Xmx512m
```

#### Root `settings.gradle.kts`

```kotlin
rootProject.name = "httptape-jvm"

include("testcontainers")
include("testcontainers-kotlin")
include("testcontainers-kotest")
```

#### Root `build.gradle.kts`

Shared conventions applied to all subprojects:

- Java toolchain: 21
- Kotlin JVM plugin applied only to Kotlin modules (via `plugins { ... }` in each module)
- All modules publish Maven artifact coordinates as `dev.httptape:httptape-<module-name>`
- No `maven-publish` plugin: entirely omitted for v0.1 to avoid confusion

```kotlin
subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Map module directory name -> Maven artifact name
    // testcontainers -> httptape-testcontainers
    // testcontainers-kotlin -> httptape-testcontainers-kotlin
    // testcontainers-kotest -> httptape-testcontainers-kotest
    afterEvaluate {
        extensions.findByType<PublishingExtension>()?.publications?.withType<MavenPublication> {
            artifactId = "httptape-${project.name}"
        }
    }
}
```

Note: the `afterEvaluate` / PublishingExtension block only takes effect if
`maven-publish` is applied later (v0.2). It is harmless if omitted entirely.
The dev should decide whether to include it as forward-compatible scaffolding
or omit it. Either is acceptable.

#### Module: `testcontainers` (Java core)

**build.gradle.kts:**

```kotlin
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(libs.testcontainers.core)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
```

**Types:**

```java
package dev.httptape.testcontainers;

// --- SseTimingMode.java ---

/**
 * Controls SSE (Server-Sent Events) replay timing in the httptape container.
 *
 * <p>Three modes:
 * <ul>
 *   <li>{@link #REALTIME} -- replay at original recorded timing</li>
 *   <li>{@link #INSTANT} -- replay all events immediately</li>
 *   <li>{@link #accelerated(double)} -- replay at a multiplied speed</li>
 * </ul>
 */
public sealed interface SseTimingMode permits
        SseTimingMode.Realtime,
        SseTimingMode.Instant,
        SseTimingMode.Accelerated {

    /** Replay at original recorded timing. */
    SseTimingMode REALTIME = new Realtime();

    /** Replay all events immediately with no delay. */
    SseTimingMode INSTANT = new Instant();

    /** Replay at the given acceleration factor (e.g., 2.0 = twice as fast). */
    static SseTimingMode accelerated(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Acceleration factor must be positive, got: " + factor);
        }
        return new Accelerated(factor);
    }

    /** Returns the CLI flag value for --sse-timing. */
    String toCliFlag();

    record Realtime() implements SseTimingMode {
        @Override public String toCliFlag() { return "realtime"; }
    }

    record Instant() implements SseTimingMode {
        @Override public String toCliFlag() { return "instant"; }
    }

    record Accelerated(double factor) implements SseTimingMode {
        @Override public String toCliFlag() { return "accelerated=" + factor; }
    }
}
```

```java
package dev.httptape.testcontainers;

// --- HttptapeContainer.java ---

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Testcontainers wrapper for the httptape HTTP mock server.
 *
 * <p>Provides a fluent Java API to configure an httptape container
 * for recording/replay in integration tests. Replaces ~50 lines of
 * manual GenericContainer setup with ~5 lines.
 *
 * <p>Usage:
 * <pre>{@code
 * var container = new HttptapeContainer()
 *     .withFixturesFromClasspath("fixtures/")
 *     .withSseTiming(SseTimingMode.REALTIME);
 * container.start();
 * String baseUrl = container.getBaseUrl();
 * }</pre>
 */
public class HttptapeContainer extends GenericContainer<HttptapeContainer> {

    /** Default httptape Docker image, pinned to the SDK release's tested version. */
    public static final String DEFAULT_IMAGE = "ghcr.io/vibewarden/httptape:0.13.1";

    /** The port httptape listens on inside the container. */
    private static final int HTTPTAPE_PORT = 8081;

    // Configuration state (applied in configure() before container start)
    private final List<String> commandParts = new ArrayList<>();
    private final Map<String, String> replayHeaders = new LinkedHashMap<>();
    private SseTimingMode sseTiming;
    private boolean corsEnabled;
    private Duration delay;
    private double errorRate;
    private int fallbackStatus = -1;  // -1 = use server default (404)

    // Classpath fixture staging
    private final List<ClasspathFixture> classpathFixtures = new ArrayList<>();
    private final List<ClasspathResource> classpathConfigs = new ArrayList<>();

    /**
     * Creates an HttptapeContainer with the default image.
     */
    public HttptapeContainer() {
        this(DEFAULT_IMAGE);
    }

    /**
     * Creates an HttptapeContainer with a custom image.
     *
     * @param dockerImageName full Docker image reference (e.g., "ghcr.io/vibewarden/httptape:0.14.0")
     */
    public HttptapeContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(HTTPTAPE_PORT);
        waitingFor(Wait.forHttp("/").forStatusCode(404));
    }

    // --- Fluent configuration methods ---

    /**
     * Mounts a host directory as the fixture source.
     *
     * @param hostPath path to the fixtures directory on the host
     * @return this container for chaining
     */
    public HttptapeContainer withFixturesPath(Path hostPath);

    /**
     * Discovers and mounts fixture files from the classpath.
     *
     * <p>Scans the given classpath root for {@code *.json} files,
     * flattens subdirectories, and copies each file into the container's
     * {@code /fixtures} directory. Fails fast on filename collisions
     * across subdirectories.
     *
     * <p>This replaces the manual
     * {@code MountableFile.forClasspathResource(...)} + {@code withCopyFileToContainer(...)}
     * pattern and Spring's {@code PathMatchingResourcePatternResolver}.
     *
     * @param classpathRoot classpath prefix (e.g., "fixtures/")
     * @return this container for chaining
     */
    public HttptapeContainer withFixturesFromClasspath(String classpathRoot);

    /**
     * Mounts a matcher config file from a host path.
     *
     * @param configFile path to the httptape config JSON on the host
     * @return this container for chaining
     */
    public HttptapeContainer withMatcherConfig(Path configFile);

    /**
     * Mounts a matcher config file from the classpath.
     *
     * @param resourcePath classpath path to the config JSON (e.g., "httptape.config.json")
     * @return this container for chaining
     */
    public HttptapeContainer withMatcherConfigFromClasspath(String resourcePath);

    /**
     * Sets the SSE replay timing mode.
     *
     * @param mode one of {@link SseTimingMode#REALTIME}, {@link SseTimingMode#INSTANT},
     *             or {@link SseTimingMode#accelerated(double)}
     * @return this container for chaining
     */
    public HttptapeContainer withSseTiming(SseTimingMode mode);

    /**
     * Enables CORS headers (Access-Control-Allow-Origin: *) on all responses.
     *
     * @param enabled true to enable CORS
     * @return this container for chaining
     */
    public HttptapeContainer withCors(boolean enabled);

    /**
     * Adds a custom header to all replayed responses.
     *
     * @param key   header name
     * @param value header value
     * @return this container for chaining
     */
    public HttptapeContainer withReplayHeader(String key, String value);

    /**
     * Adds a fixed delay before every response.
     *
     * @param delay the delay duration
     * @return this container for chaining
     */
    public HttptapeContainer withDelay(Duration delay);

    /**
     * Sets the fraction of requests that return HTTP 500.
     *
     * @param rate value between 0.0 (no errors) and 1.0 (all errors)
     * @return this container for chaining
     * @throws IllegalArgumentException if rate is outside [0.0, 1.0]
     */
    public HttptapeContainer withErrorRate(double rate);

    /**
     * Sets the HTTP status code returned when no fixture matches.
     *
     * @param status HTTP status code (default: 404)
     * @return this container for chaining
     */
    public HttptapeContainer withFallbackStatus(int status);

    // --- Accessor methods ---

    /**
     * Returns the base URL for the httptape server.
     *
     * <p>Only valid after the container has started.
     *
     * @return URL in the form {@code http://<host>:<mappedPort>}
     */
    public String getBaseUrl();

    /**
     * Returns the mapped port for the httptape server.
     *
     * @return the host port mapped to the container's 8081
     */
    public int getPort();
}
```

**Key implementation details for `withFixturesFromClasspath`:**

The method must replicate what the Java demo does with
`PathMatchingResourcePatternResolver` but using only stdlib:

1. Use `Thread.currentThread().getContextClassLoader().getResources(classpathRoot)`.
2. Walk the returned URLs (either `file:` for filesystem or `jar:` for JARs).
3. For `file:` URLs: use `java.nio.file.Files.walk()` to find `*.json` files.
4. Flatten into a map of `filename -> Path`, failing on collisions.
5. Stage each file via `withCopyFileToContainer(MountableFile.forHostPath(path), "/fixtures/" + filename)`.

For `jar:` URLs: use `JarFile` + `JarEntry` enumeration. Both code paths
converge on the same copy-to-container call.

The collision detection follows the same pattern as the existing Java demo's
`collide()` method -- fail fast with a descriptive error message.

**`configure()` override:**

Override `configure()` (called by Testcontainers before container start) to
assemble the `withCommand(...)` from accumulated configuration state:

```java
@Override
protected void configure() {
    List<String> cmd = new ArrayList<>();
    cmd.add("serve");
    cmd.add("--fixtures");
    cmd.add("/fixtures");

    if (sseTiming != null) {
        cmd.add("--sse-timing=" + sseTiming.toCliFlag());
    }
    if (corsEnabled) {
        cmd.add("--cors");
    }
    if (delay != null && !delay.isZero()) {
        cmd.add("--delay");
        cmd.add(delay.toMillis() + "ms");
    }
    if (errorRate > 0) {
        cmd.add("--error-rate");
        cmd.add(String.valueOf(errorRate));
    }
    if (fallbackStatus >= 0) {
        cmd.add("--fallback-status");
        cmd.add(String.valueOf(fallbackStatus));
    }
    for (var entry : replayHeaders.entrySet()) {
        cmd.add("--replay-header");
        cmd.add(entry.getKey() + "=" + entry.getValue());
    }
    // Matcher config mount handled by withMatcherConfig*() methods
    // which call withCopyFileToContainer + add --config flag

    withCommand(cmd.toArray(new String[0]));
    super.configure();
}
```

Note: the config file path argument (`--config /config/httptape.config.json`)
is added to the command parts when `withMatcherConfig*()` is called. The
configure() method merges it.

**Sequence (container lifecycle):**

1. User creates `HttptapeContainer` (default or custom image).
2. User calls fluent methods: `withFixturesFromClasspath(...)`, `withSseTiming(...)`, etc.
3. Each fluent method stores state in instance fields. Methods that involve
   file mounting (`withFixturesFromClasspath`, `withMatcherConfigFromClasspath`)
   also immediately call `withCopyFileToContainer(...)`.
4. User calls `container.start()`.
5. Testcontainers calls `configure()` -- we assemble the `--serve` command from state.
6. Testcontainers starts the Docker container.
7. Wait strategy (`Wait.forHttp("/").forStatusCode(404)`) blocks until ready.
8. User calls `getBaseUrl()` which returns `http://<host>:<mappedPort>`.

**Error cases:**

| Error | Handling |
|---|---|
| Classpath root not found | `IllegalArgumentException` with descriptive message at config time |
| Filename collision in classpath fixtures | `IllegalStateException` listing both source paths |
| Container fails to start | Testcontainers' own `ContainerLaunchException` propagates |
| `getBaseUrl()` called before start | `IllegalStateException` from Testcontainers' `getMappedPort()` |
| Invalid error rate (outside 0.0-1.0) | `IllegalArgumentException` at config time |
| Invalid acceleration factor (<= 0) | `IllegalArgumentException` at config time |

**Test strategy for `testcontainers` module:**

- **Unit tests** (no Docker required):
  - `SseTimingMode.toCliFlag()` for all three variants
  - Command assembly in `configure()` -- verify generated command line args
    for various combinations of options. Use reflection or a protected method
    to extract the command without starting Docker.
  - Classpath fixture discovery: place test fixtures in `src/test/resources/testfixtures/`
    and verify the scanner finds them, flattens correctly, detects collisions.
  - Error rate validation (out of range throws).
  - Acceleration factor validation (non-positive throws).

- **Integration tests** (require Docker -- tag with `@Testcontainers`):
  - Start an `HttptapeContainer` with fixtures from classpath, verify `getBaseUrl()`
    returns a valid URL and a request to it returns expected fixture data.
  - Start with matcher config, verify matched responses are correct.
  - Verify `getPort()` returns the mapped port.

#### Module: `testcontainers-kotlin` (Kotlin DSL)

**build.gradle.kts:**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":testcontainers"))

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}
```

**Types:**

```kotlin
package dev.httptape.testcontainers.kotlin

// --- HttptapeContainerScope.kt ---

import dev.httptape.testcontainers.HttptapeContainer
import dev.httptape.testcontainers.SseTimingMode
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * DSL scope for configuring an [HttptapeContainer].
 *
 * Used as the receiver in the [httptape] builder function.
 * Properties with var setters represent simple flags; functions
 * represent collection-like or complex configuration.
 */
@HttptapeDslMarker
class HttptapeContainerScope internal constructor(
    private val container: HttptapeContainer
) {
    /**
     * Loads fixture files from the given classpath root.
     * Subdirectories are flattened into the container's /fixtures directory.
     */
    fun fixtures(classpathRoot: String)

    /** Mounts a host directory as the fixture source. */
    fun fixturesPath(path: Path)

    /** Loads a matcher config file from the classpath. */
    fun matcherConfig(classpathPath: String)

    /** Loads a matcher config file from a host path. */
    fun matcherConfig(path: Path)

    /** SSE replay timing mode. Default: null (server default). */
    var sseTiming: SseTimingMode?

    /** Whether to enable CORS headers. Default: false. */
    var cors: Boolean

    /** Fixed delay before every response. Default: null (no delay). */
    var delay: Duration?

    /** Fraction of requests that return HTTP 500. Default: 0.0. */
    var errorRate: Double

    /** HTTP status when no fixture matches. Default: server default (404). */
    var fallbackStatus: Int

    /** Adds a custom header to all replayed responses. */
    fun replayHeader(key: String, value: String)
}
```

```kotlin
package dev.httptape.testcontainers.kotlin

// --- HttptapeDsl.kt ---

import dev.httptape.testcontainers.HttptapeContainer

/**
 * DSL marker annotation to prevent accidental scope leakage.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class HttptapeDslMarker

/**
 * Creates and configures an [HttptapeContainer] using a Kotlin DSL.
 *
 * Example:
 * ```kotlin
 * val container = httptape {
 *     fixtures("fixtures/")
 *     matcherConfig("httptape.config.json")
 *     sseTiming = SseTimingMode.REALTIME
 * }
 * container.start()
 * ```
 *
 * @param image Docker image to use. Defaults to [HttptapeContainer.DEFAULT_IMAGE].
 * @param configure DSL configuration block.
 * @return a configured but not yet started [HttptapeContainer].
 */
fun httptape(
    image: String = HttptapeContainer.DEFAULT_IMAGE,
    configure: HttptapeContainerScope.() -> Unit = {}
): HttptapeContainer
```

```kotlin
package dev.httptape.testcontainers.kotlin

// --- Extensions.kt ---

import dev.httptape.testcontainers.HttptapeContainer
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Extension property for the base URL of a started [HttptapeContainer].
 */
val HttptapeContainer.baseUrl: String
    get() = getBaseUrl()

/**
 * Extension function to set delay using kotlin.time.Duration.
 */
fun HttptapeContainer.withDelay(delay: Duration): HttptapeContainer =
    withDelay(delay.toJavaDuration())
```

**Implementation details for `HttptapeContainerScope`:**

Each property setter / function delegates to the corresponding Java fluent method:

```kotlin
var sseTiming: SseTimingMode? = null
    set(value) {
        field = value
        value?.let { container.withSseTiming(it) }
    }

fun fixtures(classpathRoot: String) {
    container.withFixturesFromClasspath(classpathRoot)
}

var delay: Duration? = null
    set(value) {
        field = value
        value?.let { container.withDelay(it.toJavaDuration()) }
    }
```

**Implementation of `httptape()` top-level function:**

```kotlin
fun httptape(
    image: String = HttptapeContainer.DEFAULT_IMAGE,
    configure: HttptapeContainerScope.() -> Unit
): HttptapeContainer {
    val container = HttptapeContainer(image)
    HttptapeContainerScope(container).apply(configure)
    return container
}
```

**Test strategy for `testcontainers-kotlin` module:**

- **Unit tests:**
  - DSL builds correct container configuration (verify via the container's
    internal state or command output). Use Kotest assertions.
  - Default image is used when no image specified.
  - Custom image override works.
  - Extension properties/functions work on a configured container.

- **Integration tests** (require Docker):
  - Full DSL round-trip: `httptape { fixtures(...) }` -> `start()` -> HTTP request -> verify response.

#### Module: `testcontainers-kotest` (Kotest extension)

**build.gradle.kts:**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":testcontainers-kotlin"))
    api(libs.kotest.framework.api)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}
```

**Types:**

```kotlin
package dev.httptape.testcontainers.kotest

// --- HttptapeExtension.kt ---

import dev.httptape.testcontainers.HttptapeContainer
import dev.httptape.testcontainers.kotlin.HttptapeContainerScope
import dev.httptape.testcontainers.kotlin.httptape
import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec

/**
 * Handle returned by [install] for accessing the httptape container
 * from within tests.
 */
class HttptapeHandle internal constructor(
    internal val container: HttptapeContainer
) {
    /** Base URL of the running httptape server. Only valid after spec setup. */
    val baseUrl: String
        get() = container.getBaseUrl()
}

/**
 * Kotest extension that manages an [HttptapeContainer] lifecycle per-spec.
 *
 * Usage:
 * ```kotlin
 * class MyTest : FreeSpec({
 *     val httptape = install(httptapeExtension {
 *         fixtures("fixtures/")
 *         matcherConfig("httptape.config.json")
 *     })
 *
 *     "my test" {
 *         val url = httptape.baseUrl
 *         // ...
 *     }
 * })
 * ```
 */
class HttptapeExtension(
    private val image: String = HttptapeContainer.DEFAULT_IMAGE,
    private val configure: HttptapeContainerScope.() -> Unit
) : MountableExtension<Unit, HttptapeHandle>,
    BeforeSpecListener,
    AfterSpecListener {

    private lateinit var container: HttptapeContainer

    override fun mount(configure: Unit.() -> Unit): HttptapeHandle {
        container = httptape(image, this.configure)
        return HttptapeHandle(container)
    }

    override suspend fun beforeSpec(spec: Spec) {
        container.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
    }
}

/**
 * Creates an [HttptapeExtension] for use with Kotest's [install] mechanism.
 *
 * @param image Docker image override. Defaults to [HttptapeContainer.DEFAULT_IMAGE].
 * @param configure DSL configuration block (same scope as [httptape] builder).
 */
fun httptapeExtension(
    image: String = HttptapeContainer.DEFAULT_IMAGE,
    configure: HttptapeContainerScope.() -> Unit
): HttptapeExtension = HttptapeExtension(image, configure)
```

**Lifecycle sequence:**

1. Test class declares `val httptape = install(httptapeExtension { ... })`.
2. Kotest calls `HttptapeExtension.mount()` which creates the container and
   returns an `HttptapeHandle`.
3. Before the spec runs, Kotest calls `beforeSpec()` which starts the container.
4. Tests access `httptape.baseUrl` to get the running server URL.
5. After all tests in the spec complete, Kotest calls `afterSpec()` which stops
   the container.

**Container lifecycle is per-spec** (one container shared across all test cases
in the spec class). Per-test isolation is deferred to a future version.

**Coroutine consideration:** `container.start()` and `container.stop()` are
blocking JVM calls. Kotest's `beforeSpec`/`afterSpec` are `suspend` functions
but Testcontainers is not coroutine-aware. The blocking calls run on Kotest's
dispatcher which handles this correctly. No explicit `withContext(Dispatchers.IO)`
is needed because Kotest already dispatches listeners on a thread pool. If this
proves to be a problem in practice, wrapping in `withContext(Dispatchers.IO)` is
a safe future addition.

**Test strategy for `testcontainers-kotest` module:**

- **Integration tests** (require Docker, use Kotest runner):
  - A `FreeSpec` that uses `install(httptapeExtension { ... })` with fixtures,
    verifies `baseUrl` is accessible, and makes an HTTP request against the container.
  - Verify lifecycle: container is started before tests and stopped after.
  - Verify that the handle's `baseUrl` property works correctly.

#### CI workflow (`.github/workflows/build.yml`)

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew build

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: "**/build/reports/tests/"
```

Notes:
- Ubuntu-only for v0.1 (Docker is the primary test dependency; macOS Docker
  in CI is slow and expensive).
- `gradle/actions/setup-gradle@v4` handles Gradle caching automatically.
- JDK 21 matches the toolchain target.
- No multi-JDK matrix: JDK 21 only for v0.1.

#### Composite build setup for demo validation (#13)

**Kotlin demo** (`examples/kotlin-ktor-koog/settings.gradle.kts`):

```kotlin
rootProject.name = "kotlin-ktor-koog-demo"

// Composite build: resolve httptape SDK from sibling repo if available.
// Falls back to Maven Central (once published) if the sibling isn't checked out.
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

**Kotlin demo** `build.gradle.kts` -- add SDK dependencies:

```kotlin
// Replace manual Testcontainers setup with httptape SDK
testImplementation("dev.httptape:httptape-testcontainers-kotest:0.1.0-SNAPSHOT")
```

This transitively pulls `httptape-testcontainers-kotlin` and `httptape-testcontainers`.
The existing `org.testcontainers:testcontainers:2.0.4` dependency stays (it is a
transitive dep of the SDK, but keeping it explicit is fine).

**Java demo** (Maven -- `pom.xml`):

Since Maven does not support Gradle composite builds, the Java demo consumes the
SDK from the local Maven repository:

1. Dev runs `./gradlew publishToMavenLocal` in `httptape-jvm/`.
2. Java demo's `pom.xml` adds the dependency:

```xml
<dependency>
    <groupId>dev.httptape</groupId>
    <artifactId>httptape-testcontainers</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

This requires adding `maven-publish` to the SDK but only for `publishToMavenLocal`
(no remote publishing). Revised decision: include the `maven-publish` plugin in
the root build with only `publishToMavenLocal` task available, no remote repository
configured. This is the minimum needed for the Java demo validation.

Add to each module's `build.gradle.kts`:

```kotlin
plugins {
    // ... existing plugins
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = rootProject.group.toString()
            artifactId = "httptape-${project.name}"
        }
    }
    // No repositories configured = publishToMavenLocal only
}
```

**Refactored files in `VibeWarden/httptape`:**

1. **`examples/kotlin-ktor-koog/settings.gradle.kts`** -- add conditional composite build
   (shown above).

2. **`examples/kotlin-ktor-koog/build.gradle.kts`** -- add SDK dependency, keep existing
   testcontainers dep.

3. **`examples/kotlin-ktor-koog/src/test/kotlin/dev/httptape/demo/HttptapeContainer.kt`**
   -- rewrite from 53 lines to ~10 using Kotlin DSL:

   ```kotlin
   package dev.httptape.demo

   import dev.httptape.testcontainers.SseTimingMode
   import dev.httptape.testcontainers.kotlin.httptape

   object HttptapeContainer {
       val instance by lazy {
           httptape {
               fixtures("fixtures/")
               matcherConfig("httptape.config.json")
           }.also { it.start() }
       }

       val baseUrl: String
           get() = instance.getBaseUrl()
   }
   ```

4. **`examples/kotlin-ktor-koog/src/test/kotlin/dev/httptape/demo/WeatherAdviceTest.kt`**
   -- rewrite to use Kotest extension:

   ```kotlin
   package dev.httptape.demo

   import dev.httptape.testcontainers.kotest.httptapeExtension
   import io.kotest.core.spec.style.FreeSpec
   import io.kotest.matchers.string.shouldContain
   import io.ktor.client.plugins.sse.*
   import io.ktor.server.testing.*

   class WeatherAdviceTest : FreeSpec({
       val httptape = install(httptapeExtension {
           fixtures("fixtures/")
           matcherConfig("httptape.config.json")
       })

       "advises bringing an umbrella when it is rainy in the requested city" {
           testApplication {
               application {
                   configureApp(
                       openAiBaseUrl = httptape.baseUrl,
                       openAiApiKey = "sk-test-key",
                       weatherBaseUrl = httptape.baseUrl
                   )
               }

               val client = createClient { install(SSE) }
               val events = mutableListOf<String>()
               client.sse("/weather-advice?city=Berlin") {
                   incoming.collect { event -> event.data?.let { events.add(it) } }
               }

               events.joinToString("") shouldContain "umbrella"
           }
       }
   })
   ```

5. **`examples/java-spring-boot/pom.xml`** -- add SDK dependency.

6. **`examples/java-spring-boot/src/test/java/dev/httptape/demo/TestcontainersConfig.java`**
   -- rewrite using SDK:

   ```java
   @TestConfiguration(proxyBeanMethods = false)
   class TestcontainersConfig {

       @Bean
       HttptapeContainer httptapeContainer() {
           return new HttptapeContainer()
               .withFixturesFromClasspath("fixtures/")
               .withSseTiming(SseTimingMode.REALTIME);
       }

       @Bean
       DynamicPropertyRegistrar httptapeProperties(HttptapeContainer httptapeContainer) {
           return registry -> {
               String baseUrl = httptapeContainer.getBaseUrl();
               registry.add("spring.ai.openai.base-url", () -> baseUrl);
               registry.add("spring.ai.openai.api-key", () -> "sk-test-key");
               registry.add("app.external-api.base-url", () -> baseUrl);
           };
       }
   }
   ```

   Down from ~80 lines (including the `collide`, `uri`, `toPath` helpers) to ~15.
   The `PathMatchingResourcePatternResolver` is eliminated entirely -- the SDK's
   `withFixturesFromClasspath` handles classpath scanning with stdlib.

#### File plan summary

**httptape-jvm (new files -- 20 files):**

| File | Purpose |
|---|---|
| `settings.gradle.kts` | Project structure, module includes |
| `build.gradle.kts` | Shared conventions |
| `gradle.properties` | group, version |
| `gradle/libs.versions.toml` | Version catalog |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.4.1 |
| `gradlew` | Unix wrapper script |
| `gradlew.bat` | Windows wrapper script |
| `.gitignore` | Build artifacts, IDE, JVM |
| `.github/workflows/build.yml` | CI workflow |
| `README.md` | Project overview (replace existing placeholder) |
| `decisions.md` | This ADR |
| `testcontainers/build.gradle.kts` | Java core module build |
| `testcontainers/src/main/java/dev/httptape/testcontainers/HttptapeContainer.java` | Main container class |
| `testcontainers/src/main/java/dev/httptape/testcontainers/SseTimingMode.java` | SSE timing value type |
| `testcontainers/src/test/java/dev/httptape/testcontainers/HttptapeContainerTest.java` | Java core tests |
| `testcontainers-kotlin/build.gradle.kts` | Kotlin DSL module build |
| `testcontainers-kotlin/src/main/kotlin/dev/httptape/testcontainers/kotlin/HttptapeDsl.kt` | Top-level builder + DSL marker |
| `testcontainers-kotlin/src/main/kotlin/dev/httptape/testcontainers/kotlin/HttptapeContainerScope.kt` | DSL scope class |
| `testcontainers-kotlin/src/main/kotlin/dev/httptape/testcontainers/kotlin/Extensions.kt` | Kotlin extension functions |
| `testcontainers-kotlin/src/test/kotlin/dev/httptape/testcontainers/kotlin/HttptapeDslTest.kt` | Kotlin DSL tests |
| `testcontainers-kotest/build.gradle.kts` | Kotest extension module build |
| `testcontainers-kotest/src/main/kotlin/dev/httptape/testcontainers/kotest/HttptapeExtension.kt` | Kotest extension + handle + factory |
| `testcontainers-kotest/src/test/kotlin/dev/httptape/testcontainers/kotest/HttptapeExtensionTest.kt` | Kotest extension tests |

**httptape (modified files -- 5 files):**

| File | Change |
|---|---|
| `examples/kotlin-ktor-koog/settings.gradle.kts` | Add conditional composite build |
| `examples/kotlin-ktor-koog/build.gradle.kts` | Add SDK dependency |
| `examples/kotlin-ktor-koog/src/test/kotlin/dev/httptape/demo/HttptapeContainer.kt` | Rewrite using Kotlin DSL |
| `examples/kotlin-ktor-koog/src/test/kotlin/dev/httptape/demo/WeatherAdviceTest.kt` | Rewrite using Kotest extension |
| `examples/java-spring-boot/pom.xml` | Add SDK dependency |
| `examples/java-spring-boot/src/test/java/dev/httptape/demo/TestcontainersConfig.java` | Rewrite using SDK |

#### PR strategy

**Two PRs:**

1. **PR in `VibeWarden/httptape-jvm`**: single PR covering issues #1, #3, #4, #5.
   All four issues are tightly coupled (each module depends on the previous one)
   and this is greenfield with zero existing users. A single PR provides atomic
   review of the full API surface. Branch: `feat/1-v0.1-sdk`.

2. **PR in `VibeWarden/httptape`**: separate PR covering issue #13 (demo refactor).
   This PR validates the SDK. It depends on the SDK PR being merged (or at least
   the branch existing for composite build). Branch: `feat/13-sdk-demo-validation`.

Justification for two PRs rather than one:
- Different repos. Cannot be one PR.
- The SDK PR can be reviewed and merged independently.
- The demo refactor PR serves as the validation gate -- if it passes, the SDK works.

Justification for one SDK PR (not three):
- Greenfield repo, zero users, zero existing code.
- The three modules have a strict dependency chain (kotest -> kotlin -> java).
- Splitting into three PRs would triple the review overhead with no benefit.
- All four issues (#1 bootstrap + #3 + #4 + #5) form one logical unit.

### Rationale

**JDK 21 toolchain**: LTS release with the broadest audience. JDK 25 is available
but would exclude users on JDK 21-24. Sealed interfaces and records (used by
`SseTimingMode`) are available since JDK 17. The toolchain directive means the
SDK compiles and runs on JDK 21+ regardless of what JDK the developer has installed.

**Sealed interface for SseTimingMode** (not enum): An enum cannot have per-instance
constructor parameters. `SseTimingMode.accelerated(2.5)` requires a factory method
returning a record with a `factor` field. A sealed interface with three record
implementations provides this naturally. Java 21 supports sealed interfaces and
records. Kotlin interop is clean: `SseTimingMode.REALTIME`, `SseTimingMode.INSTANT`,
`SseTimingMode.accelerated(2.5)` all work from Kotlin code.

**Composite build for validation**: Gradle composite builds allow the demos to
consume the SDK from source without publishing. This is the standard Gradle
approach for multi-repo development. The `if (sdkDir.exists())` guard means
the demos continue to build even when the sibling repo isn't checked out (once
the SDK is published to Maven Central, which is v0.2 work).

**No Maven Central publish in v0.1**: Per user direction. The SDK must prove
itself against real demos first. `publishToMavenLocal` is included only to
support the Maven-based Java demo's validation.

**Single package per module** (not sub-packages): Each module has one package
(`dev.httptape.testcontainers`, `dev.httptape.testcontainers.kotlin`,
`dev.httptape.testcontainers.kotest`). No internal sub-packages. This mirrors
the flat-package philosophy of the Go httptape project.

**Kotlin DSL re-exports Java `SseTimingMode`**: Rather than creating a parallel
Kotlin sealed class, the DSL uses the Java sealed interface directly. This avoids
a mapping layer and ensures type compatibility. The Java sealed interface is already
idiomatic enough for Kotlin use (`SseTimingMode.REALTIME`, `SseTimingMode.accelerated(2.5)`).

**`MountableExtension` for Kotest** (not raw `TestListener`): Kotest's
`MountableExtension` is the modern extension mechanism that works with `install()`.
It returns a handle object that tests use to access the container state. This is
more ergonomic than a raw `TestListener` where the container reference would need
to be a mutable property.

### Alternatives considered

1. **Kotlin Multiplatform (KMP)**: Rejected. Testcontainers is JVM-only. KMP would
   add complexity without benefit since the entire dependency tree is JVM.

2. **Per-language repos** (`httptape-java`, `httptape-kotlin`, `httptape-kotest`):
   Rejected. Three repos means three Sonatype OSSRH namespaces, three CI configs,
   three release cycles. One multi-module repo with one group ID is simpler.

3. **Auto-publish on tag**: Deferred to v0.2. Requires Maven Central credentials,
   GPG signing, Sonatype staging. Not needed until the SDK has been validated.

4. **Enum for SseTimingMode**: Rejected. Cannot represent `accelerated(factor)`
   with per-instance state. Would require a separate static factory and a wrapper
   class, which is strictly worse than a sealed interface.

5. **Spring's PathMatchingResourcePatternResolver for classpath scanning**: Rejected.
   Would pull Spring as a transitive dependency. stdlib `ClassLoader.getResources()`
   + `Files.walk()` accomplishes the same thing.

### Consequences

- The SDK is validated against real demos before publishing. If the demos can't
  cleanly adopt the API, the SDK is revised before it reaches Maven Central.
- `publishToMavenLocal` is the only publishing mechanism in v0.1. No remote
  repository config means no accidental publishes.
- The demo refactor serves as living documentation of the SDK's API surface.
- Future v0.2 work: Maven Central publishing (#2), JUnit 5 extension (#8/#9),
  Spring Boot starter (#10), release automation (#6), distribution PRs (#7).
- The Java demo stays on Maven. Migrating it to Gradle is not worth the effort
  since Maven + `publishToMavenLocal` works for validation, and once the SDK is
  on Maven Central, the demo just uses published artifacts.
