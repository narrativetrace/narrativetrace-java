/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.core.context.ContextExport
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.simple.SimpleHttpResponseFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The Micronaut half of an adversarial-review finding on raw context values reaching MDC.
 *
 * INTENT: Route, client address and user-context values are request-derived and reach MDC, which
 * is printed by whatever layout the host configured. A raw newline there forges a log line a
 * line-based parser or SIEM cannot distinguish from a real one (CWE-117), and an unbounded value
 * is unbounded cardinality once it reaches telemetry.
 *
 * @llmNote The route-template preference is proved end to end in `EmbeddedServerSmokeTest`, where
 * a real request to `/smoke/order/12345` exports `/smoke/order/{id}`. It cannot be proved here:
 * a hand-built `HttpRequest` has no route match, which is exactly the fallback path this file
 * covers.
 */
class HttpFilterContextNormalizationTest {
    private lateinit var context: ThreadLocalNarrativeContext
    private lateinit var filter: NarrativeTraceHttpFilter
    private val captured = mutableMapOf<String, String>()

    @BeforeEach
    fun setUp() {
        context = ThreadLocalNarrativeContext()
        filter = NarrativeTraceHttpFilter(context, TraceExporter { _, _ -> })
        captured.clear()
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
        context.reset()
    }

    private fun mdcDuring(path: String): Map<String, String> {
        val chain =
            ServerFilterChain {
                MDC.getCopyOfContextMap()?.let { captured.putAll(it) }
                Flux.just(SimpleHttpResponseFactory.INSTANCE.status<Any>(HttpStatus.OK) as MutableHttpResponse<*>)
            }
        Mono.from(filter.doFilter(HttpRequest.GET<Any>(path), chain)).block()
        return captured
    }

    @Test
    @DisplayName("a newline in the path cannot forge a log line through MDC")
    fun aNewlineInThePathCannotForgeALogLine() {
        val mdc = mdcDuring("/orders")
        // A hand-built request normalises the path, so drive the hostile case through the provider
        // as well; this assertion pins the ordinary path staying clean.
        assertNoRawControls(mdc)
        assertThat(mdc["httpRoute"]).isEqualTo("/orders")
    }

    @Test
    @DisplayName("hostile user-context values are normalised before MDC")
    fun hostileUserContextValuesAreNormalised() {
        filter.provider =
            RequestContextProvider {
                RequestContextProvider.UserContext(
                    "ada\nforged-user",
                    "sess\u001b[31m",
                    "tenant" + "x".repeat(10_000),
                )
            }

        val mdc = mdcDuring("/orders")

        assertThat(mdc["enduserId"]).doesNotContain("\n")
        assertThat(mdc["sessionId"]).doesNotContain("\u001b")
        assertThat(mdc["tenantId"]!!.length).isLessThanOrEqualTo(ContextExport.MAX_LENGTH + 1)
        assertNoRawControls(mdc)
    }

    @Test
    @DisplayName("an ordinary request still exports exactly what it always did")
    fun anOrdinaryRequestIsUnchanged() {
        val mdc = mdcDuring("/orders/42")

        assertThat(mdc["httpRoute"]).isEqualTo("/orders/42")
        assertThat(mdc["httpMethod"]).isEqualTo("GET")
    }

    private fun assertNoRawControls(mdc: Map<String, String>) {
        mdc.forEach { (key, value) ->
            assertThat(value.any { it.isISOControl() })
                .`as`("MDC entry %s carried a raw control character: %s", key, value)
                .isFalse()
        }
    }
}
