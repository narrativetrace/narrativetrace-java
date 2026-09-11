/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Objects whose {@code toString}, {@code hashCode}, {@code equals} or accessors misbehave — the
 * third-party DTOs a tracing library has no control over.
 *
 * <p>INTENT: These are the shapes the {@code graphs.json} {@code hostileMember} kind names. Each
 * holds a {@link HostileGraphs.Secret}, so the redaction oracle applies to all of them: a renderer
 * that falls back to {@code toString} because introspection failed must not print past the
 * redaction, and an exception raised inside instrumentation must not carry the value out in its
 * message.
 *
 * <p><b>@llmNote</b> {@link Blocking} sleeps for a bounded time rather than forever. A test that
 * hangs reports nothing; a test that takes 250 ms and blows a 5 s budget reports which input did
 * it.
 */
public final class HostileMembers {

  /** How long {@link Blocking#toString()} stalls the renderer. */
  public static final long BLOCK_MILLIS = 250;

  /** U+200B, a format character {@code Character.isJavaIdentifierPart} and no deny-list accepts. */
  public static final char ZERO_WIDTH_SPACE = 0x200b;

  /** U+0440, the Cyrillic letter that renders identically to a Latin {@code p}. */
  public static final char CYRILLIC_ER = 0x0440;

  /** Size of the string {@link Huge#toString()} returns. */
  public static final int HUGE_LENGTH = 1024 * 1024;

  private HostileMembers() {}

