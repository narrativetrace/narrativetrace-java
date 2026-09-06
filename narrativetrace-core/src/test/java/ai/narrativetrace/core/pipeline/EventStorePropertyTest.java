/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class EventStorePropertyTest {

  @Property
  void eventCountMatchesAddCount(@ForAll @IntRange(min = 0, max = 100) int count) {
    var store = new EventStore();
    for (int i = 0; i < count; i++) {
      store.add(enterEvent());
    }
    assertThat(store.events()).hasSize(count);
  }

  @Property
  void eventsPreserveInsertionOrder(@ForAll @IntRange(min = 1, max = 50) int count) {
    var store = new EventStore();
    var spanContexts = new ArrayList<SpanContext>();
    for (int i = 0; i < count; i++) {
      SpanContext sc = TestSpanContext.create();
      spanContexts.add(sc);
      store.add(
          new TraceEvent.EnterEvent(
              sc, System.nanoTime(), new MethodSignature("C", "m", List.of())));
    }
    for (int i = 0; i < count; i++) {
      assertThat(((TraceEvent.EnterEvent) store.events().get(i)).spanContext().spanId())
          .isEqualTo(spanContexts.get(i).spanId());
    }
  }

  private static TraceEvent.EnterEvent enterEvent() {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(), System.nanoTime(), new MethodSignature("C", "m", List.of()));
  }
}
