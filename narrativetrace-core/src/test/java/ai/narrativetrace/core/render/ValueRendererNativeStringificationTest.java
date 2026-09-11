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

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.RenderedValue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The 2026-09-11 family invariant: <b>a type's own stringification is never trusted while the type
 * has state.</b>
 *
 * <p>INTENT: The defect this pins was found by a five-runtime investigation, not by a test, and it
 * had survived a year of suites pointed straight at redaction. A plain {@code Login{username,
 * password}} with a hand-written {@code toString()} printed the password at depth zero — no
 * nesting, no wrapper, no annotation involved — because the trust check asked only whether the
 * class declared a {@code @NotTraced} field and never consulted the name deny-list, the field
 * types, or anything one level down. Every assertion here is a channel that was open.
 *
 * <p><b>@llmNote</b> Both render paths are asserted on every rule. {@code renderStructured} is what
 * the OpenTelemetry exporter reads; a rule enforced on one path and not the other is a leak with
 * extra steps, and the two dispatch through one {@code rendersItsOwnString} precisely so they
 * cannot drift.
 */
class ValueRendererNativeStringificationTest {

  /**
   * The house JWT fixture: shape-matched by {@link SecretValueShapes}, obviously not a real key.
   */
  private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";

  private final ValueRenderer renderer = new ValueRenderer();

  // ------------------------------------------------------------------ the channel that was open

