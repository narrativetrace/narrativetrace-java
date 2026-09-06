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
package ai.narrativetrace.api.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The contract of {@link TraceTree#traceId()} as seen by an implementation outside the runtime: it
 * is optional, and an implementation that does not carry one says so rather than inventing one.
 */
class TraceTreeIdentityTest {

  @Test
  void anImplementationThatDoesNotOverrideItCarriesNoTraceId() {
    assertThat(treeOf(node()).traceId()).isNull();
  }

  @Test
  void anImplementationThatCarriesOneAnswersWithIt() {
    var assigned = TraceId.of("0af7651916cd43dd8448eb211c80319c");

    var tree =
        new TraceTree() {
          @Override
          public List<TraceNode> roots() {
            return List.of(node());
          }

          @Override
          public boolean isEmpty() {
            return false;
          }

          @Override
          public TraceId traceId() {
            return assigned;
          }
        };

    assertThat(tree.traceId()).isEqualTo(assigned);
  }

  private static TraceNode node() {
    return new TraceNode(
        new MethodSignature("OrderService", "placeOrder", List.of()),
        List.of(),
        new TraceOutcome.Returned("\"order-1\""),
        1_000_000L);
  }

  private static TraceTree treeOf(TraceNode... roots) {
    var rootList = List.of(roots);
    return new TraceTree() {
      @Override
      public List<TraceNode> roots() {
        return rootList;
      }

      @Override
      public boolean isEmpty() {
        return rootList.isEmpty();
      }
    };
  }
}
