/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.core.export.JsonEscape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic serializer for {@code glossary.json}.
 *
 * <p>INTENT: The committed glossary must be byte-identical whenever vocabulary is unchanged
 * (anti-churn, ADR-012). Determinism rules: contexts sorted by name, abbreviations sorted by
 * abbreviation, terms in the model's canonical {@code (context, term)} order (enforced by the
 * {@link Glossary} constructor), fixed key order, 2-space indent, trailing newline. The writer
 * never emits volatile statistics — those belong to build-directory reports.
 *
 * <p><b>@llmNote</b> The {@code schemaVersion} it emits is 2 exactly when {@code abbreviations} is
 * non-empty and 1 otherwise. That is not a rule this class applies — {@link Glossary} canonicalizes
 * the version at construction, so the writer only has to report it. Anti-churn is the reason: a
 * repository that never declared shorthand must see its file unchanged.
 */
public final class GlossaryJsonWriter {

  /**
   * Serializes a glossary to its canonical JSON text.
   *
   * @param glossary glossary to serialize; must not be {@code null}
   * @return deterministic JSON document ending in a newline
   */
  public String write(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    var out =
        "{\n"
            + "  \"schemaVersion\": "
            + glossary.schemaVersion()
            + ",\n"
            + "  \"contexts\": "
            + renderContexts(glossary)
            + ",\n"
            + renderAbbreviations(glossary)
            + "  \"terms\": "
            + renderTerms(glossary)
            + "\n"
            + "}\n";
    assert out.endsWith("\n") : "canonical file must end with a trailing newline";
    return out;
  }

  /** Rendered with its trailing comma and newline, or empty — the section is omitted when empty. */
  private static String renderAbbreviations(Glossary glossary) {
    if (glossary.abbreviations().isEmpty()) {
      return "";
    }
    return glossary.abbreviations().keySet().stream()
        .sorted()
        .map(
            abbreviation ->
                "    "
                    + quoted(abbreviation)
                    + ": "
                    + quoted(glossary.abbreviations().get(abbreviation)))
        .collect(Collectors.joining(",\n", "  \"abbreviations\": {\n", "\n  },\n"));
  }

  private String renderContexts(Glossary glossary) {
    if (glossary.contexts().isEmpty()) {
      return "{}";
    }
    return glossary.contexts().keySet().stream()
        .sorted()
        .map(name -> renderContext(glossary.contexts().get(name)))
        .collect(Collectors.joining(",\n", "{\n", "\n  }"));
  }

  private String renderContext(BoundedContext context) {
    var body = new StringBuilder();
    body.append("    \"").append(JsonEscape.escape(context.name())).append("\": {\n");
    body.append("      \"packages\": ").append(renderStringArray(context.packages()));
    if (context.description() != null) {
      body.append(",\n      \"description\": ")
          .append(quoted(context.description()))
          .append("\n    }");
    } else {
      body.append("\n    }");
    }
    return body.toString();
  }

  private String renderTerms(Glossary glossary) {
    if (glossary.terms().isEmpty()) {
      return "[]";
    }
    return glossary.terms().stream()
        .map(GlossaryJsonWriter::renderTerm)
        .collect(Collectors.joining(",\n", "[\n", "\n  ]"));
  }

  private static String renderTerm(GlossaryTerm term) {
    var fields = new ArrayList<String>();
    fields.add("\"term\": " + quoted(term.term()));
    fields.add("\"context\": " + quoted(term.context()));
    fields.add("\"kind\": " + quoted(term.kind().jsonName()));
    fields.add("\"status\": " + quoted(term.status().jsonName()));
    if (term.definition() != null) {
      fields.add("\"definition\": " + quoted(term.definition()));
    }
    if (!term.translations().isEmpty()) {
      fields.add("\"translations\": " + renderTranslations(term.translations()));
    }
    if (!term.synonyms().isEmpty()) {
      fields.add("\"synonyms\": " + renderSynonyms(term.synonyms()));
    }
    if (!term.sources().isEmpty()) {
      fields.add("\"sources\": " + renderStringArray(term.sources()));
    }
    fields.add("\"firstSeen\": " + quoted(term.firstSeen().toString()));
    return fields.stream().collect(Collectors.joining(",\n      ", "    {\n      ", "\n    }"));
  }

  private static String renderTranslations(Map<String, String> translations) {
    return translations.keySet().stream()
        .sorted()
        .map(locale -> "        " + quoted(locale) + ": " + quoted(translations.get(locale)))
        .collect(Collectors.joining(",\n", "{\n", "\n      }"));
  }

  private static String renderSynonyms(List<SynonymAlias> synonyms) {
    return synonyms.stream()
        .map(GlossaryJsonWriter::renderSynonym)
        .collect(Collectors.joining(",\n", "[\n", "\n      ]"));
  }

  private static String renderSynonym(SynonymAlias synonym) {
    var body = "{ \"alias\": " + quoted(synonym.alias());
    if (synonym.note() != null) {
      body += ", \"note\": " + quoted(synonym.note());
    }
    return "        " + body + " }";
  }

  private static String renderStringArray(List<String> values) {
    return values.stream()
        .map(GlossaryJsonWriter::quoted)
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static String quoted(String value) {
    return "\"" + JsonEscape.escape(value) + "\"";
  }
}
