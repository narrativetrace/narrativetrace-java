/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ThreadLocalNarrativeContextPropertyTest {

  @Property
  void spanIdsAreUniqueHexStrings(@ForAll @IntRange(min = 1, max = 50) int callCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    var seen = new HashSet<SpanId>();
    for (int i = 0; i < callCount; i++) {
      SpanId spanId = context.enterMethod(signature("C" + i, "m" + i));
      assertThat(spanId.value()).matches("[0-9a-f]{16}");
      assertThat(seen.add(spanId)).isTrue();
      context.exitMethodWithReturn("null");
    }
  }

  @Property
  void spanIdsAreUnique(@ForAll @IntRange(min = 1, max = 100) int callCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    var spanIds = new HashSet<SpanId>();
    for (int i = 0; i < callCount; i++) {
      SpanId spanId = context.enterMethod(signature("C", "m" + i));
      assertThat(spanIds.add(spanId)).isTrue();
      context.exitMethodWithReturn("null");
    }
  }

  @Property
  void captureTraceRootsMatchTopLevelCalls(@ForAll @IntRange(min = 1, max = 20) int rootCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    for (int i = 0; i < rootCount; i++) {
      context.enterMethod(signature("Svc" + i, "run"));
      context.exitMethodWithReturn("null");
    }
    var trace = context.captureTrace();
    assertThat(trace.roots()).hasSize(rootCount);
  }

  @Property
  void nestedCallsProduceChildNodes(@ForAll @IntRange(min = 1, max = 10) int depth) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    for (int i = 0; i < depth; i++) {
      context.enterMethod(signature("Level" + i, "enter"));
    }
    for (int i = 0; i < depth; i++) {
      context.exitMethodWithReturn("null");
    }
    var trace = context.captureTrace();
    assertThat(trace.roots()).hasSize(1);
    var node = trace.roots().get(0);
    for (int i = 1; i < depth; i++) {
      assertThat(node.children()).hasSize(1);
      node = node.children().get(0);
    }
    assertThat(node.children()).isEmpty();
  }

  @Property
  void resetClearsAllState(@ForAll @IntRange(min = 1, max = 20) int callCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    for (int i = 0; i < callCount; i++) {
      context.enterMethod(signature("C", "m"));
      context.exitMethodWithReturn("null");
    }
    context.reset();
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Property
  void nestedSpansHaveCorrectParentChildRelationship(
      @ForAll @IntRange(min = 2, max = 10) int depth) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    SpanId[] spanIds = new SpanId[depth];
    for (int i = 0; i < depth; i++) {
      spanIds[i] = context.enterMethod(signature("L" + i, "m"));
    }
    var events = context.events();
    for (int i = depth - 1; i >= 1; i--) {
      final SpanId childSpanId = spanIds[i];
      final SpanId expectedParentSpanId = spanIds[i - 1];
      var childEnter =
          events.stream()
              .filter(e -> e instanceof TraceEvent.EnterEvent)
              .map(e -> (TraceEvent.EnterEvent) e)
              .filter(e -> e.spanContext().spanId().equals(childSpanId))
              .findFirst()
              .orElseThrow();
      assertThat(childEnter.spanContext().parentSpanId()).isEqualTo(expectedParentSpanId);
    }
    final SpanId rootSpanId = spanIds[0];
    var rootEnter =
        events.stream()
            .filter(e -> e instanceof TraceEvent.EnterEvent)
            .map(e -> (TraceEvent.EnterEvent) e)
            .filter(e -> e.spanContext().spanId().equals(rootSpanId))
            .findFirst()
            .orElseThrow();
    assertThat(rootEnter.spanContext().parentSpanId()).isNull();
    for (int i = 0; i < depth; i++) {
      context.exitMethodWithReturn("null");
    }
  }

  @Property
  void eventsAlternateEnterExit(@ForAll @IntRange(min = 1, max = 30) int callCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    for (int i = 0; i < callCount; i++) {
      context.enterMethod(signature("C", "m"));
      context.exitMethodWithReturn("null");
    }
    var events = context.events();
    assertThat(events).hasSize(callCount * 2);
    for (int i = 0; i < events.size(); i++) {
      if (i % 2 == 0) {
        assertThat(events.get(i)).isInstanceOf(TraceEvent.EnterEvent.class);
      } else {
        assertThat(events.get(i)).isInstanceOf(TraceEvent.ExitEvent.class);
      }
    }
  }

  @Property
  void enterExitHandlesMatch(@ForAll @IntRange(min = 1, max = 30) int callCount) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    for (int i = 0; i < callCount; i++) {
      context.enterMethod(signature("C", "m"));
      context.exitMethodWithReturn("null");
    }
    var events = context.events();
    for (int i = 0; i < events.size(); i += 2) {
      var enter = (TraceEvent.EnterEvent) events.get(i);
      var exit = (TraceEvent.ExitEvent) events.get(i + 1);
      assertThat(exit.spanContext().spanId()).isEqualTo(enter.spanContext().spanId());
    }
  }

  private static MethodSignature signature(String className, String methodName) {
    return new MethodSignature(className, methodName, List.of());
  }
}
