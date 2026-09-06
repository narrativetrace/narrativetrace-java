/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * The redaction claim an exception message used to be outside of.
 *
 * <p>INTENT: {@code SecretValueShapes} exists because a bearer token arrives with no name at all,
 * and an exception message is exactly such a place — the one place the axis built for it did not
 * reach, because {@code TraceOutcome.Threw} carries the live {@code Throwable} past every renderer
 * rather than text the policy has seen.
 */
class ExceptionMessageTest {

  private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";

  @Test
  void aCredentialShapedMessageIsHiddenLikeAParameterValue() {
    var asParameter = new ValueRenderer().render(JWT);
    var asMessage = ExceptionMessage.of(new IllegalArgumentException(JWT));

    assertThat(asParameter).isEqualTo(RedactionPolicy.MARKER);
    assertThat(asMessage).isEqualTo(RedactionPolicy.MARKER).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
  }

  @Test
  void aLuhnValidCardNumberInAMessageIsHiddenToo() {
    assertThat(ExceptionMessage.of(new IllegalStateException("4111111111111111")))
        .isEqualTo(RedactionPolicy.MARKER);
  }

  /**
   * The narrowness is the point: a message is what a reader acts on, so only the three shapes the
   * value axis recognises are hidden. A message that merely mentions a password is diagnostics.
   */
  @Test
  void anOrdinaryDiagnosticMessageIsLeftAlone() {
    assertThat(ExceptionMessage.of(new IllegalStateException("password rejected for user ada")))
        .isEqualTo("password rejected for user ada");
  }

  @Test
  void aControlCharacterInAMessageIsRenderedInert() {
    var message = "denied" + (char) 0x000a + "GET /admin 200" + (char) 0x001b + "[31m";

    assertThat(ExceptionMessage.of(new IllegalStateException(message)))
        .isEqualTo("denied\\nGET /admin 200\\u001b[31m")
        .doesNotContain(String.valueOf((char) 0x000a));
  }

  @Test
  void anAbsentMessageStaysAbsentRatherThanBecomingText() {
    assertThat(ExceptionMessage.of(new IllegalStateException())).isNull();
    assertThat(ExceptionMessage.text(new IllegalStateException())).isEqualTo("null");
  }

  @Test
  void aNullThrowableAnswersNullInsteadOfFailingTheEmitter() {
    assertThat(ExceptionMessage.of(null)).isNull();
  }

  /**
   * {@code getMessage()} is application code, and it runs at emission time on whichever thread is
   * rendering. A builder that recurses used to raise {@code StackOverflowError} inside a renderer,
   * once per emitter; an observability failure may never become an application failure.
   */
  @Test
  void aMessageThatCannotBeReadDegradesInsteadOfEscaping() {
    var hostile =
        new RuntimeException() {
          @Override
          public String getMessage() {
            throw new IllegalStateException("message builder failed");
          }
        };

    assertThatCode(() -> ExceptionMessage.of(hostile)).doesNotThrowAnyException();
    assertThat(ExceptionMessage.of(hostile)).isEqualTo("<error>");
  }

  @Test
  void aMessageThatRecursesForeverDegradesToo() {
    var hostile =
        new RuntimeException() {
          @Override
          public String getMessage() {
            return getMessage();
          }
        };

    assertThat(ExceptionMessage.of(hostile)).isEqualTo("<error>");
  }
}
