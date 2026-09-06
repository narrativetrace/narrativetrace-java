/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy.graph;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceGraph;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link NarrativeTraceGraph} from a package other than the proxy's own, so the proxy's
 * cross-package reflection (setAccessible) is really exercised — same-package tests give false
 * confidence.
 */
class NarrativeTraceGraphCrossPackageTest {

  interface Reader {
    String read();
  }

  interface Writer {
    void write(String value);
  }

  static final class ReaderWriter implements Reader, Writer {
    private String stored = "";

    @Override
    public String read() {
      return stored;
    }

    @Override
    public void write(String value) {
      stored = value;
    }
  }

  @Test
  void tracesAMultiInterfaceCollaboratorAcrossPackageBoundary() {
    var graph = NarrativeTraceGraph.create();
    Object proxy = graph.trace(new ReaderWriter(), new Class<?>[] {Reader.class, Writer.class});

    ((Writer) proxy).write("hello");
    var value = ((Reader) proxy).read();

    assertThat(value).isEqualTo("hello");
    var tree = graph.context().captureTrace();
    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("write");
    assertThat(tree.roots().get(1).signature().methodName()).isEqualTo("read");
  }

  @Test
  void wiresACollaboratorGraphAcrossPackageBoundary() {
    var graph = NarrativeTraceGraph.create();
    Reader inner = graph.trace(() -> "inner", Reader.class);
    Writer outer = graph.trace(value -> inner.read(), Writer.class);

    outer.write("ignored");

    var tree = graph.context().captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("write");
    assertThat(tree.roots().get(0).children().get(0).signature().methodName()).isEqualTo("read");
  }

  /**
   * The inactive path must open a package-private interface's methods too. Accessibility moved out
   * of the invocation handler into the cached metadata, which the handler now looks up *before* the
   * {@code isActive()} gate for exactly this reason: with tracing off, `Method.invoke` on an
   * interface this package cannot see would throw `IllegalAccessException` if nothing had opened
   * it. A same-package test cannot see that failure — hence this one.
   */
  @Test
  void invokesAPackagePrivateInterfaceWithTracingOff() {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF));
    Reader traced = NarrativeTraceProxy.trace(() -> "inner", Reader.class, context);

    assertThat(traced.read()).isEqualTo("inner");
    assertThat(context.captureTrace().isEmpty()).isTrue();
  }
}
