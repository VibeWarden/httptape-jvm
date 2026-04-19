package dev.httptape.testcontainers.kotlin

import dev.httptape.testcontainers.HttptapeContainer
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Extension property for the base URL of a started [HttptapeContainer].
 *
 * Delegates to [HttptapeContainer.getBaseUrl].
 */
val HttptapeContainer.baseUrl: String
    get() = getBaseUrl()

/**
 * Extension function to set delay using [kotlin.time.Duration].
 *
 * Converts the Kotlin duration to [java.time.Duration] and delegates
 * to [HttptapeContainer.withDelay].
 */
fun HttptapeContainer.withDelay(delay: Duration): HttptapeContainer =
    withDelay(delay.toJavaDuration())