  /** The reported shape: a deny-listed field name and a hand-written {@code toString}. */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  static final class Login {
    private final String username;
    private final String password;

    Login(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Override
    public String toString() {
      return "Login{username=" + username + ", password=" + password + "}";
    }
  }

  @Test
  void aDenyListedFieldIsHiddenEvenWhenTheClassStringifiesItself() {
    var rendered = renderer.render(new Login("ada", "hunter2"));

    assertThat(rendered)
        .isEqualTo("Login{username: \"ada\", password: [REDACTED]}")
        .doesNotContain("hunter2");
  }

  @Test
  void theStructuredPathHidesItToo() {
    var rendered = renderer.renderStructured(new Login("ada", "hunter2"));

    assertThat(rendered)
        .isEqualTo(
            new RenderedValue.ObjectVal(
                "Login",
                Map.of(
                    "username",
                    new RenderedValue.StringVal("ada"),
                    "password",
                    new RenderedValue.StringVal(RedactionPolicy.MARKER))));
  }

  /** A record whose component is annotated out of the trace; its own toString prints it in full. */
  record Secret(String label, @NotTraced String secret) {}

  /** Innocent by every name-based test there is — until it interpolates the holder. */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  static final class Session {
    private final String id;
    private final Secret holder;

    Session(String id, Secret holder) {
      this.id = id;
      this.holder = holder;
    }

    @Override
    public String toString() {
      return "Session{id=" + id + ", holder=" + holder + "}";
    }
  }

  @Test
  void anOuterCuratedToStringCannotPrintANestedAnnotatedValue() {
    var rendered = renderer.render(new Session("s-7", new Secret("visible", "s3cr3t")));

    assertThat(rendered).contains("[REDACTED]").contains("visible").doesNotContain("s3cr3t");
    assertThat(
            String.valueOf(
                renderer.renderStructured(new Session("s-7", new Secret("v", "s3"))))) // NOPMD
        .doesNotContain("s3");
  }

  @Test
  void aCompositeMapKeyIsWalkedRatherThanStringified() {
    var map = new java.util.LinkedHashMap<Object, Object>();
    map.put(new Login("ada", "hunter2"), "visible-value");

    assertThat(renderer.render(map)).contains("[REDACTED]").doesNotContain("hunter2");
    assertThat(String.valueOf(renderer.renderStructured(map))).doesNotContain("hunter2");
  }

  // ------------------------------------------------------------------ what still keeps its text

  /** A class with no instance fields: nothing to hide, nothing to walk. */
  static final class Stateless {
    @Override
    public String toString() {
      return "always the same";
    }
  }

  @Test
  void aClassWithNoInstanceFieldsKeepsItsOwnText() {
    assertThat(renderer.render(new Stateless())).isEqualTo("always the same");
    assertThat(renderer.renderStructured(new Stateless()))
        .isEqualTo(new RenderedValue.StringVal("always the same"));
  }

  /**
   * A platform type carries fields but its {@code toString()} is the JDK's, not the application's —
   * and its fields live in a module this one cannot open, so walking it would answer a row of
   * {@code <error: InaccessibleObjectException>} where the JDK answers a date. Pinned because the
   * carve-out is the one place the bare "has fields" rule is not applied, and a future edit that
   * dropped it would degrade every trace carrying a date, a UUID or a duration.
   */
  @Test
  void aPlatformTypeKeepsItsOwnTextBecauseItsFormatIsTheJdkNotTheApplication() {
    assertThat(renderer.render(LocalDate.of(2026, 9, 11))).isEqualTo("2026-09-11");
    assertThat(renderer.render(Duration.ofMinutes(90))).isEqualTo("PT1H30M");
    assertThat(renderer.render(UUID.fromString("00000000-0000-0000-0000-00000000002a")))
        .isEqualTo("00000000-0000-0000-0000-00000000002a");
  }

  /** A subclass an application declares is application code, whatever it extends. */
  static final class ApplicationDate extends java.util.Date {
    private static final long serialVersionUID = 1L;

    private final String password;

    ApplicationDate(String password) {
      super(0L);
      this.password = password;
    }

    @Override
    public String toString() {
      return "ApplicationDate{password=" + password + "}";
    }
  }

  @Test
  void aSubclassOfAPlatformTypeIsNotAPlatformType() {
    assertThat(renderer.render(new ApplicationDate("hunter2"))).doesNotContain("hunter2");
  }

  // ------------------------------------------------------------------ the one opt-in, scanned

  /** The supported way to choose the bytes: a method written for the trace. */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by the summary method
  static final class Priced {
    private final String currency;
    private final String password = "hunter2";

    Priced(String currency) {
      this.currency = currency;
    }

    @NarrativeSummary
    public String describe() {
      return currency + " 10.00";
    }
  }

  @Test
  void aNarrativeSummaryStillWinsOverIntrospection() {
    assertThat(renderer.render(new Priced("EUR"))).isEqualTo("EUR 10.00");
    assertThat(renderer.renderStructured(new Priced("EUR")))
        .isEqualTo(new RenderedValue.StringVal("EUR 10.00"));
  }

  /** A summary that interpolates a credential is a mistake, not a decision. */
  static final class LeakySummary {
    @NarrativeSummary
    public String describe() {
      return JWT;
    }
  }

  @Test
  void aSummaryWhoseTextIsCredentialShapedIsWithheldByTheValueAxis() {
    assertThat(renderer.render(new LeakySummary())).isEqualTo(RedactionPolicy.MARKER);
    assertThat(renderer.renderStructured(new LeakySummary()))
        .isEqualTo(new RenderedValue.StringVal(RedactionPolicy.MARKER));
  }

  /** A field-less leaf's own text is scanned on the same line, for the same reason. */
  static final class LeakyToString {
    @Override
    public String toString() {
      return JWT;
    }
  }

  @Test
  void aLeafWhoseTextIsCredentialShapedIsWithheldToo() {
    assertThat(renderer.render(new LeakyToString())).isEqualTo(RedactionPolicy.MARKER);
  }

  /** A summary carrying a control character forges a log line unless it is escaped. */
  static final class ForgingSummary {
    @NarrativeSummary
    public String describe() {
      return "ok" + (char) 0x000a + "## forged heading";
    }
  }

  @Test
  void aSummaryIsEscapedLikeAnyOtherText() {
    assertThat(renderer.render(new ForgingSummary())).doesNotContain("\n").contains("forged");
  }

  // ------------------------------------------------------------------ the typed failure marker

  /** A summary that throws, with the failing value in the exception message. */
  static final class ThrowingSummary {
    @NarrativeSummary
    public String describe() {
      throw new IllegalStateException("cannot format hunter2");
    }
  }

  @Test
  void aThrowingSummaryNamesTheExceptionTypeAndNeverItsMessage() {
    assertThat(renderer.render(new ThrowingSummary()))
        .isEqualTo("<error: IllegalStateException>")
        .doesNotContain("hunter2");
    assertThat(renderer.renderStructured(new ThrowingSummary()))
        .isEqualTo(new RenderedValue.StringVal("<error: IllegalStateException>"));
  }

  /** A leaf whose own text throws, with the failing value in the exception message. */
  static final class ThrowingLeaf {
    @Override
    public String toString() {
      throw new IllegalArgumentException("cannot render hunter2");
    }
  }

  @Test
  void aThrowingLeafNamesTheExceptionTypeAndNeverItsMessage() {
    assertThat(renderer.render(new ThrowingLeaf()))
        .isEqualTo("<error: IllegalArgumentException>")
        .doesNotContain("hunter2");
  }

  /** A getter-backed field whose read throws an {@link Error}, not an exception. */
  static final class ErrorField {
    @SuppressWarnings("PMD.UnusedPrivateField") // read reflectively
    private final Object value =
        new Object() {
          @Override
          public String toString() {
            throw new StackOverflowError();
          }
        };
  }

  @Test
  void theMarkerNamesAnErrorAsReadilyAsAnException() {
    assertThat(renderer.render(new ErrorField())).contains("<error: StackOverflowError>");
  }

  // ------------------------------------------------------------------ the guards toString escaped

  /** A node whose {@code toString} recursed forever; the walk's depth cap now stands in front. */
  static final class Chain {
    private final Chain next;

    Chain(Chain next) {
      this.next = next;
    }

    @Override
    public String toString() {
      return "Chain{next=" + next + "}";
    }
  }

  @Test
  void theDepthCapNowStandsInFrontOfAValueThatStringifiesItself() {
    Chain chain = new Chain(null);
    for (var i = 0; i < 10_000; i++) {
      chain = new Chain(chain);
    }
    final var deep = chain;

    assertThatCode(() -> renderer.render(deep)).doesNotThrowAnyException();
    assertThat(renderer.render(deep)).contains("<max-depth>");
  }

  /** A pair that holds each other; user stringification ran outside the identity guard. */
  @SuppressWarnings("PMD.UnusedPrivateField") // read reflectively
  static final class Node {
    private Node peer;

    @Override
    public String toString() {
      return "Node{peer=" + peer + "}";
    }
  }

  @Test
  void theCycleGuardNowStandsInFrontOfItToo() {
    var left = new Node();
    var right = new Node();
    left.peer = right;
    right.peer = left;

    assertThatCode(() -> renderer.render(left)).doesNotThrowAnyException();
    assertThat(renderer.render(left)).contains("<Node@");
  }
}
