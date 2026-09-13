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
import java.util.Set;
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
   * the metadata fuzz route added for an adversarial review, which is the first thing ever to put
   * an empty string in that field. Marker-shaped like the renderer's {@code <empty>} and {@code
   * <pending>}: a reader learns the name was absent, rather than seeing a gap.
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
   * Quotes a participant name, after {@link #identifier} has made it safe to quote.
   *
   * <p><b>@edgeCase</b> Sanitising happens here rather than at each call site so no future caller
   * can reach the quoting without it. The old version wrapped a name in quotes when it contained
   * {@code . - :} or a space and escaped nothing, which made an embedded {@code "} plus a newline a
   * breakout into arbitrary Mermaid statements, a forged PlantUML {@code note over}, or a {@code
   * !include} preprocessor directive.
   *
   * <p><b>@llmNote</b> One implementation for both grammars, for the same reason {@link
   * #identifier} is shared: both delimit names with {@code "} and both are line-oriented, so a
   * Mermaid copy and a PlantUML copy of "when does a name need quoting" would drift, and the
   * drifted one is an injection. It lived as a private twin in each sequence renderer until
   * 2026-09-12.
   *
   * @param name the participant name as captured, which may be anything
   * @return the sanitized name, quoted when it contains a character that would otherwise end the
   *     token
   */
  static String quoteIfNeeded(String name) {
    var safe = identifier(name);
    return needsQuotingForACharacter(safe) ? "\"" + safe + "\"" : safe;
  }

  /**
   * As {@link #quoteIfNeeded}, but ALSO quotes when the whole (sanitized) name is a bare grammar
   * keyword — safe wherever the identifier is used AS the grammar token itself: a PLAIN-mode
   * participant declaration or arrow endpoint, in either grammar.
   *
   * <p><b>@edgeCase</b> Deliberately a separate function from {@link #quoteIfNeeded}, not a
   * universal change to it: Mermaid's alias mode reuses {@link #quoteIfNeeded} for the
   * human-readable display name after {@code as} ({@code participant X as DisplayName}), which is
   * never itself used as a bare token and is documented (see {@code
   * MermaidSequenceDiagramRenderer}'s alias-mode test) to keep a reserved word unescaped there —
   * changing {@link #quoteIfNeeded} itself would have silently re-quoted that display name too, an
   * unrelated behavior change to already-shipped, already-tested output.
   *
   * @param name the participant name as captured, which may be anything
   * @return the sanitized name, quoted when it contains a character that would otherwise end the
   *     token, or when the whole name is a bare grammar keyword
   */
  static String plainModeToken(String name) {
    var safe = identifier(name);
    return needsQuotingForACharacter(safe) || isSequenceDiagramReservedWord(safe)
        ? "\"" + safe + "\""
        : safe;
  }

  private static boolean needsQuotingForACharacter(String safe) {
    return safe.chars()
        .anyMatch(c -> c == '.' || c == '-' || c == ':' || c == ' ' || c == '<' || c == '>');
  }

  /**
   * True when {@code token}, compared case-insensitively as a WHOLE, is a bare keyword either
   * grammar reserves — never merely contains one as a substring ({@code endpoint} is an ordinary
   * name).
   */
  private static boolean isSequenceDiagramReservedWord(String token) {
    var lower = token.toLowerCase(Locale.ROOT);
    return MERMAID_RESERVED_ALIASES.contains(lower) || PLANTUML_RESERVED_WORDS.contains(lower);
  }

  /**
   * Mermaid sequence-diagram keywords a bare alias token must never collide with, matched
   * case-insensitively — the grammar declares {@code %options case-insensitive}.
   *
   * <p><b>@llmNote</b> Sourced from every single-word literal lexer rule in {@code
   * sequenceDiagram.jison} (mermaid-js/mermaid, {@code develop} branch, verified 2026-09-13):
   * {@code "loop" { ...; return 'loop'; }} and its siblings for {@code box}, {@code participant},
   * {@code actor}, {@code create}, {@code destroy}, {@code rect}, {@code opt}, {@code alt}, {@code
   * else}, {@code par}, {@code par_over}, {@code and}, {@code critical}, {@code option}, {@code
   * break}, {@code end}, {@code links}, {@code link}, {@code properties}, {@code details}, {@code
   * over}, {@code note}, {@code activate}, {@code deactivate}, {@code autonumber}, {@code off}, and
   * the diagram-opening {@code sequenceDiagram} itself. {@code title} is included defensively: its
   * lexer rule only fires when the keyword is followed by same-line text ({@code
   * "title"\s[^#\n;]+}), so a bare {@code title} alias does not collide against today's grammar,
   * but a future revision could drop that requirement and a suffixed alias costs nothing. Words the
   * grammar only recognizes as part of a multi-word phrase ({@code "left of"}, {@code "right of"})
   * are absent — {@link #aliasToken} can never produce a token containing a space.
   */
  private static final Set<String> MERMAID_RESERVED_ALIASES =
      Set.of(
          "sequencediagram",
          "participant",
          "actor",
          "create",
          "destroy",
          "box",
          "loop",
          "rect",
          "opt",
          "alt",
          "else",
          "par",
          "par_over",
          "and",
          "critical",
          "option",
          "break",
          "end",
          "links",
          "link",
          "properties",
          "details",
          "over",
          "note",
          "activate",
          "deactivate",
          "autonumber",
          "off",
          "title");

  /**
   * PlantUML sequence-diagram keywords a bare (unquoted) participant/arrow token risks colliding
   * with, matched case-insensitively.
   *
   * <p><b>@llmNote</b> Sourced from plantuml.com/sequence-diagram (verified 2026-09-13): the
   * participant-type keywords ({@code participant}, {@code actor}, {@code boundary}, {@code
   * control}, {@code entity}, {@code database}, {@code collections}, {@code queue}), the
   * block/control keywords ({@code alt}, {@code else}, {@code opt}, {@code loop}, {@code par},
   * {@code break}, {@code critical}, {@code group}, {@code end}), the messaging keywords ({@code
   * note}, {@code ref}, {@code activate}, {@code deactivate}, {@code destroy}, {@code create},
   * {@code return}), and the structural keywords ({@code box}, {@code title}, {@code header},
   * {@code footer}, {@code newpage}, {@code autonumber}, {@code hide}, {@code show}, {@code
   * skinparam}, {@code mainframe}, {@code partition}). PlantUML's own docs demonstrate quoting as
   * the documented escape for exactly this collision ({@code participant "I have a really\nlong
   * name" as L}, {@code "Bob()" -> "This is very\nlong" as Long} — quoting works in a message/arrow
   * line, not only a declaration), which is why {@link #quoteIfNeeded} closes this with the same
   * quoting mechanism it already uses for special characters, rather than {@link #aliasToken}'s
   * suffix.
   */
  private static final Set<String> PLANTUML_RESERVED_WORDS =
      Set.of(
          "participant",
          "actor",
          "boundary",
          "control",
          "entity",
          "database",
          "collections",
          "queue",
          "alt",
          "else",
          "opt",
          "loop",
          "par",
          "break",
          "critical",
          "group",
          "end",
          "note",
          "ref",
          "activate",
          "deactivate",
          "destroy",
          "create",
          "return",
          "box",
          "title",
          "header",
          "footer",
          "newpage",
          "autonumber",
          "hide",
          "show",
          "skinparam",
          "mainframe",
          "partition");

  /**
   * A Mermaid participant alias: a single bare token, so it is safe unquoted in message lines.
   *
   * <p><b>@edgeCase</b> The alias is emitted both in {@code participant X as Name} and, unquoted,
   * on every arrow. {@link #identifier} is not enough there — it can still contain a space, which
   * would split the arrow into two tokens — so this reduces to {@code [A-Za-z0-9_]} and falls back
   * to {@code P} when nothing survives. A class name with no uppercase letters keeps its historical
   * alias: {@code scheduler} stays {@code scheduler}.
   *
   * <p><b>@edgeCase</b> A class literally named {@code end} or {@code participant} used to yield
   * that word, unchanged, as its own alias — a bare token Mermaid's grammar reserves for closing a
   * block ({@code end}) or opening a declaration ({@code participant}), which the parser rejects
   * outright rather than rendering. A token that collides with {@link #MERMAID_RESERVED_ALIASES},
   * case-insensitively, gets a trailing {@code _}: {@code end} becomes {@code end_}. Found by a
   * cross-port participant-alias review, 2026-09-13.
   *
   * @param raw the candidate alias
   * @return a non-empty token safe to emit unquoted, and never a bare Mermaid keyword
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
    if (sb.length() == 0) {
      return "P";
    }
    var token = sb.toString();
    return MERMAID_RESERVED_ALIASES.contains(token.toLowerCase(Locale.ROOT)) ? token + "_" : token;
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
