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
package ai.narrativetrace.api.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The exporter contract, exercised against a tree the API jar can build by itself — the runtime's
 * {@code DefaultTraceTree} lives in core and must stay unreachable from here.
 */
class TraceExporterTest {

  @Test
  void lambdaAssignmentAndCall() {
    var called = new AtomicBoolean(false);
    TraceExporter exporter = (tree, requestContext) -> called.set(true);

    exporter.export(emptyTree(), new RequestContext(200, 0L));

    assertThat(called).isTrue();
  }

  @Test
  void receivesTheTreeAndTheRequestContextItWasGiven() {
    var seenTree = new AtomicReference<TraceTree>();
    var seenContext = new AtomicReference<RequestContext>();
    TraceExporter exporter =
        (tree, requestContext) -> {
          seenTree.set(tree);
          seenContext.set(requestContext);
        };
    var tree = emptyTree();
    var context = new RequestContext(503, 42L);

    exporter.export(tree, context);

    assertThat(seenTree.get()).isSameAs(tree);
    assertThat(seenContext.get()).isEqualTo(context);
  }

  private static TraceTree emptyTree() {
    return new TraceTree() {
      @Override
      public List<TraceNode> roots() {
        return List.of();
      }

      @Override
      public boolean isEmpty() {
        return true;
      }
    };
  }
}
