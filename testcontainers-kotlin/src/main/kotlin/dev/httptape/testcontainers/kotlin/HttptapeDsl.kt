package dev.httptape.testcontainers.kotlin

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
): HttptapeContainer {
    val container = HttptapeContainer(image)
    HttptapeContainerScope(container).apply(configure)
    return container
}
