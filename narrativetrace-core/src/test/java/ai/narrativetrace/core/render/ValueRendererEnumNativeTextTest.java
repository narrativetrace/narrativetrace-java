/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import org.junit.jupiter.api.Test;

/**
 * B-206, Java part: an enum's own text is scanned for secret shapes like any other leaf text.
 *
 * <p>INTENT: {@code Enum.toString()} is a per-constant overridable method and the constant's name
 * is application text; both end up in the trace. The family contract is shape, then sanitise, then
 * cap. The enum branch used to run only the sanitise-and-cap half, so a constant named after a JWT
 * printed it.
 */
class ValueRendererEnumNativeTextTest {

  private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";

  private final ValueRenderer renderer = new ValueRenderer();

  /** A constant whose own text is secret-shaped. */
  enum Token {
    SIGNED {
      @Override
      public String toString() {
        return JWT;
      }
    }
  }

  /** A constant whose text is a Luhn-valid card number. */
  enum Named {
    CARD_4111111111111111 {
      @Override
      public String toString() {
        return "4111111111111111";
      }
    }
  }

  @Test
  void anEnumWhoseToStringIsAJwtRendersTheMarkerOnBothPaths() {
    assertThat(renderer.render(Token.SIGNED)).isEqualTo("[REDACTED]");
    assertThat(renderer.renderStructured(Token.SIGNED))
        .isEqualTo(new RenderedValue.StringVal("[REDACTED]"));
  }

  @Test
  void anEnumWhoseToStringIsACardNumberRendersTheMarkerOnTheFlatPath() {
    assertThat(renderer.render(Named.CARD_4111111111111111)).isEqualTo("[REDACTED]");
  }

  @Test
  void anEnumWhoseToStringIsACardNumberRendersTheMarkerOnTheStructuredPath() {
    assertThat(renderer.renderStructured(Named.CARD_4111111111111111))
        .isEqualTo(new RenderedValue.StringVal("[REDACTED]"));
  }

  @Test
  void anOrdinaryEnumStillRendersItsName() {
    assertThat(renderer.render(Thread.State.RUNNABLE)).isEqualTo("RUNNABLE");
    assertThat(renderer.renderStructured(Thread.State.RUNNABLE))
        .isEqualTo(new RenderedValue.StringVal("RUNNABLE"));
  }
}
