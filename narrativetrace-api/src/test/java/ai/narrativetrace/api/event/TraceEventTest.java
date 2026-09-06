/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TraceEventTest {

  // --- Step 2.1: EnterEvent carries SpanContext ---
  @Test
  void enterEventCarriesSpanContext() {
    var span = TestSpanContext.create();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var info = new ConcurrencyInfo("g1", "pool-1", 42L, false, ConcurrencyKind.FORK_JOIN);

    var event = new TraceEvent.EnterEvent(span, 1000L, sig, info);

    assertThat(event.spanContext()).isSameAs(span);
    assertThat(event.timestampNanos()).isEqualTo(1000L);
    assertThat(event.signature()).isEqualTo(sig);
    assertThat(event.concurrency()).isEqualTo(info);
    assertThat(event).isInstanceOf(TraceEvent.class);
  }

  // --- Step 2.2: 3-arg convenience defaults concurrency to null ---
  @Test
  void enterEventThreeArgConvenienceDefaultsConcurrency() {
    var span = TestSpanContext.create();
    var sig = new MethodSignature("Svc", "run", List.of());

    var event = new TraceEvent.EnterEvent(span, 1000L, sig);

    assertThat(event.concurrency()).isNull();
    assertThat(event.spanContext()).isSameAs(span);
    assertThat(event.signature()).isEqualTo(sig);
  }

  // --- Step 2.3: ExitEvent carries SpanContext ---
  @Test
  void exitEventCarriesSpanContext() {
    var span = TestSpanContext.create();
    var outcome = new TraceOutcome.Returned("\"ok\"");

    var event = new TraceEvent.ExitEvent(span, 2000L, outcome, null);

    assertThat(event.spanContext()).isSameAs(span);
    assertThat(event.timestampNanos()).isEqualTo(2000L);
    assertThat(event.outcome()).isEqualTo(outcome);
    assertThat(event.errorContext()).isNull();
    assertThat(event).isInstanceOf(TraceEvent.class);
  }

  @Test
  void exitEventCarriesEnteringSignature() {
    var span = TestSpanContext.create();
    var outcome = new TraceOutcome.Returned("\"ok\"");
    var signature = new MethodSignature("OrderService", "placeOrder", java.util.List.of());

    var event = new TraceEvent.ExitEvent(span, 2000L, outcome, null, signature);

    assertThat(event.signature()).isSameAs(signature);
  }

  @Test
  void exitEventSignatureDefaultsToNullFromCompatibilityConstructor() {
    var event =
        new TraceEvent.ExitEvent(
            TestSpanContext.create(), 2000L, new TraceOutcome.Returned("\"ok\""), null);

    assertThat(event.signature()).isNull();
  }

  // --- Step 2.4: lifecycle events unchanged ---
  @Test
  void forkCreatedEventUnchanged() {
    var fork = new TraceEvent.ForkCreatedEvent("g1", 500L);
    assertThat(fork.groupId()).isEqualTo("g1");
    assertThat(fork.timestampNanos()).isEqualTo(500L);

    var merge = new TraceEvent.MergeEvent("g1", 3, 600L);
    assertThat(merge.groupId()).isEqualTo("g1");
    assertThat(merge.memberCount()).isEqualTo(3);

    var fire = new TraceEvent.FireAndForgetEvent("g2", 700L);
    assertThat(fire.groupId()).isEqualTo("g2");
  }
}
