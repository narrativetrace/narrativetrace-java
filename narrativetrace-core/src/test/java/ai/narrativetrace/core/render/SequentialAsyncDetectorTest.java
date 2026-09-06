/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class SequentialAsyncDetectorTest {

  @Test
  void twoNonOverlappingAsyncChildrenDetectedAsSequentialAsync() {
    var a = asyncNode("SvcA", "work", 1000L, 100_000L, "fork-1");
    var b = asyncNode("SvcB", "work", 200_000L, 80_000L, "fork-1");

    var result = SequentialAsyncDetector.analyze(List.of(a, b));

    assertThat(result.isSequentialAsync()).isTrue();
  }

  @Test
  void twoOverlappingAsyncChildrenNotSequentialAsync() {
    var a = asyncNode("SvcA", "work", 1000L, 200_000L, "fork-1");
    var b = asyncNode("SvcB", "work", 50_000L, 80_000L, "fork-1");

    var result = SequentialAsyncDetector.analyze(List.of(a, b));

    assertThat(result.isSequentialAsync()).isFalse();
  }

  @Test
  void singleAsyncChildNoClassification() {
    var a = asyncNode("SvcA", "work", 1000L, 100_000L, "fork-1");

    var result = SequentialAsyncDetector.analyze(List.of(a));

    assertThat(result.isSequentialAsync()).isFalse();
  }

  @Test
  void threeChildrenFirstTwoOverlapThirdDoesNotReturnsMixed() {
    var a = asyncNode("SvcA", "work", 1000L, 200_000L, "fork-1");
    var b = asyncNode("SvcB", "work", 50_000L, 150_000L, "fork-1");
    var c = asyncNode("SvcC", "work", 500_000L, 80_000L, "fork-1");

    var result = SequentialAsyncDetector.analyze(List.of(a, b, c));

    assertThat(result.isMixed()).isTrue();
    assertThat(result.isSequentialAsync()).isFalse();
  }

  private TraceNode asyncNode(
      String cls, String method, long startNanos, long durationNanos, String groupId) {
    var info = new ConcurrencyInfo(groupId, "pool-1", 1L, false, ConcurrencyKind.FORK_JOIN);
    return new TraceNode(
        new MethodSignature(cls, method, List.of()),
        List.of(),
        new TraceOutcome.Returned("ok"),
        durationNanos,
        startNanos,
        info);
  }
}
