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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
 * <p>Tightened 2026-09-19 to the form every runtime now shares: the trust is an EXPLICIT list of
 * platform leaf types, keyed on the exact class. "Declares no instance field" was the second rule
 * and was escaped in turn — a fieldless class can hold its state in a static identity-keyed side
 * table, a {@code ClassValue} or a {@code ThreadLocal}, all invisible to a field walk and all
 * readable from inside its own {@code toString()}.
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

  /** A class with no instance fields — which is not the same thing as a class with no state. */
  static final class Stateless {
    @Override
    public String toString() {
      return "always the same";
    }
  }

  /**
   * The 2026-09-19 correction, from the cross-runtime finding that a value presenting as FIELDLESS
   * to reflection can print state reflection cannot see: "declares no instance field" was never a
   * statement that a value has nothing to tell, so it buys no trust at all. A fieldless user class
   * renders as the object it is, naming its type, its own {@code toString()} never entered.
   */
  @Test
  void aClassWithNoInstanceFieldsIsStillNotTrustedWithItsOwnText() {
    assertThat(renderer.render(new Stateless())).isEqualTo("Stateless{}");
    assertThat(renderer.renderStructured(new Stateless()))
        .isEqualTo(new RenderedValue.ObjectVal("Stateless", Map.of()));
  }

  /**
   * The shape the correction was ruled for: a FIELDLESS class holding its real state in a static
   * identity-keyed SIDE TABLE and reading it back inside its own {@code toString()}. Reflection
   * finds nothing to walk, so the old "no field, nothing to hide" test handed the class its own
   * text — and that text prints state no field name exists for, so neither the deny-list nor
   * {@code @NotTraced} can reach it. A {@code ClassValue} or a {@code ThreadLocal} is the same
   * door; the corpus row {@code fieldless-sidetable-tostring-door} pins it for every runtime.
   */
  static final class SideTableState {
    private static final Map<Object, String> STATE =
        Collections.synchronizedMap(new IdentityHashMap<>());

    SideTableState(String hidden) {
      STATE.put(this, hidden);
    }

    @Override
    public String toString() {
      return "SideTableState[" + STATE.get(this) + "]";
    }
  }

  /** The same door through a {@link ThreadLocal} rather than a table keyed by the instance. */
  static final class ThreadLocalState {
    private static final ThreadLocal<String> STATE = new ThreadLocal<>();

    ThreadLocalState(String hidden) {
      STATE.set(hidden);
    }

    @Override
    public String toString() {
      return "ThreadLocalState[" + STATE.get() + "]";
    }
  }

  @Test
  void stateHeldOffTheFieldGraphIsNeverPrintedThroughAFieldlessClassOwnText() {
    var sideTable = new SideTableState("hunter2");
    var threadLocal = new ThreadLocalState("hunter2");

    assertThat(renderer.render(sideTable)).isEqualTo("SideTableState{}");
    assertThat(renderer.renderStructured(sideTable))
        .isEqualTo(new RenderedValue.ObjectVal("SideTableState", Map.of()));
    assertThat(renderer.renderForCapture(sideTable).rendered()).doesNotContain("hunter2");

    assertThat(renderer.render(threadLocal)).isEqualTo("ThreadLocalState{}");
    assertThat(renderer.renderStructured(threadLocal))
        .isEqualTo(new RenderedValue.ObjectVal("ThreadLocalState", Map.of()));
    assertThat(renderer.renderForCapture(threadLocal).rendered()).doesNotContain("hunter2");
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

  /**
   * The leaf list also names three FAMILIES — {@code Path}, {@code ZoneId}, {@code Charset} — whose
   * every implementation is a JDK-internal class no list could name by hand ({@code
   * sun.nio.fs.UnixPath}, {@code java.time.ZoneRegion}, {@code sun.nio.cs.UTF_8}). Trust still
   * turns on the exact class's ORIGIN: the family is consulted only after the runtime class itself
   * proves platform-defined.
   */
  @Test
  void aPlatformLeafFamilyKeepsItsOwnTextThroughItsInternalImplementationClass() {
    assertThat(renderer.render(Path.of("tmp/narrativetrace"))).isEqualTo("tmp/narrativetrace");
    assertThat(renderer.render(ZoneId.of("Europe/Madrid"))).isEqualTo("Europe/Madrid");
    assertThat(renderer.render(StandardCharsets.UTF_8)).isEqualTo("UTF-8");
  }

  /** A FIELDLESS application subclass of a family member: application code, so not a leaf. */
  static final class ApplicationCharset extends Charset {
    ApplicationCharset() {
      super("x-application-charset", null);
    }

    @Override
    public boolean contains(Charset other) {
      return false;
    }

    @Override
    public CharsetDecoder newDecoder() {
      throw new UnsupportedOperationException("never called by rendering");
    }

    @Override
    public CharsetEncoder newEncoder() {
      throw new UnsupportedOperationException("never called by rendering");
    }
  }

  /**
   * {@code Charset} declares its own {@code toString()} {@code final}, so this subclass's text
   * would be the JDK's — and it is refused all the same, because trust is decided by the exact
   * class's origin and never by what it extends. The next application subclass of a family member
   * will not be so harmless.
   */
  @Test
  void anApplicationSubclassOfALeafFamilyIsNotALeaf() {
    assertThat(renderer.render(new ApplicationCharset()))
        .isEqualTo("ApplicationCharset{}")
        .doesNotContain("x-application-charset");
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

  /**
   * A platform leaf's own text is scanned on the same line, for the same reason — pinned on a
   * {@link Pattern}, whose text is the pattern it holds, because a platform leaf is now the only
   * type whose own text rendering reads at all.
   */
  @Test
  void aPlatformLeafWhoseTextIsCredentialShapedIsWithheldToo() {
    assertThat(renderer.render(Pattern.compile(JWT))).isEqualTo(RedactionPolicy.MARKER);
    assertThat(renderer.renderStructured(Pattern.compile(JWT)))
        .isEqualTo(new RenderedValue.StringVal(RedactionPolicy.MARKER));
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

  /** A fieldless class whose own text throws, with the failing value in the exception message. */
  static final class ThrowingLeaf {
    @Override
    public String toString() {
      throw new IllegalArgumentException("cannot render hunter2");
    }
  }

  /**
   * A fieldless class is not a leaf, so its {@code toString()} is never entered and cannot even
   * fail — no marker, and certainly no message. The typed marker itself stays pinned on the members
   * rendering does run: the summary hook above, and the field read below.
   */
  @Test
  void aThrowingToStringIsNeverEnteredSoItCannotEvenFail() {
    assertThat(renderer.render(new ThrowingLeaf()))
        .isEqualTo("ThrowingLeaf{}")
        .doesNotContain("hunter2");
  }

  /** A field whose own rendered member throws an {@link Error}, not an exception. */
  static final class ErrorField {
    @SuppressWarnings("PMD.UnusedPrivateField") // read reflectively
    private final Object value = new SummaryRaisingAnError();
  }

  /** The summary hook is the member a field's value can still fail inside. */
  static final class SummaryRaisingAnError {
    @NarrativeSummary
    public String describe() {
      throw new StackOverflowError();
    }
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
