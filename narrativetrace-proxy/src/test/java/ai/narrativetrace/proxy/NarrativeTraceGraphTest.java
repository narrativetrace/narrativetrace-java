/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.Test;

class NarrativeTraceGraphTest {

  interface Converter {
    long toBase(long amount);
  }

  interface Ledger {
    long record(long amount);
  }

  @Test
  void createdGraphTracesASingleCollaboratorIntoItsOwnContext() {
    var graph = NarrativeTraceGraph.create();
    Converter converter = graph.trace(amount -> amount * 2, Converter.class);

    converter.toBase(21);

    var tree = graph.context().captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("toBase");
  }

  @Test
  void wiresAGraphOfCollaboratorsIntoOneSharedContext() {
    var graph = NarrativeTraceGraph.create();
    Converter converter = graph.trace(amount -> amount * 2, Converter.class);
    Ledger ledger = graph.trace(amount -> converter.toBase(amount) + 1, Ledger.class);

    ledger.record(10);

    var tree = graph.context().captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("record");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("toBase");
  }

  @Test
  void onReusesTheSuppliedContext() {
    var context = new ThreadLocalNarrativeContext();
    var graph = NarrativeTraceGraph.on(context);

    assertThat(graph.context()).isSameAs(context);
  }

  @Test
  void onRejectsNullContext() {
    assertThatThrownBy(() -> NarrativeTraceGraph.on(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
