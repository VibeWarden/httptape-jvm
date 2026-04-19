package dev.httptape.testcontainers.kotlin

import dev.httptape.testcontainers.HttptapeContainer
import dev.httptape.testcontainers.SseTimingMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

/**
 * Unit tests for the Kotlin DSL.
 *
 * These tests verify that the DSL correctly configures the underlying
 * [HttptapeContainer] without requiring Docker.
 */
class HttptapeDslTest : FreeSpec({

    "httptape builder" - {

        "uses default image when no image specified" {
            val container = httptape {}
            container.dockerImageName shouldBe HttptapeContainer.DEFAULT_IMAGE
        }

        "produces a valid container" {
            val container = httptape {}
            val cmd = container.buildCommand()
            cmd[0] shouldBe "serve"
            cmd[1] shouldBe "--fixtures"
            cmd[2] shouldBe "/fixtures"
        }
    }

    "HttptapeContainerScope" - {

        "sseTiming property delegates to container" {
            val container = httptape {
                sseTiming = SseTimingMode.REALTIME
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--sse-timing=realtime"
        }

        "sseTiming with accelerated mode" {
            val container = httptape {
                sseTiming = SseTimingMode.accelerated(2.5)
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--sse-timing=accelerated=2.5"
        }

        "cors property delegates to container" {
            val container = httptape {
                cors = true
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--cors"
        }

        "delay property delegates to container" {
            val container = httptape {
                delay = 500.milliseconds
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--delay"
            cmd shouldContain "500ms"
        }

        "errorRate property delegates to container" {
            val container = httptape {
                errorRate = 0.25
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--error-rate"
            cmd shouldContain "0.25"
        }

        "fallbackStatus property delegates to container" {
            val container = httptape {
                fallbackStatus = 503
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--fallback-status"
            cmd shouldContain "503"
        }

        "replayHeader function delegates to container" {
            val container = httptape {
                replayHeader("X-Mock", "true")
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--replay-header"
            cmd shouldContain "X-Mock=true"
        }

        "multiple configurations compose correctly" {
            val container = httptape {
                sseTiming = SseTimingMode.INSTANT
                cors = true
                delay = 100.milliseconds
                errorRate = 0.1
                fallbackStatus = 502
                replayHeader("X-Test", "yes")
            }
            val cmd = container.buildCommand()
            cmd shouldContain "--sse-timing=instant"
            cmd shouldContain "--cors"
            cmd shouldContain "--delay"
            cmd shouldContain "--error-rate"
            cmd shouldContain "--fallback-status"
            cmd shouldContain "--replay-header"
        }
    }

    "extension functions" - {

        "withDelay accepts kotlin.time.Duration" {
            val container = HttptapeContainer()
                .withDelay(250.milliseconds)
            val cmd = container.buildCommand()
            cmd shouldContain "--delay"
            cmd shouldContain "250ms"
        }
    }
})
