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
import ai.narrativetrace.micronaut.http.sample.Greeter
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end smoke test: a real Netty server, a real HTTP round trip, the filter in the chain.
 *
 * INTENT: Every other test in this module either constructs the filter directly against a fake
 * chain or boots only the bean container. Neither would notice a Micronaut server upgrade that
 * changed filter registration, the `HttpServerFilter` contract, or request-path resolution — which
 * is what the 4.7.6 → 4.10.26 bump (CVE-2026-44241, CVE-2026-33012) moves under us. This is the
 * module's sample application: wire it, call it over the wire, assert a trace came out the far end.
 *
 * @llmNote The sample deliberately routes through [Greeter], an interface in an instrumented base
 * package, because the filter skips export for an empty tree — a controller calling nothing traced
 * would make this test pass while proving only that Netty answers.
 */
@MicronautTest(environments = ["embedded-smoke"])
@Property(name = "narrativetrace.base-packages", value = "ai.narrativetrace.micronaut.http.sample")
class EmbeddedServerSmokeTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @BeforeEach
    fun setUp() {
        RECORDER.clear()
    }

    @Test
    fun aRealRequestThroughTheEmbeddedServerIsTracedAndExported() {
        val body = client.toBlocking().retrieve("/smoke/greet")

        assertThat(body).isEqualTo("hello world")
        assertThat(RECORDER.contexts).hasSize(1)
        assertThat(RECORDER.contexts[0].statusCode()).isEqualTo(200)
        assertThat(RECORDER.trees[0].isEmpty).isFalse()
    }

    @Test
    fun aRequestForAnUnmappedRouteGetsItsOwn404RatherThanAFilterFailure() {
        assertThatThrownBy { client.toBlocking().retrieve("/smoke/nothing-here") }
            .isInstanceOf(HttpClientResponseException::class.java)
            .extracting { (it as HttpClientResponseException).status.code }
            .isEqualTo(404)
    }

    @Test
    fun theExportedRouteIsTheTemplateRatherThanTheConcretePath() {
        client.toBlocking().retrieve("/smoke/order/12345")

        assertThat(RECORDER.routes).hasSize(1)
        assertThat(RECORDER.routes[0])
            .`as`("a templated route is one label; a concrete path is one label per order")
            .isEqualTo("/smoke/order/{id}")
    }

    @Controller("/smoke")
    @Requires(env = ["embedded-smoke"])
    class SmokeController(
        private val greeter: Greeter,
    ) {
        @Get("/greet")
        fun greet(): String = greeter.greet("world")

        @Get("/order/{id}")
        fun order(id: String): String = greeter.greet(id)
    }

    @Requires(env = ["embedded-smoke"])
    @Factory
    class RecordingExporterFactory {
        @Bean
        @Singleton
        fun recordingExporter(): TraceExporter = RECORDER
    }

    /** Collects what the filter exported, on whichever thread Netty completed the request. */
    class Recorder : TraceExporter {
        val trees: MutableList<TraceTree> = CopyOnWriteArrayList()
        val contexts: MutableList<RequestContext> = CopyOnWriteArrayList()

        /** The route as MDC saw it, captured before the filter clears it. */
        val routes: MutableList<String> = CopyOnWriteArrayList()

        override fun export(
            tree: TraceTree,
            requestContext: RequestContext,
        ) {
            trees.add(tree)
            contexts.add(requestContext)
            org.slf4j.MDC
                .get("httpRoute")
                ?.let { routes.add(it) }
        }

        fun clear() {
            trees.clear()
            contexts.clear()
            routes.clear()
        }
    }

    companion object {
        val RECORDER = Recorder()
    }
}
