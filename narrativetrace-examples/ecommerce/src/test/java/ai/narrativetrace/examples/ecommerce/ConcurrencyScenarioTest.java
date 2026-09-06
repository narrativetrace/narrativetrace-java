/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.FireAndForgetGroup;
import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.export.JsonExporter;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcurrencyScenarioTest {

  private ThreadLocalNarrativeContext context;
  private DiscountService discounts;
  private ShippingEstimateService shipping;
  private ProductCatalogService catalog;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    discounts =
        NarrativeTraceProxy.trace(new InMemoryDiscountService(), DiscountService.class, context);
    shipping =
        NarrativeTraceProxy.trace(
            new InMemoryShippingEstimateService(), ShippingEstimateService.class, context);
    catalog =
        NarrativeTraceProxy.trace(
            new InMemoryProductCatalogService(), ProductCatalogService.class, context);
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  // --- Scenario 7: Fork-Join ---

  @Test
  void forkJoinTwoTasksProduceCorrectTrace() {
    var md = runForkJoinScenario();

    assertThat(md).contains("⑂ fork [2 tasks]");
    assertThat(md).contains("⑃ join");
    assertThat(md).contains("DiscountService.calculateDiscount");
    assertThat(md).contains("ShippingEstimateService.estimate");
  }

  @Test
  void forkJoinMergedChildrenCarryCorrectThreadNames() {
    var md = runForkJoinScenario();

    assertThat(md).contains("[thread:");
  }

  @Test
  void forkJoinJoinLineShowsWallTime() {
    var md = runForkJoinScenario();

    assertThat(md).containsPattern("⑃ join — \\d+ms");
  }

  // --- Scenario 8: Fire-and-Forget ---

  @Test
  void fireAndForgetParentTraceHasLauncherNode() {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));

    var fanf = FireAndForgetGroup.create(context, "OrderService");
    Supplier<Boolean> rawNotify = () -> true;
    CompletableFuture.supplyAsync(fanf.wrap(rawNotify), executor);

    context.exitMethodWithReturn("\"order-1\"");

    assertThat(renderMarkdown()).contains("⤳ fire-and-forget");
  }

  @Test
  void fireAndForgetChildRootsCollectedWithMatchingGroupId() throws Exception {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));

    var fanf = FireAndForgetGroup.create(context, "OrderService");
    Supplier<Discount> rawNotify = () -> discounts.calculateDiscount("C-1234", "SKU-MECHANICAL-KB");
    CompletableFuture.supplyAsync(fanf.wrap(rawNotify), executor).get();

    context.exitMethodWithReturn("\"order-1\"");

    var childRoots = fanf.childRoots();
    assertThat(childRoots).isNotEmpty();
    assertThat(childRoots.get(0).concurrency()).isNotNull();
    assertThat(childRoots.get(0).concurrency().groupId()).isEqualTo(fanf.groupId());
    assertThat(childRoots.get(0).concurrency().kind()).isEqualTo(ConcurrencyKind.FIRE_AND_FORGET);
  }

  // --- Scenario 9: Sequential-Async Anti-Pattern ---

  @Test
  void sequentialAsyncChildrenFlaggedAsAwaitedSequentially() {
    var md = runSequentialAsyncScenario();

    assertThat(md).contains("[async, awaited sequentially]");
  }

  @Test
  void sequentialAsyncOptimizationHintShowsSavings() {
    var md = runSequentialAsyncScenario();

    assertThat(md).containsPattern("⚡ Sequential async: total \\d+ms, parallelizable to ~\\d+ms");
  }

  // --- Scenario 10: Mixed Concurrency ---

  @Test
  void mixedConcurrencyPatternsInCorrectOrder() {
    var md = runMixedConcurrencyScenario();

    assertThat(md).contains("ProductCatalogService.lookupPrice");
    assertThat(md).contains("⑂ fork [2 tasks]");
    assertThat(md).contains("⤳ fire-and-forget");

    int lookupPos = md.indexOf("lookupPrice");
    int forkPos = md.indexOf("⑂ fork");
    int fanfPos = md.indexOf("⤳ fire-and-forget");
    assertThat(lookupPos).isLessThan(forkPos);
    assertThat(forkPos).isLessThan(fanfPos);
  }

  @Test
  void mixedConcurrencyJsonExportIncludesConcurrencyFields() {
    context.enterMethod(new MethodSignature("OrderService", "fullPipeline", List.of()));

    var group = ForkGroup.create(context);
    Supplier<Discount> rawDiscount =
        () -> discounts.calculateDiscount("C-1234", "SKU-MECHANICAL-KB");
    CompletableFuture.supplyAsync(group.wrap(rawDiscount), executor).join();
    group.merge();

    context.exitMethodWithReturn("\"done\"");

    var json = new JsonExporter().export(context.captureTrace());
    assertThat(json).contains("\"concurrency\"");
    assertThat(json).contains("\"kind\": \"fork-join\"");
  }

  // --- Scenario 11: Virtual thread flag ---

  @Test
  void virtualThreadFlagPreservedInConcurrencyInfo() {
    var info = new ConcurrencyInfo("fork-v1", "vthread-1", 999, true, ConcurrencyKind.FORK_JOIN);
    var node = leafNode("SvcA", "work", "\"ok\"", 10_000_000L, info);

    assertThat(node.concurrency().virtual()).isTrue();
    assertThat(node.concurrency().threadId()).isEqualTo(999);
  }

  @Test
  void virtualThreadFlagShowsInMarkdownOutput() {
    var info = new ConcurrencyInfo("fork-v1", "vthread-1", 999, true, ConcurrencyKind.FORK_JOIN);
    var child = leafNode("SvcA", "work", "\"ok\"", 10_000_000L, info);
    var parent =
        new TraceNode(
            new MethodSignature("Parent", "run", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            20_000_000L);

    var md = new MarkdownRenderer().render(new DefaultTraceTree(List.of(parent)));

    assertThat(md).contains("[thread: vthread-1 (virtual)]");
  }

  // --- Edge case ---

  @Test
  void captureLevelOffProducesEmptyTrace() {
    var offConfig = new NarrativeTraceConfig(TracingLevel.OFF);
    var offContext = new ThreadLocalNarrativeContext(offConfig);
    offContext.reset();
    var offCatalog =
        NarrativeTraceProxy.trace(
            new InMemoryProductCatalogService(), ProductCatalogService.class, offContext);

    offCatalog.lookupPrice("SKU-MECHANICAL-KB");

    assertThat(offContext.captureTrace().roots()).isEmpty();
  }

  // --- Helpers ---

  private String runForkJoinScenario() {
    context.enterMethod(new MethodSignature("OrderService", "enrichPrice", List.of()));
    catalog.lookupPrice("SKU-MECHANICAL-KB");
    forkDiscountAndShipping();
    context.exitMethodWithReturn("\"enriched\"");
    return renderMarkdown();
  }

  private String runSequentialAsyncScenario() {
    context.enterMethod(new MethodSignature("OrderService", "sequentialAsync", List.of()));

    var group = ForkGroup.create(context);
    Supplier<Discount> rawDiscount =
        () -> discounts.calculateDiscount("C-1234", "SKU-MECHANICAL-KB");
    Supplier<ShippingEstimate> rawShipping = () -> shipping.estimate("SKU-MECHANICAL-KB", 2);
    CompletableFuture.supplyAsync(group.wrap(rawDiscount), executor).join();
    CompletableFuture.supplyAsync(group.wrap(rawShipping), executor).join();
    group.merge();

    context.exitMethodWithReturn("\"done\"");
    return renderMarkdown();
  }

  private String runMixedConcurrencyScenario() {
    context.enterMethod(new MethodSignature("OrderService", "fullPipeline", List.of()));
    catalog.lookupPrice("SKU-MECHANICAL-KB");
    forkDiscountAndShipping();

    var fanf = FireAndForgetGroup.create(context, "OrderService");
    Supplier<Boolean> rawNotify = () -> true;
    CompletableFuture.supplyAsync(fanf.wrap(rawNotify), executor);

    context.exitMethodWithReturn("\"done\"");
    return renderMarkdown();
  }

  private void forkDiscountAndShipping() {
    var group = ForkGroup.create(context);
    Supplier<Discount> rawDiscount =
        () -> discounts.calculateDiscount("C-1234", "SKU-MECHANICAL-KB");
    Supplier<ShippingEstimate> rawShipping = () -> shipping.estimate("SKU-MECHANICAL-KB", 2);
    var df = CompletableFuture.supplyAsync(group.wrap(rawDiscount), executor);
    var sf = CompletableFuture.supplyAsync(group.wrap(rawShipping), executor);
    CompletableFuture.allOf(df, sf).join();
    group.merge();
  }

  private static TraceNode leafNode(
      String cls, String method, String returnValue, long durationNanos, ConcurrencyInfo info) {
    return new TraceNode(
        new MethodSignature(cls, method, List.of()),
        List.of(),
        new TraceOutcome.Returned(returnValue),
        durationNanos,
        0L,
        info);
  }

  private String renderMarkdown() {
    return new MarkdownRenderer().render(context.captureTrace());
  }
}
