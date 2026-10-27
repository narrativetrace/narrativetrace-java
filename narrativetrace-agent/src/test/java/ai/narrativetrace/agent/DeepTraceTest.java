/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TraceDepth;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The opt-in deep mode: private methods of the classes under test are woven only when the agent is
 * asked for {@code depth=internal}, and recorded only inside a deep scope (one test's run).
 */
class DeepTraceTest {

  private static final String SAMPLE = "ai/narrativetrace/agent/sample/DeepSample";

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  @AfterEach
  void tearDown() {
    AgentRuntime.endDeepScope();
  }

  private Object load(TraceDepth depth) throws Exception {
    var bytes = getClass().getClassLoader().getResourceAsStream(SAMPLE + ".class").readAllBytes();
    var loader = new MultiClassLoader(getClass().getClassLoader());
    loader.addClass(
        SAMPLE.replace('/', '.'), ClassTransformer.transform(bytes, SAMPLE, loader, depth));
    return loader.loadClass(SAMPLE.replace('/', '.')).getDeclaredConstructor().newInstance();
  }

  private static Object call(Object target, String method, Object... args) throws Exception {
    var types = new Class<?>[args.length];
    for (int i = 0; i < args.length; i++) {
      types[i] = args[i] instanceof Integer ? int.class : args[i].getClass();
    }
    return target.getClass().getMethod(method, types).invoke(target, args);
  }

  private static List<String> names(List<TraceNode> nodes) {
    return nodes.stream().map(n -> n.signature().methodName()).toList();
  }

  @Test
  void defaultDepthTracesTheFormerBoundaryOnly() throws Exception {
    var sample = load(TraceDepth.PUBLIC);

    assertThat(call(sample, "total", 100, 2)).isEqualTo(225);

    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("total");
    assertThat(names(root.children())).containsExactly("surcharge");
    assertThat(names(root.children().get(0).children())).isEmpty();
  }

  @Test
  void deepDepthShowsThePrivateCallNestedUnderThePublicOneWithValues() throws Exception {
    var sample = load(TraceDepth.INTERNAL);
    AgentRuntime.beginDeepScope();

    call(sample, "total", 100, 2);

    var root = context.captureTrace().roots().get(0);
    assertThat(names(root.children())).containsExactly("withTax", "surcharge");
    var withTax = root.children().get(0);
    assertThat(withTax.signature().internal()).isTrue();
    assertThat(withTax.signature().parameters().get(0).name()).isEqualTo("net");
    assertThat(withTax.signature().parameters().get(0).renderedValue()).isEqualTo("200");
    assertThat(((TraceOutcome.Returned) withTax.outcome()).renderedValue()).isEqualTo("221");
    assertThat(root.signature().internal()).isFalse();
  }

  @Test
  void deepDepthRecordsTheSelfCallThroughThisAndTheStaticPrivateHelper() throws Exception {
    var sample = load(TraceDepth.INTERNAL);
    AgentRuntime.beginDeepScope();

    call(sample, "total", 100, 2);

    var surcharge = context.captureTrace().roots().get(0).children().get(1);
    assertThat(surcharge.signature().internal()).isFalse();
    assertThat(names(surcharge.children())).containsExactly("doubled");
    assertThat(surcharge.children().get(0).signature().internal()).isTrue();
  }

  @Test
  void aDeepWovenClassStaysShallowOutsideADeepScope() throws Exception {
    var sample = load(TraceDepth.INTERNAL);

    call(sample, "total", 100, 2);

    var root = context.captureTrace().roots().get(0);
    assertThat(names(root.children())).containsExactly("surcharge");
  }

  @Test
  void aClassNotUnderTestIsNeverDeepened() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.agent.sample,depth=internal");
    var transformer = new NarrativeClassFileTransformer(config);

    assertThat(config.depth()).isEqualTo(TraceDepth.INTERNAL);
    assertThat(transformer.transform(null, "java/util/ArrayList", null, null, new byte[0]))
        .isNull();
    assertThat(transformer.transform(null, "org/junit/Assert", null, null, new byte[0])).isNull();
  }

  @Test
  void deepSpansBeyondTheCapAreDroppedAndCountedWhilePublicSpansAreKept() throws Exception {
    var sample = load(TraceDepth.INTERNAL);
    AgentRuntime.beginDeepScope(2);

    call(sample, "total", 100, 2); // withTax, doubled: two deep spans, at the cap
    call(sample, "total", 100, 2); // two more deep spans, both over it
    var dropped = AgentRuntime.endDeepScope();

    assertThat(dropped).isEqualTo(2);
    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(2);
    assertThat(names(roots.get(1).children())).containsExactly("surcharge");
  }

  @Test
  void depthDefaultsToPublicAndReadsTheAgentArgument() {
    assertThat(AgentConfig.parse("packages=x").depth()).isEqualTo(TraceDepth.PUBLIC);
    assertThat(AgentConfig.parse("packages=x,depth=internal").depth())
        .isEqualTo(TraceDepth.INTERNAL);
    assertThat(AgentConfig.parse("packages=x,depth=nonsense").depth()).isEqualTo(TraceDepth.PUBLIC);
    assertThat(AgentConfig.parse("packages=x,maxInternalSpans=7").maxInternalSpans()).isEqualTo(7);
  }

  @Test
  void aBadCapFallsBackToTheDefaultRatherThanFailingThePremain() {
    var dflt = AgentConfig.DEFAULT_MAX_INTERNAL_SPANS;

    assertThat(AgentConfig.parse("packages=x,maxInternalSpans=lots").maxInternalSpans())
        .isEqualTo(dflt);
    assertThat(AgentConfig.parse("packages=x,maxInternalSpans=0").maxInternalSpans())
        .isEqualTo(dflt);
    assertThat(AgentConfig.parse("packages=x,maxInternalSpans=").maxInternalSpans())
        .isEqualTo(dflt);
    assertThat(AgentConfig.parse("packages=x").maxInternalSpans()).isEqualTo(dflt);
  }

  @Test
  void theFallbackPathReadsTheDocumentedProperties() {
    System.setProperty("narrativetrace.agent.depth", "internal");
    System.setProperty("narrativetrace.agent.maxInternalSpans", "9");
    try {
      var config = AgentConfig.parse(null);

      assertThat(config.depth()).isEqualTo(TraceDepth.INTERNAL);
      assertThat(config.maxInternalSpans()).isEqualTo(9);
    } finally {
      System.clearProperty("narrativetrace.agent.depth");
      System.clearProperty("narrativetrace.agent.maxInternalSpans");
    }
    assertThat(AgentConfig.parse(null).depth()).isEqualTo(TraceDepth.PUBLIC);
  }

  @Test
  void theOlderConfigShapesAreAlwaysPublic() {
    var five =
        new AgentConfig(
            List.of(), "n", ai.narrativetrace.api.config.TracingLevel.DETAIL, List.of(), List.of());

    assertThat(five.depth()).isEqualTo(TraceDepth.PUBLIC);
    assertThat(five.maxInternalSpans()).isEqualTo(AgentConfig.DEFAULT_MAX_INTERNAL_SPANS);
  }

  @Test
  void theInitializedRuntimeKnowsWhetherItWasArmed() {
    var before = AgentRuntime.getContext();
    try {
      AgentRuntime.initialize(AgentConfig.parse("packages=x,depth=internal,loggerName=deep-test"));
      assertThat(AgentRuntime.isDeepArmed()).isTrue();
      AgentRuntime.initialize(AgentConfig.parse("packages=x,loggerName=deep-test"));
      assertThat(AgentRuntime.isDeepArmed()).isFalse();
    } finally {
      AgentRuntime.setContext(before);
    }
  }

  @Test
  void theThreeArgumentClassVisitorStaysShallow() throws Exception {
    var bytes = getClass().getClassLoader().getResourceAsStream(SAMPLE + ".class").readAllBytes();
    var reader = new org.objectweb.asm.ClassReader(bytes);
    var collector = new MethodMetadataCollector();
    reader.accept(collector, 0);
    var writer = new org.objectweb.asm.ClassWriter(reader, 0);
    reader.accept(new NarrativeClassVisitor(writer, SAMPLE, collector.getMetadata()), 0);

    assertThat(new String(writer.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("isDeepActive");
  }
}
