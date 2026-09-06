/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * The one reading of {@code Throwable.getMessage()} every emitter shares.
 *
 * <p>INTENT: {@code TraceOutcome.Threw} holds the live {@link Throwable}, unlike {@code Returned},
 * which holds text {@code ValueRenderer} already rendered. A message therefore never passed the
 * redaction policy at all: {@code IllegalArgumentException("bad token: eyJhbGciOi…")} reached every
 * output in full, while the identical string as a parameter answered {@code [REDACTED]}. That
 * falsifies {@link SecretValueShapes}' own stated purpose — the value axis exists because a bearer
 * token arrives with no name, and an exception message is exactly such a place. One accessor here,
 * called by every renderer and exporter, is what keeps them from drifting: the previous arrangement
 * had four different spellings of "read the message" across the emitters, three of which escaped it
 * and one of which did not.
 *
 * <p><b>@llmNote</b> Which axis applies is a product decision, and it is the <em>value-shape</em>
 * axis only. A JWT, a Luhn-valid PAN or a {@code Set-Cookie} string inside a message is never
 * legitimate and the matcher is narrow enough to say so without a name. The <em>name</em> deny-list
 * has nothing to match on here — blanking whole messages by keyword would destroy the diagnostic
 * value the message exists for — and neither does {@code @NotTraced}: no library can follow a value
 * into {@code "bad token: " + token}, and claiming otherwise would be an overclaim.
 *
 * <p><b>@llmNote</b> Length is deliberately <em>not</em> capped, unlike a rendered value. A message
 * is the one piece of an exceptional trace a reader acts on, and truncating it costs exactly the
 * detail they came for. Boundedness of the emitters is already asserted over the whole artifact.
 *
 * <p><b>@sideEffects</b> {@code getMessage()} is application code, run once per emitter on whatever
 * thread renders — a message builder that recurses used to raise {@code StackOverflowError} inside
 * a renderer. The read is guarded here, so a message that cannot be produced degrades to a marker
 * instead of failing the emitter.
 */
public final class ExceptionMessage {

  /** What an emitter says when the message could not be read at all. */
  private static final String UNREADABLE = "<error>";

  private ExceptionMessage() {}

  /**
   * The message an emitter may print: redacted when its bytes are a credential, control-escaped
   * always, and {@code null} when the exception carries none.
   *
   * <p><b>@edgeCase</b> {@code null} is preserved rather than turned into text. The canonical
   * exporters distinguish "no message" from the four characters {@code null} — a JSON field that is
   * absent says something a field holding {@code "null"} does not — so the renderers that do want
   * the literal keep their own {@code String.valueOf}.
   *
   * @param exception the captured throwable; {@code null} answers {@code null}
   * @return the text every emitter must print in place of the raw message
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a rogue getMessage() may throw Error
  public static String of(Throwable exception) {
    if (exception == null) {
      return null;
    }
    String raw;
    try {
      raw = exception.getMessage();
    } catch (Throwable t) { // NOPMD
      return UNREADABLE;
    }
    if (raw == null) {
      return null;
    }
    return RedactionPolicy.DEFAULT.shouldRedactValue(raw)
        ? RedactionPolicy.MARKER
        : ControlEscape.sanitize(raw);
  }

  /**
   * The same message where a caller needs text rather than {@code null}: the literal {@code null}
   * every line-oriented renderer has always printed for a message-less exception.
   *
   * @param exception the captured throwable
   * @return {@link #of} with {@code null} spelled as {@code "null"}
   */
  public static String text(Throwable exception) {
    return String.valueOf(of(exception));
  }
}
