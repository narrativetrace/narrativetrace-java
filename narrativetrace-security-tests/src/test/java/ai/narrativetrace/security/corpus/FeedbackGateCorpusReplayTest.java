/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.feedback.ValueFreeCheck;
import ai.narrativetrace.tooling.feedback.ValueFreeRule;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The shared corpus replayed against the value-free gate, one row at a time.
 *
 * <p>INTENT: {@code feedback.json} is the cross-runtime master for this gate — every runtime copies
 * it verbatim and reimplements only its reader — so what it means has to be asserted here rather
 * than agreed in prose. A row is a decision: "this text must never reach a public issue, for this
 * named reason", or "this text must be filable, or an agent will learn to work around the gate".
 *
 * <p><b>@llmNote</b> A rejected row asserts the named rule is AMONG the refusing rules, not that it
 * is the only one. Several rules firing on one line is the normal case — a pasted rendered trace
 * breaks the call rule and the duration rule together — and demanding exactness would make the
 * corpus a record of this implementation's internals rather than of the product's promise.
 */
class FeedbackGateCorpusReplayTest {

  static List<FeedbackCase> rows() {
    return HostileCorpus.feedbacks();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  void everyRowIsDecidedTheWayItDeclares(FeedbackCase row) {
    List<String> refusing =
        ValueFreeCheck.rulesRefusing(row.value()).stream().map(ValueFreeRule::id).toList();

    if (row.mustBeRejected()) {
      assertThat(refusing)
          .as("%s (%s) must be refused by %s", row.id(), row.description(), row.rule())
          .contains(row.rule());
    } else {
      assertThat(refusing)
          .as("%s (%s) must be filable — no rule may refuse it", row.id(), row.description())
          .isEmpty();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  void everyRowDeclaresARuleExactlyWhenItIsRejected(FeedbackCase row) {
    if (row.mustBeRejected()) {
      assertThat(row.rule()).as("%s is rejected and must name its rule", row.id()).isNotNull();
      assertThat(ValueFreeRule.all())
          .as("%s names rule %s, which must exist", row.id(), row.rule())
          .anyMatch(rule -> rule.id().equals(row.rule()));
    } else {
      assertThat(row.rule()).as("%s is accepted and must name no rule", row.id()).isNull();
    }
  }

  /** Every rule carries at least one row: a rule nobody replays is a rule nobody has tested. */
  @org.junit.jupiter.api.Test
  void everyRuleIsExercisedByAtLeastOneRow() {
    List<String> replayed = rows().stream().map(FeedbackCase::rule).filter(r -> r != null).toList();

    assertThat(ValueFreeRule.all())
        .allSatisfy(rule -> assertThat(replayed).as(rule.id()).contains(rule.id()));
  }
}
