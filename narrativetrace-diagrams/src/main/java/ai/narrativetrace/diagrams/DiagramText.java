/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sanitizers for text interpolated into sequence-diagram grammars.
 *
 * <p>INTENT: Both the Mermaid and PlantUML renderers embed rendered trace values (which {@code
 * ValueRenderer} preserves verbatim, newlines included) into single-line message text. This helper
 * is the one place that neutralizes characters capable of breaking out of that line.
 *
 * <p><b>@llmNote</b> Diagram grammars are line-oriented: a raw {@code \n} in a message terminates
 * the current statement and starts a new one the engine parses as a directive — Mermaid {@code
 * click} interactions, PlantUML {@code !include}/{@code !includeurl} preprocessor directives (SSRF
 * / local-file read). Folding every ISO control character to a space is toolchain-independent and
 * keeps the whole rendered value on one physical line.
 */
final class DiagramText {

  private DiagramText() {}

  /**
   * Length above which a scalar return value is truncated with an ellipsis. A sequence-diagram
   * message must stay on one short line to keep the diagram renderable; a full payload (the dogfood
   * 1000-char {@code expensesOf} return) makes the {@code .mmd}/{@code .puml} unusable.
   */
  static final int MAX_SCALAR_LENGTH = 60;

  /**
   * Word boundary inside an identifier: a lowercase letter or digit followed by an uppercase one.
   *
   * <p>Deliberately not "before every capital" — that turns {@code HTTPRequest} into {@code "h t t
   * p request"}. An acronym stays one word, which reads worse than a hand-written noun and far
   * better than the alternative.
   */
  private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

  /**
   * Message text for a return arrow, sized for a sequence-diagram line. Void completions ({@code
   * null} rendered value) render as a check mark; collections summarize to {@code "<count> <noun>"}
   * (e.g. {@code "3 transfers"}) from the structured value; other scalars fold control characters
   * and truncate past {@link #MAX_SCALAR_LENGTH}.
   *
   * @param renderedValue the flat rendered return value, or {@code null} for void
   * @param structuredValue the type-preserving companion, or {@code null} when not captured
   */
  static String returnMessage(String renderedValue, RenderedValue structuredValue) {
    if (renderedValue == null) {
      return "\u2713";
    }
    if (structuredValue instanceof RenderedValue.ListVal list) {
      return summarizeCollection(list);
    }
    return truncate(message(renderedValue));
  }

  private static String summarizeCollection(RenderedValue.ListVal list) {
    int count = list.elements().size();
    return count + " " + pluralize(elementNoun(list), count);
  }

  /**
   * Element noun for a collection: the simple type name as a lowercase phrase when every element is
   * an object of the same type, otherwise the generic {@code "item"}. Empty and mixed collections
   * cannot name a type, so they read as {@code "items"}.
   *
   * <p><b>@llmNote</b> The phrase form is the repo's own identifier-to-prose convention (the same
   * split the narrative renderer applies to method names). Lowercasing the whole type name instead
   * produced {@code "3 temperaturereadings"} — a dogfood finding that scans as a missing space,
   * though the space was between the count and the noun all along.
   */
  private static String elementNoun(RenderedValue.ListVal list) {
    String typeName = null;
    for (var element : list.elements()) {
      if (!(element instanceof RenderedValue.ObjectVal obj)) {
        return "item";
      }
      if (typeName == null) {
        typeName = obj.typeName();
      } else if (!typeName.equals(obj.typeName())) {
        return "item";
      }
    }
    return typeName == null ? "item" : toPhrase(typeName);
  }

  /** {@code TemperatureReading} to {@code "temperature reading"}. */
  private static String toPhrase(String typeName) {
    return String.join(" ", WORD_BOUNDARY.split(typeName)).toLowerCase(Locale.ROOT);
  }

  /**
   * Minimal English pluralization for element nouns: singular when {@code count == 1}; otherwise
   * {@code -s}, with the common {@code -es} (after s/x/z/ch/sh) and consonant-{@code y \u2192 -ies}
   * exceptions. Only the last word of a phrase inflects — "shipment categories", not "shipments
   * categories".
   */
  private static String pluralize(String noun, int count) {
    if (count == 1) {
      return noun;
    }
    var lastSpace = noun.lastIndexOf(' ');
    if (lastSpace < 0) {
      return inflect(noun);
    }
    return noun.substring(0, lastSpace + 1) + inflect(noun.substring(lastSpace + 1));
  }

  /** The spelling rules themselves, applied to one word. */
  private static String inflect(String word) {
    if (word.endsWith("s")
        || word.endsWith("x")
        || word.endsWith("z")
        || word.endsWith("ch")
        || word.endsWith("sh")) {
      return word + "es";
    }
    if (word.length() > 1 && word.endsWith("y") && !isVowel(word.charAt(word.length() - 2))) {
      return word.substring(0, word.length() - 1) + "ies";
    }
    return word + "s";
  }

  private static boolean isVowel(char c) {
    return "aeiou".indexOf(c) >= 0;
  }

