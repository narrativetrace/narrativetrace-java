/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor
import ai.narrativetrace.micronaut.http.concurrency.shop.LaunchedGroups
import io.micrometer.context.ContextRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Hooks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Request scope: Micronaut's reactive HTTP filter, over a real Netty server and real HTTP round
 * trips. Route: the proxy (the bean listener traces the shop beans). The "controller" calls the
 * traced checkout, which hands its work over; the export is what the filter's scope captured.
 *
 * The event loop count is raised well above the eight overlapping requests, because the handler
 * blocks its loop thread while its workers run (ForkGroup joins on the request thread) and the
 * eight connections must each get their own loop to be in flight together.
 */
abstract class MicronautMatrixBase {
    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var context: ai.narrativetrace.core.context.NarrativeContext

    @BeforeEach
    fun registerPropagation() {
        MatrixExporterFactory.RECORDER.clear()
        ContextRegistry.getInstance().registerThreadLocalAccessor(NarrativeTraceThreadLocalAccessor(context as ThreadLocalNarrativeContext))
        Hooks.enableAutomaticContextPropagation()
    }

    @AfterEach
    fun unregisterPropagation() {
        Hooks.disableAutomaticContextPropagation()
        ContextRegistry.getInstance().removeThreadLocalAccessor(NarrativeTraceThreadLocalAccessor.KEY)
    }

    /** One request over HTTP, on a client of its own (its own connection, hence its own event loop). */
    protected fun get(requestId: String): String = HttpClient.create(server.url).use { it.toBlocking().retrieve("/orders/$requestId") }

    /** Eight requests in flight at once: each client connects, all eight fire together. */
    protected fun getConcurrently(ids: List<String>) {
        val start = CyclicBarrier(ids.size)
        val failure = AtomicReference<Throwable>()
        val done = CountDownLatch(ids.size)
        ids.forEach { id ->
            Thread {
                try {
                    HttpClient.create(server.url).use { client ->
                        client.toBlocking().retrieve("/ready") // connect first, so the connections are distinct
                        start.await(30, TimeUnit.SECONDS)
                        client.toBlocking().retrieve("/orders/$id")
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    done.countDown()
                }
            }.start()
        }
        check(done.await(60, TimeUnit.SECONDS)) { "requests did not finish" }
        failure.get()?.let { throw AssertionError("a request failed", it) }
    }

    protected fun singleRequestIsOwn(
        n: Int,
        workMethod: String,
    ) {
        val id = "REQ-%04d".format(n)
        get(id)
        val exported = MatrixExporterFactory.RECORDER.of(id)
        Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, workMethod)
        Verdict.workRanOnWorkerThreads(exported, workMethod)
    }

    protected fun overlappingRequestsAreOwn(
        first: Int,
        workMethod: String,
    ) {
        val ids = ids(first, 8)
        getConcurrently(ids)
        ids.forEach {
            val exported = MatrixExporterFactory.RECORDER.of(it)
            Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, it, workMethod)
            Verdict.workRanOnWorkerThreads(exported, workMethod)
        }
    }

    companion object {
        fun ids(
            from: Int,
            count: Int,
        ) = (0 until count).map { "REQ-%04d".format(from + it) }
    }
}

@MicronautTest(environments = ["mt-matrix"])
@Property(name = "narrativetrace.base-packages", value = "ai.narrativetrace.micronaut.http.concurrency.shop")
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "32")
@Property(name = "mt.handover", value = "fork")
class MicronautForkGroupMatrixTest : MicronautMatrixBase() {
    @Test
    fun micronautForkGroup_workersNestUnderTheSubmittingSpanWithTheirValues() = singleRequestIsOwn(1, "reserve")

    @Test
    fun micronautForkGroup_eightOverlappingRequestsExportOnlyTheirOwnWork() = overlappingRequestsAreOwn(100, "reserve")
}

@MicronautTest(environments = ["mt-matrix"])
@Property(name = "narrativetrace.base-packages", value = "ai.narrativetrace.micronaut.http.concurrency.shop")
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "32")
@Property(name = "mt.handover", value = "fire-and-forget")
class MicronautFireAndForgetMatrixTest : MicronautMatrixBase() {
    @Inject
    lateinit var groups: LaunchedGroups

    @Test
    fun micronautFireAndForget_launcherInTheTraceAndTheWorkInTheGroup() {
        get("REQ-0002")
        Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
            MatrixExporterFactory.RECORDER.of("REQ-0002"),
            "REQ-0002",
            groups.byRequest.getValue("REQ-0002"),
        )
    }

    @Test
    fun micronautFireAndForget_eightOverlappingRequestsKeepTheirOwnLauncherAndGroup() {
        val ids = ids(200, 8)
        getConcurrently(ids)
        ids.forEach {
            Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
                MatrixExporterFactory.RECORDER.of(it),
                it,
                groups.byRequest.getValue(it),
            )
        }
    }
}

@MicronautTest(environments = ["mt-matrix"])
@Property(name = "narrativetrace.base-packages", value = "ai.narrativetrace.micronaut.http.concurrency.shop")
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "32")
@Property(name = "mt.handover", value = "reactor")
class MicronautReactorMatrixTest : MicronautMatrixBase() {
    @Test
    fun micronautMicrometerReactor_workersNestUnderTheSubmittingSpanWithTheirValues() = singleRequestIsOwn(3, "reserve")

    @Test
    fun micronautMicrometerReactor_eightOverlappingRequestsExportOnlyTheirOwnWork() = overlappingRequestsAreOwn(300, "reserve")
}
