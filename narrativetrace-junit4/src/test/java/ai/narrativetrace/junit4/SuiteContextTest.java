/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The suite owner's own contract: built once per test JVM, closed once, and closed safely. */
class SuiteContextTest {

  @Test
  void theSharedSuiteContextIsBuiltOnceForTheTestJvm() {
    assertThat(SuiteContext.shared()).isSameAs(SuiteContext.shared());
    assertThat(SuiteContext.shared().context()).isSameAs(SuiteContext.shared().context());
    assertThat(SuiteContext.shared().config()).isSameAs(SuiteContext.shared().config());
  }

  /**
   * The shutdown hook can fire while something still holds the context — and a test harness may
   * close twice. Both are defined: {@code close()} is idempotent and a publish into a closed
   * pipeline is dropped, never thrown. Exercised on a private instance, because closing the shared
   * one would take the rest of this test JVM's suite down with it.
   */
  @Test
  void closingTwiceIsSafeAndAPublishAfterwardsIsDroppedRatherThanThrown() {
    var suite = new SuiteContext();

    suite.close();
    suite.close();

    assertThatCode(
            () -> {
              suite.context().enterMethod(new MethodSignature("Late", "call", List.of()));
              suite.context().exitMethodWithReturn("ignored");
            })
        .doesNotThrowAnyException();
  }
}