  private static String truncate(String text) {
    return text.length() > MAX_SCALAR_LENGTH
        ? text.substring(0, MAX_SCALAR_LENGTH) + "\u2026"
        : text;
  }

  /**
   * Length above which interpolated <em>metadata</em> is truncated. Long enough for any real class
   * or method name, short enough that a hostile one cannot make the diagram unreadable on its own.
   */
  static final int MAX_IDENTIFIER_LENGTH = 120;

  /**
   * Stands in for metadata that is empty, or that was nothing but control characters.
   *
   * <p><b>@edgeCase</b> An empty class name emitted the bare line {@code participant }, which is
   * not a Mermaid statement at all — the diagram was malformed rather than merely ugly. Found by
   * the metadata fuzz route added for the 2026-09-02 audit's finding 4, which is the first thing
   * ever to put an empty string in that field. Marker-shaped like the renderer's {@code <empty>}
   * and {@code <pending>}: a reader learns the name was absent, rather than seeing a gap.
   */
  private static final String UNNAMED = "<unnamed>";

  /**
   * Neutralises a piece of trace metadata — class name, method name, parameter name, exception type
   * — for interpolation into either diagram grammar.
   *
   * <p>INTENT: {@code MethodSignature} is a public record with unvalidated fields, and trace trees
   * arrive from deserialization and from integrations as well as from the proxy. Before this
   * existed, the renderers passed those fields through {@code quoteIfNeeded}, which wrapped a name
   * in quotes when it contained {@code . - :} or a space and escaped nothing at all — so a class
   * name carrying {@code "} and a newline closed the quoted participant and opened a statement of
   * the attacker's choosing. The audit's probe produced a forged participant, a Mermaid {@code
   * click} interaction pointing at an external URL, and a forged PlantUML note.
   *
   * <p><b>@llmNote</b> Deliberately one sanitizer for two grammars, applying the union of their
   * hazards rather than a Mermaid version and a PlantUML version. Both are line-oriented and both
   * delimit names with {@code "}; two implementations of "what is safe here" would drift, and a
   * drifted escaper is an injection on whichever side fell behind. This is the same reasoning that
   * keeps one redaction rule for the renderer and the template resolver.
   *
   * <ul>
   *   <li>Every ISO control folds to a space — the newline is what turns interpolated text into a
   *       new statement, and it is also how PlantUML {@code !include} / {@code !includeurl}
   *       preprocessor directives (local-file read, SSRF) would be reached.
   *   <li>{@code "} becomes {@code '} — neither grammar offers an escape for a quote inside a
   *       quoted name, so the only safe move is for the character not to be a quote. Replacing it
   *       rather than dropping it keeps the name honest about having contained one.
   *   <li>{@code %%} becomes {@code %} — {@code %%} starts a Mermaid comment, which would swallow
   *       the rest of a line the renderer meant to emit.
   *   <li>The result is capped, with an ellipsis, at {@link #MAX_IDENTIFIER_LENGTH}.
   * </ul>
   *
   * @param raw the metadata field as captured, which may be anything
   * @return one line of text containing no quote, no control character and no comment opener
   */
  static String identifier(String raw) {
    if (raw == null) {
      return "";
    }
    var sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isISOControl(c)) {
        sb.append(' ');
      } else if (c == '"') {
        sb.append('\'');
      } else if (c == '%' && sb.length() > 0 && sb.charAt(sb.length() - 1) == '%') {
        continue;
      } else {
        sb.append(c);
      }
    }
    var folded = sb.toString();
    if (folded.isBlank()) {
      return UNNAMED;
    }
    return folded.length() > MAX_IDENTIFIER_LENGTH
        ? folded.substring(0, MAX_IDENTIFIER_LENGTH) + "\u2026"
        : folded;
  }

  /**
   * A Mermaid participant alias: a single bare token, so it is safe unquoted in message lines.
   *
   * <p><b>@edgeCase</b> The alias is emitted both in {@code participant X as Name} and, unquoted,
   * on every arrow. {@link #identifier} is not enough there — it can still contain a space, which
   * would split the arrow into two tokens — so this reduces to {@code [A-Za-z0-9_]} and falls back
   * to {@code P} when nothing survives. A class name with no uppercase letters keeps its historical
   * alias: {@code scheduler} stays {@code scheduler}.
   *
   * @param raw the candidate alias
   * @return a non-empty token safe to emit unquoted
   */
  static String aliasToken(String raw) {
    if (raw == null) {
      return "P";
    }
    var sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length() && sb.length() < MAX_IDENTIFIER_LENGTH; i++) {
      char c = raw.charAt(i);
      if (c == '_' || Character.isLetterOrDigit(c)) {
        sb.append(c);
      }
    }
    return sb.length() == 0 ? "P" : sb.toString();
  }

  /**
   * Folds ISO control characters (notably CR/LF) in interpolated message text to single spaces so a
   * rendered value cannot inject a new diagram line.
   *
   * @param text the raw message text (e.g. a rendered return value)
   * @return the text with every control character replaced by a space
   */
  static String message(String text) {
    var sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      sb.append(Character.isISOControl(c) ? ' ' : c);
    }
    return sb.toString();
  }
}
