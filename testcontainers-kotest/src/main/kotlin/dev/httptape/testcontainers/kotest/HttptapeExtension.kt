package dev.httptape.testcontainers.kotest

import dev.httptape.testcontainers.HttptapeContainer
import dev.httptape.testcontainers.kotlin.HttptapeContainerScope
import dev.httptape.testcontainers.kotlin.httptape
import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec

/**
 * Handle returned by [install][io.kotest.core.spec.style.scopes.ContainerScope]
 * for accessing the httptape container from within tests.
 *
 * @property container the underlying [HttptapeContainer] instance
 */
class HttptapeHandle internal constructor(
    val container: HttptapeContainer
) {
    /**
     * Base URL of the running httptape server.
     * Only valid after the spec setup (container start).
     */
    val baseUrl: String
        get() = container.getBaseUrl()

    /**
     * Mapped port of the running httptape server.
     * Only valid after the spec setup (container start).
     */
    val port: Int
        get() = container.getPort()
}

/**
 * Kotest extension that manages an [HttptapeContainer] lifecycle per-spec.
 *
 * The container is created during [mount], started in [beforeSpec], and
 * stopped in [afterSpec]. This provides one container shared across all
 * test cases in the spec class.
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
 * Creates an [HttptapeExtension] for use with Kotest's `install` mechanism.
 *
 * @param image Docker image override. Defaults to [HttptapeContainer.DEFAULT_IMAGE].
 * @param configure DSL configuration block (same scope as [httptape] builder).
 * @return an [HttptapeExtension] ready to be installed
 */
fun httptapeExtension(
    image: String = HttptapeContainer.DEFAULT_IMAGE,
    configure: HttptapeContainerScope.() -> Unit
): HttptapeExtension = HttptapeExtension(image, configure)
