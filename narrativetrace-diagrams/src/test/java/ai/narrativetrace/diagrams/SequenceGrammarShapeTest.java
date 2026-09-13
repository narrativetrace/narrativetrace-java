/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.tree.TreeWalk;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link DiagramLabel} is supposed to make "no raw trace string reaches a grammar hook" a
 * structural fact rather than a convention. This test is the receipt, checked on the interface's
 * shape rather than on any one call site: {@link SequenceGrammar} cannot regress to a {@code
 * String} parameter without failing here.
 *
 * <p><b>@llmNote</b> This module has no ArchUnit dependency (unlike core/api/proxy/agent), so the
 * check is a plain reflection assertion on {@link SequenceGrammar}'s declared methods rather than
 * an architecture rule — equivalent in effect, per the walk-and-grammar ruling's own fallback.
 */
class SequenceGrammarShapeTest {

  @Test
  void noHookAcceptsARawString() {
    for (Method method : SequenceGrammar.class.getDeclaredMethods()) {
      assertThat(List.of(method.getParameterTypes()))
          .as(
              "SequenceGrammar.%s must not take a raw String — trace-derived text must arrive as a"
                  + " DiagramLabel",
              method.getName())
          .doesNotContain(String.class);
    }
  }

  @Test
  void everyHookParameterIsADiagramLabelOrAWalkReason() {
    for (Method method : SequenceGrammar.class.getDeclaredMethods()) {
      for (var type : method.getParameterTypes()) {
        assertThat(type)
            .as("SequenceGrammar.%s has an unexpected parameter type %s", method.getName(), type)
            .isIn(DiagramLabel.class, TreeWalk.Reason.class);
      }
    }
  }

  @Test
  void everyHookReturnsAString() {
    for (Method method : SequenceGrammar.class.getDeclaredMethods()) {
      assertThat(method.getReturnType())
          .as("SequenceGrammar.%s should return the composed diagram text", method.getName())
          .isEqualTo(String.class);
    }
  }
}