  /** A {@code toString} that throws — the commonest hostile DTO. */
  public record Throwing(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      throw new IllegalStateException("toString refuses");
    }
  }

  /** A {@code toString} whose exception message carries the secret out with it. */
  public record ThrowingWithPayload(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      throw new IllegalStateException("cannot render " + held.secret());
    }
  }

  /** A {@code toString} that recurses until the stack ends. */
  public record Recursing(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return "recursing " + this;
    }
  }

  /** A {@code toString} that blocks, bounded, so the time budget is what fails. */
  public record Blocking(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      try {
        Thread.sleep(BLOCK_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "eventually";
    }
  }

  /** A {@code toString} returning a mebibyte — the bounded-output case. */
  public record Huge(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return "z".repeat(HUGE_LENGTH);
    }
  }

  /** A {@code toString} returning {@code null}, which the JDK contract forbids. */
  public record NullReturning(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return null;
    }
  }

  /**
   * A {@code Number} subclass whose {@code toString()} is a forged narrative line, not a number —
   * the shape {@code ValueRenderer.renderScalar} used to trust unsanitized because {@code Number}
   * is a fast-pathed scalar type. Carries a newline (log/Markdown-line forgery), a Markdown fence,
   * JSON object structure and the two non-JSON floating-point spellings, all in one payload so a
   * single case exercises every renderer's scalar-numeric path at once.
   *
   * <p><b>@llmNote</b> Takes a {@code held} constructor argument purely to match every other {@code
   * hostileMember} shape's factory signature in {@link HostileGraphs#hostile}; it is never read.
   * This shape is a scalar (a {@code Number}), so it is never introspected field-by-field the way
   * {@link Throwing} and its siblings are — the redaction oracle does not apply to it, only the
   * structure-forging one does.
   */
  @SuppressWarnings({
    "PMD.UnusedPrivateField", // kept only to match the uniform hostile() factory
    "PMD.NonSerializableClass" // extends Number, which is Serializable; never actually serialized
  })
  public static final class NumberHostileToString extends Number {
    private final HostileGraphs.Secret held;

    NumberHostileToString(HostileGraphs.Secret held) {
      this.held = held;
    }

    @Override
    public String toString() {
      return "1\n```\n{\"outcome\": \"success\"}\nNaN Infinity -Infinity\n```";
    }

    @Override
    public int intValue() {
      return 1;
    }

    @Override
    public long longValue() {
      return 1L;
    }

    @Override
    public float floatValue() {
      return 1f;
    }

    @Override
    public double doubleValue() {
      return 1d;
    }
  }

  /** A {@code hashCode} that throws — breaks any identity or hash set the renderer keeps. */
  public record HashThrowing(HostileGraphs.Secret held) {
    @Override
    public int hashCode() {
      throw new IllegalStateException("hashCode refuses");
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }
  }

  /** An {@code equals} that throws. */
  public record EqualsThrowing(HostileGraphs.Secret held) {
    @Override
    public boolean equals(Object other) {
      throw new IllegalStateException("equals refuses");
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  /** A record component accessor that throws while the renderer is reading components. */
  public record AccessorThrowing(HostileGraphs.Secret held, String label) {
    @Override
    public String label() {
      throw new IllegalStateException("accessor refuses");
    }
  }

  /** A JavaBean whose getter throws while the renderer is introspecting it. */
  public static final class GetterThrowing {

    private final HostileGraphs.Secret held;

    GetterThrowing(HostileGraphs.Secret held) {
      this.held = held;
    }

    /** Never returns; the renderer must degrade rather than propagate. */
    public String getDetail() {
      throw new IllegalStateException("getter refuses");
    }

    /** Reachable, and behind {@code @NotTraced} at the next level down. */
    public HostileGraphs.Secret getHeld() {
      return held;
    }
  }

  /**
   * Hostile *names*, where names actually come from data: map keys.
   *
   * <p>The deny-list matches on the rendered key text, so a key carrying an invisible code point
   * (U+200B inside the word "password") reads as an ordinary key to a human and matches nothing.
   * The secret here is protected by {@code @NotTraced} one level down, not by its name — which is
   * the point. Redaction that depended on a name being readable would be no redaction at all.
   */
  public static Map<String, Object> hostileKeyNames(HostileGraphs.Secret held) {
    var map = new LinkedHashMap<String, Object>();
    map.put("pass" + ZERO_WIDTH_SPACE + "word", "visible-but-unmatched");
    map.put("PASSWORD", "upper-case");
    map.put("pass_word", "separated");
    map.put(CYRILLIC_ER + "assword", "cyrillic-lookalike");
    map.put("held", held);
    return map;
  }

  /** More fields than the renderer's five-field cap, with the secret beyond it. */
  @SuppressWarnings("PMD.UnusedPrivateField") // every field exists to be introspected
  public static final class ManyFields {

    private final String a = "1";
    private final String b = "2";
    private final String c = "3";
    private final String d = "4";
    private final String e = "5";
    private final String f = "6";
    private final String g = "7";
    private final String h = "8";
    private final String i = "9";
    private final String j = "10";
    private final String k = "11";
    private final HostileGraphs.Secret secret;

    ManyFields(HostileGraphs.Secret held) {
      this.secret = held;
    }
  }

  /**
   * A plain class carrying a deny-listed field and a hand-written {@code toString} that prints it —
   * the shape the 2026-09-11 five-runtime investigation found leaking at depth zero.
   *
   * <p>INTENT: No nesting, no annotation, no wrapper. Just a class an application author wrote a
   * pleasant {@code toString} for years before anyone traced it. Every runtime except .NET trusted
   * that {@code toString} once the type declared one, and the trust check never consulted the name
   * deny-list, so {@code password=hunter2} reached every output.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class CuratedToString {

    private final String username = "ada";
    private final String password;

    CuratedToString(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Login{username=" + username + ", password=" + password + "}";
    }
  }

  /**
   * A class with no sensitive field of its own whose hand-written {@code toString} interpolates a
   * nested holder that has one.
   *
   * <p><b>@llmNote</b> The held record's own generated {@code toString} prints its
   * {@code @NotTraced} component in full — which is exactly why the outer curated {@code toString}
   * is the leak: one interpolation carries a value the annotation promised no output would show.
   * The outer class is innocent by every name-based test there is.
   *
   * <p><b>@edgeCase</b> A plain class, deliberately not a record. Java dispatches a record to its
   * component walk <em>before</em> it ever asks whether the type stringifies itself, so a record
   * realisation of this shape would exercise the record branch and prove nothing about native
   * stringification — the thing the row exists for.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class CuratedToStringNested {

    private final String sessionId = "session-7";
    private final HostileGraphs.Secret holder;

    CuratedToStringNested(HostileGraphs.Secret holder) {
      this.holder = holder;
    }

    @Override
    public String toString() {
      return "Session{id=" + sessionId + ", holder=" + holder + "}";
    }
  }

  /**
   * A composite carrying a deny-listed field, built to be used as a {@code Map} KEY.
   *
   * <p><b>@llmNote</b> A key has to become text before it can be printed, which is the one place
   * native stringification is hardest to avoid — so this shape carries a curated {@code toString}
   * too. A renderer that reaches for it on the key path leaks there even when the value path is
   * already safe.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class SensitiveKey {

    private final String username = "ada";
    private final String password;

    SensitiveKey(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Key{username=" + username + ", password=" + password + "}";
    }
  }

  /**
   * A holder whose {@code @NarrativeSummary} throws, with the payload in the exception message.
   *
   * <p>INTENT: The summary marker is the ONLY opt-in to curated rendering left, so its failure mode
   * is now part of the contract: the traced call succeeds, the failed part renders the typed marker
   * {@code <error: IllegalStateException>}, and the message — which carries the very value that
   * failed to format — reaches no output.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by the summary method and by introspection
  public static final class ThrowingSummary {

    private final HostileGraphs.Secret held;

    ThrowingSummary(HostileGraphs.Secret held) {
      this.held = held;
    }

    /** Never returns; the renderer must degrade to a typed, value-free marker. */
    @NarrativeSummary
    public String describe() {
      throw new IllegalStateException("cannot summarise " + held.secret());
    }
  }

  /** A ring: every node holds the next, and the last holds the first. */
  @SuppressWarnings("PMD.UnusedPrivateField") // both fields exist to be read reflectively
  public static final class Ring {

    private final HostileGraphs.Secret held;

    private Ring next;

    Ring(HostileGraphs.Secret held) {
      this.held = held;
    }

    void linkTo(Ring node) {
      this.next = node;
    }
  }
}
