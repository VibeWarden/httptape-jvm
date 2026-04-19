package dev.httptape.testcontainers.kotest

import dev.httptape.testcontainers.HttptapeContainer
import dev.httptape.testcontainers.SseTimingMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests for [HttptapeExtension] and [httptapeExtension] factory.
 *
 * These tests verify the extension wiring and configuration without
 * requiring Docker. Integration tests that start the container are
 * separate.
 */
class HttptapeExtensionTest : FreeSpec({

    "httptapeExtension factory" - {

        "creates an extension that can be mounted" {
            val extension = httptapeExtension {
                sseTiming = SseTimingMode.REALTIME
            }
            extension shouldNotBe null
        }

        "mount returns a handle with a configured container" {
            val extension = httptapeExtension {
                sseTiming = SseTimingMode.INSTANT
                cors = true
            }
            val handle = extension.mount {}

            handle shouldNotBe null
            handle.container shouldNotBe null

            val cmd = handle.container.buildCommand()
            cmd shouldContain "--sse-timing=instant"
            cmd shouldContain "--cors"
        }

        "mount uses default image" {
            val extension = httptapeExtension {}
            val handle = extension.mount {}

            handle.container.dockerImageName shouldBe HttptapeContainer.DEFAULT_IMAGE
        }

        "mount accepts custom image" {
            val extension = httptapeExtension(image = HttptapeContainer.DEFAULT_IMAGE) {
                sseTiming = SseTimingMode.REALTIME
            }
            val handle = extension.mount {}

            handle.container.dockerImageName shouldBe HttptapeContainer.DEFAULT_IMAGE
        }
    }

    "HttptapeHandle" - {

        "exposes container reference" {
            val extension = httptapeExtension {}
            val handle = extension.mount {}

            handle.container shouldNotBe null
        }
    }
})
