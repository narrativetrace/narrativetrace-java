/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency

import ai.narrativetrace.api.event.TraceNode
import ai.narrativetrace.api.export.RequestContext
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import ai.narrativetrace.core.render.IndentedTextRenderer
import ai.narrativetrace.micronaut.http.concurrency.shop.Checkout
import ai.narrativetrace.micronaut.http.concurrency.shop.Workers
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/** The calls every request makes: the worker-side refs are `<requestId>:<sku>`. */
val SKUS = listOf("sku-a", "sku-b", "sku-c")

/** One span of an exported trace, flattened in pre-order. */
data class Flat(
    val index: Int,
    val depth: Int,
    val method: String,
    val firstArgument: String,
    val thread: String,
    val traceId: String,
)

/** One request's exported trace. */
class Exported(
    val tree: TraceTree,
) {
    val nodes: List<Flat> = flatten(tree)
    val text: String = IndentedTextRenderer().render(tree)
    val traceIds: Set<String> = nodes.map { it.traceId }.filter { it.isNotEmpty() }.toSet()

    fun only(method: String): Flat {
        val found = named(method)
        check(found.size == 1) { "expected exactly one $method span, found ${found.size} in:\n$text" }
        return found[0]
    }

    fun named(method: String): List<Flat> = nodes.filter { it.method == method }

    fun below(parent: Flat): List<Flat> = nodes.drop(parent.index + 1).takeWhile { it.depth > parent.depth }

    private fun flatten(tree: TraceTree): List<Flat> {
        val flat = mutableListOf<Flat>()

        fun walk(
            node: TraceNode,
            depth: Int,
        ) {
            val thread = node.thread()?.threadName() ?: node.concurrency()?.threadName() ?: ""
            flat.add(
                Flat(
                    flat.size,
                    depth,
                    node.signature().methodName(),
                    node
                        .signature()
                        .parameters()
                        .firstOrNull()
                        ?.renderedValue() ?: "",
                    thread,
                    node.spanContext()?.traceId()?.toString() ?: "",
                ),
            )
            node.children().forEach { walk(it, depth + 1) }
        }
        tree.roots().forEach { walk(it, 0) }
        return flat
    }
}

/** Keeps every export, keyed by the request id the `place` span carries. */
class RecordingExporter : TraceExporter {
    private val byRequest = ConcurrentHashMap<String, Exported>()

    override fun export(
        tree: TraceTree,
        requestContext: RequestContext,
    ) {
        val exported = Exported(tree)
        val place = exported.named("place").firstOrNull() ?: return
        byRequest[place.firstArgument.trim('"')] = exported
    }

    fun of(requestId: String): Exported = byRequest[requestId] ?: error("request $requestId exported no trace; exported: ${byRequest.keys}")

    fun clear() = byRequest.clear()
}

/** What every cell asserts about one request's exported trace. */
object Verdict {
    private val requestIds = Regex("REQ-\\d{4}")

    fun ownWorkNestedUnderTheSubmittingSpan(
        exported: Exported,
        requestId: String,
        workMethod: String,
    ) {
        val place = exported.only("place")
        check(place.firstArgument.contains(requestId)) { "the submitting span is not $requestId's:\n${exported.text}" }
        val nested = exported.below(place).filter { it.method == workMethod }
        check(
            nested.size == SKUS.size,
        ) { "expected ${SKUS.size} $workMethod calls nested under place, found ${nested.size}:\n${exported.text}" }
        check(exported.named(workMethod).size == nested.size) { "a worker call sits outside the submitting span:\n${exported.text}" }
        SKUS.forEach { sku ->
            check(
                nested.any { it.firstArgument.contains("$requestId:$sku") },
            ) { "no worker call carries $requestId:$sku:\n${exported.text}" }
        }
        nothingFromAnotherRequest(exported, requestId)
    }

    /** The launcher node sits under the submitting span; the background work is the group's `childRoots()`. */
    fun launcherInTheTraceAndOwnWorkInTheGroup(
        exported: Exported,
        requestId: String,
        group: ai.narrativetrace.core.context.FireAndForgetGroup,
    ) {
        val place = exported.only("place")
        check(exported.below(place).count { it.method == "fire-and-forget" } == 1) {
            "the launcher node is not under the submitting span:\n${exported.text}"
        }
        nothingFromAnotherRequest(exported, requestId)
        val roots = group.childRoots()
        check(roots.size == SKUS.size) { "expected ${SKUS.size} background roots, found ${roots.size}" }
        roots.forEach { root ->
            check(root.concurrency().kind() == ai.narrativetrace.api.event.ConcurrencyKind.FIRE_AND_FORGET) { "not a fire-and-forget root" }
            check(
                root
                    .signature()
                    .parameters()[0]
                    .renderedValue()
                    .contains("$requestId:"),
            ) { "a background root is not $requestId's" }
            check(root.concurrency().threadName().startsWith(Workers.PREFIX)) { "background work did not run on a worker" }
            check(root.spanContext().traceId().toString() == exported.traceIds.single()) { "background work is on another trace" }
        }
    }

    fun nothingFromAnotherRequest(
        exported: Exported,
        requestId: String,
    ) {
        val seen = requestIds.findAll(exported.text).map { it.value }.toSet()
        check(seen == setOf(requestId)) { "$requestId's trace holds request ids $seen:\n${exported.text}" }
        check(exported.traceIds.size == 1) { "$requestId's work is on ${exported.traceIds.size} traces:\n${exported.text}" }
    }

    fun workRanOnWorkerThreads(
        exported: Exported,
        workMethod: String,
    ) {
        val workers = exported.named(workMethod)
        check(workers.isNotEmpty()) { "no $workMethod call in:\n${exported.text}" }
        workers.forEach {
            check(it.thread.startsWith(Workers.PREFIX)) { "$workMethod ran on '${it.thread}', not a worker:\n${exported.text}" }
        }
    }
}

/** The "controller": serves the request by calling the traced checkout. */
@Controller("/orders")
@Requires(env = ["mt-matrix"])
class OrderController(
    private val checkout: Checkout,
) {
    @Get("/{id}")
    fun order(id: String): String = checkout.place(id, SKUS)
}

@Requires(env = ["mt-matrix"])
@Factory
class MatrixExporterFactory {
    @Bean
    @Singleton
    fun recordingExporter(): TraceExporter = RECORDER

    companion object {
        val RECORDER = RecordingExporter()
    }
}

/** A trivial route so a client can open its connection before the requests are released together. */
@Controller("/ready")
@Requires(env = ["mt-matrix"])
class ReadyController {
    @Get
    fun ready(): String = "ok"
}
