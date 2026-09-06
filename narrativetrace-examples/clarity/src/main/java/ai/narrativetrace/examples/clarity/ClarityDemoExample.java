/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.examples.DemoTraces;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tutorial runner for the clarity-analysis example.
 *
 * <p>This example is designed to be read in layers. Each scenario demonstrates a different naming
 * quality level, then the clarity analyzer explains why the resulting trace scored well or poorly.
 *
 * <h2>Recommended walkthrough</h2>
 *
 * <ol>
 *   <li>Run the example and read the trace for "Guest books a room".
 *   <li>Compare it with "Legacy data processing" and notice how much story is lost when names are
 *       generic.
 *   <li>Read the rendered clarity report after each scenario.
 *   <li>Open the underlying service interfaces and implementations to see which naming choices led
 *       to each score.
 * </ol>
 *
 * <p>INTENT: Use this tutorial to teach developers how NarrativeTrace and the clarity subsystem
 * reinforce each other: better names produce better traces, and better traces make poor names
 * harder to ignore.
 */
public class ClarityDemoExample {

  private static final Logger logger = LoggerFactory.getLogger(ClarityDemoExample.class);

  public static void main(String[] args) {
    var pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    var renderer = new IndentedTextRenderer();
    var scenarioResults = new LinkedHashMap<String, TraceTree>();

    scenarioResults.put("Guest books a room", runReservationScenario(context, renderer));
    context.reset();
    scenarioResults.put("Booking via manager", runBookingManagerScenario(context, renderer));
    context.reset();
    scenarioResults.put("Legacy data processing", runLegacyProcessingScenario(context, renderer));
    context.reset();
    scenarioResults.put("Guest repository operations", runCohesionScenario(context, renderer));

    renderClarityReport(scenarioResults);
  }

  private static TraceTree runReservationScenario(
      NarrativeContext context, IndentedTextRenderer renderer) {
    logger.info("=== Scenario 1: Guest Books a Room (Excellent Naming) ===\n");

    var availabilityChecker =
        NarrativeTraceProxy.trace(
            new DefaultAvailabilityChecker(), AvailabilityChecker.class, context);
    var paymentGateway =
        NarrativeTraceProxy.trace(new DefaultPaymentGateway(), PaymentGateway.class, context);
    var reservationService =
        NarrativeTraceProxy.trace(
            new DefaultReservationService(availabilityChecker, paymentGateway),
            ReservationService.class,
            context);

    reservationService.confirmReservation(
        "G-1001", "deluxe", LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 18));

    var trace = context.captureTrace();
    DemoTraces.capture("Scenario 1: Guest Books a Room (Excellent Naming)", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));
    return trace;
  }

  private static TraceTree runBookingManagerScenario(
      NarrativeContext context, IndentedTextRenderer renderer) {
    logger.info("\n=== Scenario 2: Booking via Manager (Adequate Naming) ===\n");

    var bookingManager =
        NarrativeTraceProxy.trace(new DefaultBookingManager(), BookingManager.class, context);

    bookingManager.handleBooking("Jane Smith", "suite", "2025-07-01", "2025-07-05");

    var trace = context.captureTrace();
    DemoTraces.capture("Scenario 2: Booking via Manager (Adequate Naming)", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));
    return trace;
  }

  private static TraceTree runLegacyProcessingScenario(
      NarrativeContext context, IndentedTextRenderer renderer) {
    logger.info("\n=== Scenario 3: Legacy Data Processing (Poor Naming) ===\n");

    var dataProcessor =
        NarrativeTraceProxy.trace(new DefaultDataProcessor(), DataProcessor.class, context);

    dataProcessor.execute("room-data", 42);

    var trace = context.captureTrace();
    DemoTraces.capture("Scenario 3: Legacy Data Processing (Poor Naming)", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));
    return trace;
  }

  private static TraceTree runCohesionScenario(
      NarrativeContext context, IndentedTextRenderer renderer) {
    logger.info("\n=== Scenario 4: Guest Repository (Cohesion Mismatch) ===\n");

    var guestRepository =
        NarrativeTraceProxy.trace(new DefaultGuestRepository(), GuestRepository.class, context);

    guestRepository.findGuestById("G-1001");
    guestRepository.renderReport();
    guestRepository.dispatchEmail("G-1001", "Your reservation is confirmed");

    var trace = context.captureTrace();
    DemoTraces.capture("Scenario 4: Guest Repository (Cohesion Mismatch)", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));
    return trace;
  }

  private static void renderClarityReport(LinkedHashMap<String, TraceTree> scenarioResults) {
    var analyzer = new ClarityAnalyzer();
    var reportRenderer = new ClarityReportRenderer();

    logger.info("\n\n========================================");
    logger.info("         CLARITY ANALYSIS REPORT");
    logger.info("========================================\n");

    var combinedResults = new LinkedHashMap<String, ai.narrativetrace.clarity.ClarityResult>();
    for (var entry : scenarioResults.entrySet()) {
      var result = analyzer.analyze(entry.getValue());
      combinedResults.put(entry.getKey(), result);
      logger.info("\n{}", reportRenderer.render(entry.getKey(), result));
    }

    logger.info("\n\n{}", reportRenderer.renderSuiteReport(combinedResults));
  }
}
