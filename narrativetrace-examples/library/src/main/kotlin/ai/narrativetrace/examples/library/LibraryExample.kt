/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.library

import ai.narrativetrace.core.config.NarrativeTraceConfig
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import ai.narrativetrace.core.pipeline.DualPathPipeline
import ai.narrativetrace.core.render.IndentedTextRenderer
import ai.narrativetrace.core.render.ProseRenderer
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer
import ai.narrativetrace.examples.AsciiSequenceDiagram
import ai.narrativetrace.examples.DemoTraces
import ai.narrativetrace.proxy.NarrativeTraceProxy
import ai.narrativetrace.slf4j.Slf4jTraceEventListener
import org.slf4j.LoggerFactory

object LibraryExample {
    private val logger = LoggerFactory.getLogger(LibraryExample::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val pipeline = DualPathPipeline(Slf4jTraceEventListener())
        val context = ThreadLocalNarrativeContext(NarrativeTraceConfig(), pipeline)
        val renderer = IndentedTextRenderer()
        val proseRenderer = ProseRenderer()
        val mermaidRenderer = MermaidSequenceDiagramRenderer()

        val catalog =
            NarrativeTraceProxy.trace(
                InMemoryCatalogService(),
                CatalogService::class.java,
                context,
            )
        val members =
            NarrativeTraceProxy.trace(
                InMemoryMemberService(),
                MemberService::class.java,
                context,
            )
        val lending =
            NarrativeTraceProxy.trace(
                DefaultLendingService(catalog, members),
                LendingService::class.java,
                context,
            )

        // --- Scenario 1: Successful borrow ---
        logger.info("=== Scenario 1: Successful Book Borrow ===\n")
        val receipt = lending.borrowBook("M-001", "978-0-13-468599-1")
        logger.info("Received: {}", receipt)
        val trace1 = context.captureTrace()
        DemoTraces.capture("Scenario 1: Successful Book Borrow", trace1)
        logger.info("\n--- Trace tree ---\n")
        logger.info("\n{}", renderer.render(trace1))
        logger.info("\n--- Prose ---\n")
        logger.info("\n{}", proseRenderer.render(trace1))
        logger.info("\n--- Mermaid ---\n")
        logger.info("\n{}", mermaidRenderer.render(trace1))
        logger.info("\n--- Sequence diagram (ASCII) ---\n")
        logger.info(
            "\n{}",
            AsciiSequenceDiagram.render(PlantUmlSequenceDiagramRenderer().render(trace1)),
        )

        // --- Scenario 2: Book unavailable ---
        context.reset()
        logger.info("\n=== Scenario 2: Book Unavailable ===\n")
        try {
            lending.borrowBook("M-001", "978-0-13-235088-4")
        } catch (e: BookUnavailableException) {
            // expected
        }
        val trace2 = context.captureTrace()
        DemoTraces.capture("Scenario 2: Book Unavailable", trace2)
        logger.info("\n--- Trace tree ---\n")
        logger.info("\n{}", renderer.render(trace2))
        logger.info("\n--- Prose ---\n")
        logger.info("\n{}", proseRenderer.render(trace2))
    }
}
