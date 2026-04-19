package dev.httptape.testcontainers.kotlin

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
    fun fixtures(classpathRoot: String) {
        container.withFixturesFromClasspath(classpathRoot)
    }

    /**
     * Mounts a host directory as the fixture source.
     */
    fun fixturesPath(path: Path) {
        container.withFixturesPath(path)
    }

    /**
     * Loads a matcher config file from the classpath.
     */
    fun matcherConfig(classpathPath: String) {
        container.withMatcherConfigFromClasspath(classpathPath)
    }

    /**
     * Loads a matcher config file from a host path.
     */
    fun matcherConfig(path: Path) {
        container.withMatcherConfig(path)
    }

    /**
     * SSE replay timing mode. Default: null (server default).
     * Uses the Java [SseTimingMode] sealed interface directly.
     */
    var sseTiming: SseTimingMode? = null
        set(value) {
            field = value
            value?.let { container.withSseTiming(it) }
        }

    /**
     * Whether to enable CORS headers. Default: false.
     */
    var cors: Boolean = false
        set(value) {
            field = value
            container.withCors(value)
        }

    /**
     * Fixed delay before every response. Default: null (no delay).
     * Uses [kotlin.time.Duration].
     */
    var delay: Duration? = null
        set(value) {
            field = value
            value?.let { container.withDelay(it.toJavaDuration()) }
        }

    /**
     * Fraction of requests that return HTTP 500. Default: 0.0.
     */
    var errorRate: Double = 0.0
        set(value) {
            field = value
            container.withErrorRate(value)
        }

    /**
     * HTTP status when no fixture matches. Default: server default (404).
     * Use -1 to reset to server default.
     */
    var fallbackStatus: Int = -1
        set(value) {
            field = value
            container.withFallbackStatus(value)
        }

    /**
     * Adds a custom header to all replayed responses.
     */
    fun replayHeader(key: String, value: String) {
        container.withReplayHeader(key, value)
    }
}
