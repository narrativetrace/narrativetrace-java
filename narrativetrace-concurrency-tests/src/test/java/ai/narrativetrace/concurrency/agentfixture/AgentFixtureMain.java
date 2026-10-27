/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.agentfixture;

import ai.narrativetrace.concurrency.shop.Checkout;
import ai.narrativetrace.concurrency.shop.ForkCheckout;
import ai.narrativetrace.concurrency.shop.InMemoryInventory;
import ai.narrativetrace.concurrency.support.Overlap;
import ai.narrativetrace.concurrency.support.Requests;
import ai.narrativetrace.concurrency.support.Workers;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * The program the agent route forks: a servlet-filter request loop in a JVM that has the real
 * standalone agent attached, so the shop classes (package {@code
 * ai.narrativetrace.concurrency.shop}) are traced by bytecode, never by a proxy. It writes each
 * exported request to a file; the parent test reads them back and asserts.
 *
 * <p>Scenarios: {@code servlet-fork} (the filter, a woven {@code ForkGroup} checkout) and {@code
 * spring-async} (a Spring context whose filter bean comes from {@code
 * NarrativeTraceWebConfiguration} and whose woven checkout awaits {@code @Async} calls). Each runs
 * three phases: one request, eight overlapping ones, and two consecutive requests handed to the
 * same single pooled worker.
 */
public final class AgentFixtureMain {

  private AgentFixtureMain() {}

  public static void main(String[] args) throws Exception {
    var scenario = args[0];
    var outDir = Path.of(args[1]);
    Files.createDirectories(outDir);
    var exporter = new FileExporter(outDir);
    var context = agentContext();
    switch (scenario) {
      case "servlet-fork" -> servletFork(context, exporter);
      case "spring-async" -> SpringAsyncScenario.run(context, exporter);
      default -> throw new IllegalArgumentException("unknown scenario " + scenario);
    }
    System.out.println("SCENARIO_DONE " + scenario);
    System.exit(0);
  }

  /** The agent's own shared context — what its woven classes publish into. */
  static NarrativeContext agentContext() throws ReflectiveOperationException {
    var runtime = Class.forName("ai.narrativetrace.agent.AgentRuntime");
    return (NarrativeContext) runtime.getMethod("getContext").invoke(null);
  }

  private static void servletFork(NarrativeContext context, FileExporter exporter)
      throws Exception {
    var filter = new NarrativeTraceFilter(context, exporter);
    var overlap = new Overlap();
    var pool = Executors.newFixedThreadPool(4, Workers.factory());
    Checkout checkout = new ForkCheckout(context, pool, new InMemoryInventory());
    runPhases(exporter, overlap, id -> Requests.throughFilter(filter, overlap, checkout, id));
    pool.shutdownNow();
    var single = Executors.newFixedThreadPool(1, Workers.factory());
    Checkout reuse = new ForkCheckout(context, single, new InMemoryInventory());
    runReuse(exporter, id -> Requests.throughFilter(filter, overlap, reuse, id));
    single.shutdownNow();
  }

  static void runPhases(FileExporter exporter, Overlap overlap, Requests.Serve serve)
      throws Exception {
    exporter.phase("single");
    serve.request(Requests.id(1));
    exporter.phase("concurrent");
    overlap.expect(8);
    Requests.concurrently(Requests.ids(100, 8), serve);
    overlap.expect(1);
  }

  static void runReuse(FileExporter exporter, Requests.Serve serve) throws Exception {
    exporter.phase("reuse");
    serve.request(Requests.id(21));
    serve.request(Requests.id(22));
  }
}
