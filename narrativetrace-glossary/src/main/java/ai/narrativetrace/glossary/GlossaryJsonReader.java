/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict reader for {@code glossary.json}.
 *
 * <p>INTENT: The committed glossary is hand-curated; a typo (unknown key, bad enum label, malformed
 * date) must fail loudly at load, never be silently dropped. Validation happens before any work —
 * every structural rule of {@link Glossary} is enforced by the record constructors this reader
 * calls.
 *
 * <p><b>@llmNote</b> Schema 2 adds the root-level {@code abbreviations} section. This reader
 * accepts it at any {@code schemaVersion} ≥ 1 and accepts a file that omits it — the section is
 * additive, so a strict version gate would only create a migration cliff. The <em>writer</em> is
 * where the version stamp is decided.
 */
public final class GlossaryJsonReader {

  private static final Set<String> ROOT_KEYS =
      Set.of("schemaVersion", "contexts", "abbreviations", "terms");
  private static final Set<String> CONTEXT_KEYS = Set.of("packages", "description");
  private static final Set<String> TERM_KEYS =
      Set.of(
          "term",
          "context",
          "kind",
          "status",
          "definition",
          "translations",
          "synonyms",
          "sources",
          "firstSeen");
  private static final Set<String> SYNONYM_KEYS = Set.of("alias", "note");

  /**
   * Parses glossary JSON text into a validated {@link Glossary}.
   *
   * @param json complete JSON document; must not be {@code null}
   * @return the parsed glossary
   * @throws IllegalArgumentException on malformed JSON, unknown keys, missing required keys, bad
   *     enum labels, or malformed dates
   */
  public Glossary read(String json) {
    var root = asObject(JsonParser.parse(json), "document root");
    rejectUnknownKeys(root, ROOT_KEYS, "glossary");
    int schemaVersion = (int) asLong(required(root, "schemaVersion"), "schemaVersion");
    var contexts = readContexts(asObject(required(root, "contexts"), "contexts"));
    var terms = readTerms(asArray(required(root, "terms"), "terms"));
    return new Glossary(schemaVersion, contexts, readAbbreviations(root), terms);
  }

  /**
   * Reads the schema-2 {@code abbreviations} section.
   *
   * <p><b>@edgeCase</b> Accepted at any {@code schemaVersion} ≥ 1. The section is additive and
   * harmless, so refusing it on a 1-stamped file would buy nothing and create a migration cliff for
   * a repository that adds shorthand before its writer stamps 2.
   */
  private Map<String, String> readAbbreviations(Map<String, Object> root) {
    if (!root.containsKey("abbreviations")) {
      return Map.of();
    }
    var raw = asObject(root.get("abbreviations"), "abbreviations");
    var abbreviations = new LinkedHashMap<String, String>();
    for (var entry : raw.entrySet()) {
      abbreviations.put(
          entry.getKey(), asString(entry.getValue(), "abbreviation '" + entry.getKey() + "'"));
    }
    return abbreviations;
  }

  private Map<String, BoundedContext> readContexts(Map<String, Object> raw) {
    var contexts = new LinkedHashMap<String, BoundedContext>();
    for (var entry : raw.entrySet()) {
      var body = asObject(entry.getValue(), "context '" + entry.getKey() + "'");
      rejectUnknownKeys(body, CONTEXT_KEYS, "context '" + entry.getKey() + "'");
      var packages = readStringList(required(body, "packages"), "packages");
      contexts.put(
          entry.getKey(),
          new BoundedContext(entry.getKey(), packages, optionalString(body, "description")));
    }
    return contexts;
  }

  private List<GlossaryTerm> readTerms(List<Object> raw) {
    var terms = new ArrayList<GlossaryTerm>();
    for (var element : raw) {
      terms.add(readTerm(asObject(element, "term entry")));
    }
    return terms;
  }

  private GlossaryTerm readTerm(Map<String, Object> body) {
    rejectUnknownKeys(body, TERM_KEYS, "term");
    return new GlossaryTerm(
        asString(required(body, "term"), "term"),
        asString(required(body, "context"), "context"),
        readKind(asString(required(body, "kind"), "kind")),
        readStatus(asString(required(body, "status"), "status")),
        optionalString(body, "definition"),
        readTranslations(body),
        readSynonyms(body),
        body.containsKey("sources") ? readStringList(body.get("sources"), "sources") : List.of(),
        readDate(asString(required(body, "firstSeen"), "firstSeen")));
  }

  private Map<String, String> readTranslations(Map<String, Object> body) {
    if (!body.containsKey("translations")) {
      return Map.of();
    }
    var raw = asObject(body.get("translations"), "translations");
    var translations = new LinkedHashMap<String, String>();
    for (var entry : raw.entrySet()) {
      translations.put(
          entry.getKey(), asString(entry.getValue(), "translation '" + entry.getKey() + "'"));
    }
    return translations;
  }

  private List<SynonymAlias> readSynonyms(Map<String, Object> body) {
    if (!body.containsKey("synonyms")) {
      return List.of();
    }
    var synonyms = new ArrayList<SynonymAlias>();
    for (var element : asArray(body.get("synonyms"), "synonyms")) {
      var synonym = asObject(element, "synonym entry");
      rejectUnknownKeys(synonym, SYNONYM_KEYS, "synonym");
      synonyms.add(
          new SynonymAlias(
              asString(required(synonym, "alias"), "alias"), optionalString(synonym, "note")));
    }
    return synonyms;
  }

  private static TermKind readKind(String label) {
    for (var kind : TermKind.values()) {
      if (kind.jsonName().equals(label)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("unknown term kind '" + label + "'");
  }

  private static TermStatus readStatus(String label) {
    for (var status : TermStatus.values()) {
      if (status.jsonName().equals(label)) {
        return status;
      }
    }
    throw new IllegalArgumentException("unknown term status '" + label + "'");
  }

  private static LocalDate readDate(String text) {
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("invalid firstSeen date '" + text + "'", e);
    }
  }

  private static List<String> readStringList(Object value, String what) {
    var strings = new ArrayList<String>();
    for (var element : asArray(value, what)) {
      strings.add(asString(element, what + " element"));
    }
    return strings;
  }

  private static void rejectUnknownKeys(Map<String, Object> body, Set<String> known, String what) {
    for (var key : body.keySet()) {
      if (!known.contains(key)) {
        throw new IllegalArgumentException("unknown key '" + key + "' in " + what);
      }
    }
  }

  private static Object required(Map<String, Object> body, String key) {
    var value = body.get(key);
    if (value == null || value == JsonParser.NULL) {
      throw new IllegalArgumentException("missing required key '" + key + "'");
    }
    return value;
  }

  private static String optionalString(Map<String, Object> body, String key) {
    var value = body.get(key);
    if (value == null || value == JsonParser.NULL) {
      return null;
    }
    return asString(value, key);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String what) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(what + " must be a JSON object");
    }
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asArray(Object value, String what) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(what + " must be a JSON array");
    }
    return (List<Object>) value;
  }

  private static String asString(Object value, String what) {
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException(what + " must be a JSON string");
    }
    return s;
  }

  private static long asLong(Object value, String what) {
    if (!(value instanceof Long n)) {
      throw new IllegalArgumentException(what + " must be a JSON integer");
    }
    return n;
  }
}
