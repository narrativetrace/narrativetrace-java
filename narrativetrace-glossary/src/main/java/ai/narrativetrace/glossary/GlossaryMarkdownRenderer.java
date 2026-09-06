/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the generated {@code glossary.md} view from a {@link Glossary}.
 *
 * <p>INTENT: The Markdown view is the PR-review surface (ADR-011): the accepted-abbreviation
 * section, then one section per context, one table per term kind, definitions/synonyms/translations
 * inline. It is regenerated on every harvest; the JSON is the single editable source.
 */
public final class GlossaryMarkdownRenderer {

  private static final String TABLE_HEADER =
      "| Term | Status | Definition | Deprecated synonyms | Translations |\n|---|---|---|---|---|\n";

  private static final String ABBREVIATION_TABLE_HEADER =
      "| Abbreviation | Stands for |\n|---|---|\n";

  /**
   * Renders the full Markdown document.
   *
   * @param glossary glossary to render; must not be {@code null}
   * @return Markdown text ending in a newline; contexts sorted by name, one table per kind present
   */
  public String render(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    var out = new StringBuilder();
    out.append("# Domain Glossary\n\n");
    out.append(
        "Generated from `glossary.json` — edit the JSON, then regenerate; do not edit this"
            + " file.\n");
    renderAbbreviations(out, glossary);
    glossary.contexts().keySet().stream()
        .sorted()
        .forEach(name -> renderContext(out, glossary, glossary.contexts().get(name)));
    assert out.charAt(out.length() - 1) == '\n' : "document must end with a newline";
    return out.toString();
  }

  /**
   * The accepted-shorthand section, root-level and above the contexts.
   *
   * <p>INTENT: This is the vocabulary decision the reviewable document exists to show. Leaving it
   * out would hide the one part of the glossary that changes how every identifier in the repository
   * is scored.
   */
  private void renderAbbreviations(StringBuilder out, Glossary glossary) {
    if (glossary.abbreviations().isEmpty()) {
      return;
    }
    out.append("\n## Accepted abbreviations\n\n")
        .append("Project shorthand the clarity scorers accept and spell out.\n\n")
        .append(ABBREVIATION_TABLE_HEADER);
    glossary.abbreviations().keySet().stream()
        .sorted()
        .forEach(
            abbreviation ->
                out.append("| ")
                    .append(cell(abbreviation))
                    .append(" | ")
                    .append(cell(glossary.abbreviations().get(abbreviation)))
                    .append(" |\n"));
  }

  private void renderContext(StringBuilder out, Glossary glossary, BoundedContext context) {
    out.append("\n## ").append(cell(context.name())).append("\n");
    if (context.description() != null) {
      out.append("\n").append(context.description()).append("\n");
    }
    if (!context.packages().isEmpty()) {
      out.append("\nPackages: ")
          .append(
              context.packages().stream()
                  .map(pkg -> "`" + pkg + "`")
                  .collect(Collectors.joining(", ")))
          .append("\n");
    }
    for (var kind : TermKind.values()) {
      renderKindTable(out, termsOf(glossary, context.name(), kind), kind);
    }
  }

  private void renderKindTable(StringBuilder out, List<GlossaryTerm> terms, TermKind kind) {
    if (terms.isEmpty()) {
      return;
    }
    out.append("\n### ").append(heading(kind)).append("\n\n").append(TABLE_HEADER);
    for (var term : terms) {
      out.append("| ")
          .append(cell(term.term()))
          .append(" | ")
          .append(term.status().jsonName())
          .append(" | ")
          .append(term.definition() == null ? "" : cell(term.definition()))
          .append(" | ")
          .append(synonymsCell(term))
          .append(" | ")
          .append(translationsCell(term))
          .append(" |\n");
    }
  }

  private static List<GlossaryTerm> termsOf(Glossary glossary, String context, TermKind kind) {
    return glossary.terms().stream()
        .filter(term -> term.context().equals(context) && term.kind() == kind)
        .toList();
  }

  private static String heading(TermKind kind) {
    return switch (kind) {
      case WORD -> "Words";
      case NOUN_PHRASE -> "Noun phrases";
      case VERB_PHRASE -> "Verb phrases";
      case TEMPLATE -> "Templates";
    };
  }

  private static String synonymsCell(GlossaryTerm term) {
    return term.synonyms().stream()
        .map(s -> s.note() == null ? cell(s.alias()) : cell(s.alias()) + " — " + cell(s.note()))
        .collect(Collectors.joining("; "));
  }

  private static String translationsCell(GlossaryTerm term) {
    return term.translations().keySet().stream()
        .sorted()
        .map(locale -> cell(locale) + ": " + cell(term.translations().get(locale)))
        .collect(Collectors.joining("; "));
  }

  /** Escapes table-breaking characters: pipes are escaped, newlines collapse to spaces. */
  private static String cell(String text) {
    return text.replace("|", "\\|").replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
  }
}
