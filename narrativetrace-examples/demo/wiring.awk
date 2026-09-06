# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Per-scenario wiring notes for the demo launcher: how THAT scenario's trace is
# configured. Loaded by demo.sh ahead of colorize.awk, which prints the matching entry
# under each scenario header.
#
# Keys are the scenario titles exactly as the examples print them, between "=== " and
# " ===", plus two sections that have no such header: clarity's report banner, and the
# "--- Trace tree ---" marker, which carries the renderer model shown once per run. They live
# here rather than in the example sources so the examples stay reference-grade code with
# no demo scaffolding in them. The cost of that split is drift — a renamed or added
# scenario silently losing its note — which the `demoWiringCheck` build task (backed by
# buildSrc DemoWiringSupport) fails the build on, in both directions.
#
# Keep entries short: the note explains the mechanism, the trace does the rest. State
# only what the code actually does — every claim here is checkable in the sources.

BEGIN {
  # ---- how renderings are chosen — printed once, at the first rendering section ----

  wiring["--- Trace tree ---"] = \
    "Renderers are not configured: there is no default, no registry, no setting. Capture\n" \
    "produces a TraceTree and you call the renderer you want — here that is one line,\n" \
    "new IndentedTextRenderer().render(trace); ProseRenderer, MarkdownRenderer and the\n" \
    "diagram renderers below are the same deal. NarrativeRenderer is a single method\n" \
    "(String render(TraceTree)), so your own renderer is a lambda.\n" \
    "The live → ← !! lines are not a renderer at all: that is Slf4jTraceEventListener in\n" \
    "the DualPathPipeline, formatting each event as it happens — the only view you get\n" \
    "without writing any rendering code, and what your log tool ingests.\n" \
    "Configuration picks a renderer in exactly one place, trace files written from tests:\n" \
    "-Dnarrativetrace.output=true with -Dnarrativetrace.format=markdown|text|mermaid|\n" \
    "plantuml (markdown is the default; the Gradle plugin's narrativeTrace { format = … }\n" \
    "sets the same property)."

  # ---- ecommerce (Spring AOP, annotations, async propagation) ----

  wiring["Scenario 1: Successful Order + Async Notification"] = \
    "Wiring: @EnableNarrativeTrace on ECommerceConfig — Spring wraps every service bean\n" \
    "in a tracing proxy at startup, so DefaultOrderService holds no tracing code at all.\n" \
    "The // line in the tree is @Narrated on OrderService.placeOrder; cardToken prints as\n" \
    "[REDACTED] because that parameter carries @NotTraced. The async notification joins\n" \
    "this same trace through ContextPropagatingTaskDecorator on the taskExecutor bean."

  wiring["Scenario 2: Payment Failure — Inventory Leak Bug"] = \
    "Wiring: unchanged from scenario 1 — nothing was added to catch or log this failure.\n" \
    "The proxy records the thrown PaymentDeclinedException and unwinds the tree itself;\n" \
    "the bracketed text after !! comes from @OnError on PaymentService.charge."

  wiring["Scenario 3: Flaky External Service"] = \
    "Wiring: no Spring in this one — NarrativeTraceProxy.trace(impl,\n" \
    "NotificationService.class, context) wraps a plain object at runtime through a JDK\n" \
    "dynamic proxy. Same NarrativeContext as the beans above, so both calls land in the\n" \
    "same trace: annotations and a container are conveniences, not requirements."

  wiring["Scenario 4: Unknown Customer"] = \
    "Wiring: unchanged — the same Spring proxies produced these lines, and only the\n" \
    "rendering differs. @OnError on CustomerService.findCustomer supplies the bracketed\n" \
    "message; the MDC traceId in the prefix is set by the example, as Micrometer would."

  wiring["Scenario 5: Out of Stock"] = \
    "Wiring: same Spring proxies; @OnError on InventoryService.reserve supplies the\n" \
    "bracketed message. The diagram below is that same captured tree handed to\n" \
    "PlantUmlSequenceDiagramRenderer and drawn by AsciiSequenceDiagram — not a re-run."

  wiring["Scenario 6: Explicit Async Trace Capture"] = \
    "Wiring: ContextPropagatingTaskDecorator on the taskExecutor bean (ECommerceConfig)\n" \
    "carries MDC and the narrative context onto the worker thread; the Micrometer\n" \
    "NarrativeTraceThreadLocalAccessor registered in narrativeContext() makes that\n" \
    "propagation automatic for anything the executor runs."

  # ---- clarity (plain proxies; the variable under test is naming, not configuration) ----

  wiring["Scenario 1: Guest Books a Room (Excellent Naming)"] = \
    "Wiring: no Spring, no annotations — NarrativeTraceProxy.trace(impl, Iface.class,\n" \
    "context) around each service, with DualPathPipeline(new Slf4jTraceEventListener())\n" \
    "turning events into the live lines. All four scenarios are wired identically; only\n" \
    "the naming quality changes."

  wiring["Scenario 2: Booking via Manager (Adequate Naming)"] = \
    "Wiring: the same proxy setup after context.reset() — one traced interface this time.\n" \
    "Nothing in the configuration changed between scenarios; the names did."

  wiring["Scenario 3: Legacy Data Processing (Poor Naming)"] = \
    "Wiring: the same proxy setup again. A tracer can only report what the code calls\n" \
    "itself, so generic names in, generic trace out — no configuration rescues this one."

  wiring["Scenario 4: Guest Repository (Cohesion Mismatch)"] = \
    "Wiring: the same proxy setup, one repository interface. Cohesion is judged afterwards\n" \
    "from the captured tree, so the unrelated calls below are all the analyzer has to go on."

  wiring["CLARITY ANALYSIS REPORT"] = \
    "Wiring: the four trees captured above are passed to ClarityAnalyzer, and\n" \
    "ClarityReportRenderer prints a report per scenario plus the suite summary. The\n" \
    "analysis reads captured traces — no extra instrumentation, no second run of the code."

  # ---- minecraft (identical wiring on both halves; only the vocabulary differs) ----

  wiring["Refactored: Player Joins World"] = \
    "Wiring: no Spring, no annotations — every interface is wrapped with\n" \
    "NarrativeTraceProxy.trace(impl, Iface.class, context), and DualPathPipeline(new\n" \
    "Slf4jTraceEventListener()) turns the events into ordinary SLF4J log lines."

  wiring["Unrefactored: Player Joins World"] = \
    "Wiring: byte for byte the setup above — same proxies, same pipeline, same context\n" \
    "after a reset(). Only the class and method names differ, and that is the whole point."

  # ---- library (Kotlin, same proxy API, annotations on interfaces) ----

  wiring["Scenario 1: Successful Book Borrow"] = \
    "Wiring: Kotlin, no Spring — NarrativeTraceProxy.trace(...) around CatalogService,\n" \
    "MemberService and LendingService. @Narrated on LendingService.borrowBook supplies the\n" \
    "// line; cardNumber prints as [REDACTED] from @NotTraced on the parameter."

  wiring["Scenario 2: Book Unavailable"] = \
    "Wiring: the same three proxies after context.reset(). BookUnavailableException is\n" \
    "thrown by LendingService itself and the proxy records it on the way out — there is no\n" \
    "error-handling code in this path, and nothing to keep in sync when it changes."
}
