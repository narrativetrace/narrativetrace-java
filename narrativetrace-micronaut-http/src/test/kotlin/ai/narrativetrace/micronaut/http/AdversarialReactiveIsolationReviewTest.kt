/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.event.MethodSignature
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import ai.narrativetrace.core.config.NarrativeTraceConfig
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import ai.narrativetrace.core.pipeline.BufferedEventConsumer
import ai.narrativetrace.core.pipeline.DualPathPipeline
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.simple.SimpleHttpResponseFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

/** Desired-contract reproducer for the 2026-10-22 review; failure is intentional. */
class AdversarialReactiveIsolationReviewTest {
    @Test
    fun overlappingRequestsOnOneEventLoopMustKeepTheirOwnTraces() {
        val pipeline = DualPathPipeline(null, BufferedEventConsumer(32, false))
        val context = ThreadLocalNarrativeContext(NarrativeTraceConfig(), pipeline)
        val exported = mutableListOf<TraceTree>()
        val filter = NarrativeTraceHttpFilter(context, TraceExporter { tree, _ -> exported.add(tree) })
        val firstResponse = Sinks.one<MutableHttpResponse<*>>()
        val secondResponse = Sinks.one<MutableHttpResponse<*>>()
        try {
            // One event-loop thread starts B while A is waiting for its response.
            val first =
                Mono
                    .from(
                        filter.doFilter(
                            HttpRequest.GET<Any>("/first"),
                            ServerFilterChain {
                                context.enterMethod(MethodSignature("Service", "first", emptyList()))
                                context.exitMethodWithReturn("\"first-result\"")
                                firstResponse.asMono()
                            },
                        ),
                    ).subscribe()
            val second =
                Mono
                    .from(
                        filter.doFilter(
                            HttpRequest.GET<Any>("/second"),
                            ServerFilterChain {
                                context.enterMethod(MethodSignature("Service", "second", emptyList()))
                                context.exitMethodWithReturn("\"second-result\"")
                                secondResponse.asMono()
                            },
                        ),
                    ).subscribe()
            try {
                assertThat(firstResponse.tryEmitValue(okResponse())).isEqualTo(Sinks.EmitResult.OK)
                assertThat(secondResponse.tryEmitValue(okResponse())).isEqualTo(Sinks.EmitResult.OK)
                assertThat(exported.map { tree -> tree.roots().map { it.signature().methodName() } })
                    .describedAs("A must export A, and completing A must not erase B")
                    .isEqualTo(listOf(listOf("first"), listOf("second")))
            } finally {
                first.dispose()
                second.dispose()
            }
        } finally {
            context.reset()
            pipeline.close()
            MDC.clear()
        }
    }

    private fun okResponse(): MutableHttpResponse<*> = SimpleHttpResponseFactory.INSTANCE.status<Any>(HttpStatus.OK)
}
