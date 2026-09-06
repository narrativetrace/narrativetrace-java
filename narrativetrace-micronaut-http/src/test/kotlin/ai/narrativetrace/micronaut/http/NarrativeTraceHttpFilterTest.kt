/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.export.RequestContext
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.simple.SimpleHttpResponseFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class NarrativeTraceHttpFilterTest {
    private lateinit var context: ThreadLocalNarrativeContext
    private val exportedTrees = mutableListOf<TraceTree>()
    private val exportedContexts = mutableListOf<RequestContext>()
    private var exporterError: RuntimeException? = null

    private val exporter =
        TraceExporter { tree, requestContext ->
            exporterError?.let { throw it }
            exportedTrees.add(tree)
            exportedContexts.add(requestContext)
        }

    private lateinit var filter: NarrativeTraceHttpFilter

    @BeforeEach
    fun setUp() {
        context = ThreadLocalNarrativeContext()
        filter = NarrativeTraceHttpFilter(context, exporter)
        exportedTrees.clear()
        exportedContexts.clear()
        exporterError = null
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun implementsHttpServerFilter() {
        assertThat(filter).isInstanceOf(HttpServerFilter::class.java)
    }

    @Test
    fun callsChainProceed() {
        var chainCalled = false
        val chain =
            chain {
                chainCalled = true
                okResponse()
            }
        val result = Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(chainCalled).isTrue()
        assertThat(result).isNotNull()
    }

    @Test
    fun resetsContextBeforeRequest() {
        // Pre-pollute context
        context.enterMethod(traceSignature("stale"))
        context.exitMethodWithReturn("done")

        val chain =
            chain {
                // During chain execution, context should be clean (stale trace cleared)
                val tree = context.captureTrace()
                assertThat(tree.isEmpty()).isTrue()
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
    }

    @Test
    fun capturesTraceAndExportsAfterResponse() {
        val chain =
            chain {
                // Simulate traced work
                context.enterMethod(traceSignature("doWork"))
                context.exitMethodWithReturn("\"result\"")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()

        assertThat(exportedTrees).hasSize(1)
        assertThat(exportedTrees[0].isEmpty()).isFalse()
    }

    @Test
    fun resetsContextAfterRequest() {
        val chain =
            chain {
                context.enterMethod(traceSignature("doWork"))
                context.exitMethodWithReturn("\"result\"")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()

        // After filter completes, context should be reset
        val tree = context.captureTrace()
        assertThat(tree.isEmpty()).isTrue()
    }

    @Test
    fun stampsHttpMetadata() {
        val chain =
            chain {
                context.enterMethod(traceSignature("doWork"))
                context.exitMethodWithReturn("\"ok\"")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/api/orders"), chain)).block()

        assertThat(exportedContexts).hasSize(1)
    }

    @Test
    fun skipsExportForEmptyTraces() {
        val chain = chain { okResponse() }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()

        assertThat(exportedTrees).isEmpty()
    }

    @Test
    fun exporterFailureDoesNotPropagate() {
        exporterError = RuntimeException("exporter broken")
        val chain =
            chain {
                context.enterMethod(traceSignature("doWork"))
                context.exitMethodWithReturn("\"ok\"")
                okResponse()
            }
        // Should not throw
        val result = Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(result).isNotNull()
    }

    @Test
    fun cleansUpWhenChainErrors() {
        context.enterMethod(traceSignature("stale"))
        context.exitMethodWithReturn("stale")

        val chain = errorChain(RuntimeException("chain failed"))

        try {
            Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        } catch (_: RuntimeException) {
            // Expected
        }

        // Context should be reset even on error
        val tree = context.captureTrace()
        assertThat(tree.isEmpty()).isTrue()
    }

    @Test
    fun requestContextIncludesStatusCodeAndDuration() {
        val chain =
            chain {
                context.enterMethod(traceSignature("doWork"))
                context.exitMethodWithReturn("\"ok\"")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()

        assertThat(exportedContexts).hasSize(1)
        assertThat(exportedContexts[0].statusCode()).isEqualTo(200)
        assertThat(exportedContexts[0].durationMillis()).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun mdcContainsTraceIdDuringChainExecution() {
        var capturedTraceId: String? = null
        val chain =
            chain {
                capturedTraceId = MDC.get("traceId")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(capturedTraceId).isNotNull().matches("[0-9a-f]{32}")
    }

    @Test
    fun mdcContainsHttpFieldsDuringChainExecution() {
        var capturedMethod: String? = null
        var capturedRoute: String? = null
        val chain =
            chain {
                capturedMethod = MDC.get("httpMethod")
                capturedRoute = MDC.get("httpRoute")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/api/orders"), chain)).block()
        assertThat(capturedMethod).isEqualTo("GET")
        assertThat(capturedRoute).isEqualTo("/api/orders")
    }

    @Test
    fun mdcClearedAfterRequest() {
        val chain = chain { okResponse() }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(MDC.get("traceId")).isNull()
        assertThat(MDC.get("httpMethod")).isNull()
    }

    @Test
    fun mdcContainsIdentityFieldsFromProvider() {
        filter.provider =
            RequestContextProvider { RequestContextProvider.UserContext("user-42", "sess-1", "tenant-a") }
        var capturedUser: String? = null
        var capturedTenant: String? = null
        val chain =
            chain {
                capturedUser = MDC.get("enduserId")
                capturedTenant = MDC.get("tenantId")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(capturedUser).isEqualTo("user-42")
        assertThat(capturedTenant).isEqualTo("tenant-a")
    }

    @Test
    fun throwingRequestContextProviderDoesNotFailTheRequest() {
        filter.provider = RequestContextProvider { throw IllegalStateException("auth backend down") }
        var chainCalled = false
        val chain =
            chain {
                chainCalled = true
                okResponse()
            }

        // Must not throw — observability failure must never become a request failure
        val result = Mono.from(filter.doFilter(getRequest("/test"), chain)).block()

        assertThat(chainCalled).isTrue()
        assertThat(result?.status).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun providerNullDoesNotSetIdentityMdc() {
        var capturedUser: String? = "sentinel"
        val chain =
            chain {
                capturedUser = MDC.get("enduserId")
                okResponse()
            }
        Mono.from(filter.doFilter(getRequest("/test"), chain)).block()
        assertThat(capturedUser).isNull()
    }

    // --- Helpers ---

    private fun traceSignature(methodName: String) =
        ai.narrativetrace.api.event.MethodSignature(
            "TestService",
            methodName,
            emptyList(),
            null,
            null,
        )

    private fun getRequest(path: String): HttpRequest<*> {
        val request = HttpRequest.GET<Any>(path)
        return request
    }

    private fun okResponse(): MutableHttpResponse<*> = SimpleHttpResponseFactory.INSTANCE.status<Any>(HttpStatus.OK)

    private fun chain(block: () -> MutableHttpResponse<*>): ServerFilterChain = ServerFilterChain { Flux.just(block()) }

    private fun errorChain(error: Throwable): ServerFilterChain = ServerFilterChain { Flux.error(error) }
}
