/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One piece of diagram text guaranteed to have passed through {@link DiagramText}, the one
 * sanitizer both sequence grammars share.
 *
 * <p>INTENT: "call me before a string enters a diagram line" used to be a convention every call
 * site had to remember on its own — {@link DiagramText}'s functions are plain {@code String}
 * transforms that nothing stopped a caller from skipping. Wrapping the sanitized text in a type
 * makes the convention structural: a {@link SequenceGrammar} hook that declares a {@code
 * DiagramLabel} parameter cannot be called with a raw trace string, sanitized or not, because
 * nothing outside this class can construct one.
 *
 * <p><b>@llmNote</b> {@link #identifier}, {@link #quotedIdentifier}, {@link #message} and {@link
 * #alias} are the only routes from untrusted trace metadata into a label — each delegates to {@link
 * DiagramText}'s existing sanitizer, unchanged. Every other method here ({@link #withParameters},
 * {@link #aliasedAs}) composes labels that are already sanitized, joining their text with literal
 * punctuation that never came from the trace ({@code (}, {@code , }, {@code as}) — so composition
 * can never reopen the hole the sanitizer closed.
 */
final class DiagramLabel {

  private final String text;

  private DiagramLabel(String text) {
    this.text = text;
  }

  /**
   * Sanitizes one piece of trace metadata (class name, method name, parameter name, exception type)
   * for interpolation into either diagram grammar. See {@link DiagramText#identifier}.
   *
   * @param raw the metadata field as captured, which may be anything
   */
  static DiagramLabel identifier(String raw) {
    return new DiagramLabel(DiagramText.identifier(raw));
  }

  /**
   * A sanitized identifier, quoted when it contains a character that would otherwise end an
   * unquoted token ({@code . - : < >} or a space) — the label a participant declaration or an arrow
   * endpoint uses. See {@link DiagramText#quoteIfNeeded}.
   *
   * @param raw the metadata field as captured, which may be anything
   */
  static DiagramLabel quotedIdentifier(String raw) {
    return new DiagramLabel(DiagramText.quoteIfNeeded(raw));
  }

  /**
   * As {@link #quotedIdentifier}, but also quoted when the identifier is itself a bare grammar
   * keyword — the label a PLAIN-mode participant declaration or arrow endpoint uses, in either
   * grammar. Never use this for an alias-mode "as" display name; {@link #quotedIdentifier} is the
   * right call there. See {@link DiagramText#plainModeToken}.
   *
   * @param raw the metadata field as captured, which may be anything
   */
  static DiagramLabel plainToken(String raw) {
    return new DiagramLabel(DiagramText.plainModeToken(raw));
  }

  /**
   * The message text for a return arrow — a checkmark for void, a count-plus-noun summary for a
   * collection, or a folded and truncated scalar. See {@link DiagramText#returnMessage}.
   */
  static DiagramLabel message(String renderedValue, RenderedValue structuredValue) {
    return new DiagramLabel(DiagramText.returnMessage(renderedValue, structuredValue));
  }

  /** A bare, unquotable Mermaid participant alias. See {@link DiagramText#aliasToken}. */
  static DiagramLabel alias(String raw) {
    return new DiagramLabel(DiagramText.aliasToken(raw));
  }

  /**
   * This label (a sanitized method name) followed by its already-sanitized parameter names,
   * comma-joined and parenthesized: {@code method(paramA, paramB)}. The parentheses and separator
   * are literal, not trace-derived, so this cannot reintroduce anything the sanitizer folded.
   */
  DiagramLabel withParameters(List<DiagramLabel> parameters) {
    var joined = parameters.stream().map(DiagramLabel::text).collect(Collectors.joining(", "));
    return new DiagramLabel(text + "(" + joined + ")");
  }

  /**
   * This label (a Mermaid alias) followed by the display name it stands for: {@code X as Name} —
   * the participant declaration line Mermaid's alias mode emits.
   */
  DiagramLabel aliasedAs(DiagramLabel displayName) {
    return new DiagramLabel(text + " as " + displayName.text);
  }

  /** The sanitized text, safe to append directly to a diagram line. */
  String text() {
    return text;
  }
}
