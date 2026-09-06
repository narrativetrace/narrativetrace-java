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

class TraceNodeTest {

  @Test
  void representsLeafCall() {
    var signature = new MethodSignature("OrderService", "placeOrder", List.of());
    var outcome = new TraceOutcome.Returned("\"order-42\"");
    var node = new TraceNode(signature, List.of(), outcome);

    assertThat(node.signature()).isSameAs(signature);
    assertThat(node.children()).isEmpty();
    assertThat(node.outcome()).isSameAs(outcome);
  }

  @Test
  void representsNestedTree() {
    var childSig = new MethodSignature("InventoryService", "checkStock", List.of());
    var child = new TraceNode(childSig, List.of(), new TraceOutcome.Returned("true"));

    var parentSig = new MethodSignature("OrderService", "placeOrder", List.of());
    var parent =
        new TraceNode(parentSig, List.of(child), new TraceOutcome.Returned("\"order-42\""));

    assertThat(parent.children()).hasSize(1);
    assertThat(parent.children().get(0).signature().className()).isEqualTo("InventoryService");
  }

  @Test
  void capturesDurationInNanoseconds() {
    var signature = new MethodSignature("OrderService", "placeOrder", List.of());
    var outcome = new TraceOutcome.Returned("\"order-42\"");
    long durationNanos = 24_000_000L; // 24ms

    var node = new TraceNode(signature, List.of(), outcome, durationNanos);

    assertThat(node.durationNanos()).isEqualTo(24_000_000L);
  }

  @Test
  void threeArgConstructorDefaultsStartTimeAndConcurrencyAndSpanContext() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var node = new TraceNode(signature, List.of(), new TraceOutcome.Returned("ok"));

    assertThat(node.startTimeNanos()).isZero();
    assertThat(node.concurrency()).isNull();
    assertThat(node.spanContext()).isNull();
  }

  @Test
  void fourArgConstructorDefaultsStartTimeAndConcurrencyAndSpanContext() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var node = new TraceNode(signature, List.of(), new TraceOutcome.Returned("ok"), 5_000_000L);

    assertThat(node.durationNanos()).isEqualTo(5_000_000L);
    assertThat(node.startTimeNanos()).isZero();
    assertThat(node.concurrency()).isNull();
    assertThat(node.spanContext()).isNull();
  }

  @Test
  void sixArgConstructorPreservesAllFields() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var children = List.<TraceNode>of();
    var outcome = new TraceOutcome.Returned("ok");

    var node = new TraceNode(signature, children, outcome, 10_000L, 999_000L, null);

    assertThat(node.signature()).isSameAs(signature);
    assertThat(node.children()).isSameAs(children);
    assertThat(node.outcome()).isSameAs(outcome);
    assertThat(node.durationNanos()).isEqualTo(10_000L);
    assertThat(node.startTimeNanos()).isEqualTo(999_000L);
    assertThat(node.concurrency()).isNull();
    assertThat(node.spanContext()).isNull();
  }

  @Test
  void sevenArgConstructorPreservesSpanContext() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var spanContext = TestSpanContext.create();
    var info =
        new ConcurrencyInfo("fork-1", "pool-1-thread-1", 42L, false, ConcurrencyKind.FORK_JOIN);

    var node =
        new TraceNode(
            signature,
            List.of(),
            new TraceOutcome.Returned("ok"),
            10_000L,
            500L,
            info,
            spanContext);

    assertThat(node.spanContext()).isSameAs(spanContext);
    assertThat(node.concurrency()).isSameAs(info);
  }

  @Test
  void withConcurrencyPreservesSpanContext() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var spanContext = TestSpanContext.create();
    var node =
        new TraceNode(
            signature,
            List.of(),
            new TraceOutcome.Returned("ok"),
            10_000L,
            500L,
            null,
            spanContext);

    var info =
        new ConcurrencyInfo("fork-1", "pool-1-thread-1", 42L, false, ConcurrencyKind.FORK_JOIN);
    var withInfo =
        new TraceNode(
            node.signature(),
            node.children(),
            node.outcome(),
            node.durationNanos(),
            node.startTimeNanos(),
            info,
            node.spanContext());

    assertThat(withInfo.spanContext()).isSameAs(spanContext);
    assertThat(withInfo.concurrency()).isSameAs(info);
  }

  @Test
  void sixArgConstructorWithConcurrencyInfo() {
    var signature = new MethodSignature("Svc", "method", List.of());
    var info =
        new ConcurrencyInfo("fork-1", "pool-1-thread-1", 42L, false, ConcurrencyKind.FORK_JOIN);

    var node =
        new TraceNode(signature, List.of(), new TraceOutcome.Returned("ok"), 10_000L, 500L, info);

    assertThat(node.concurrency()).isNotNull();
    assertThat(node.concurrency().groupId()).isEqualTo("fork-1");
    assertThat(node.concurrency().kind()).isEqualTo(ConcurrencyKind.FORK_JOIN);
  }
}
