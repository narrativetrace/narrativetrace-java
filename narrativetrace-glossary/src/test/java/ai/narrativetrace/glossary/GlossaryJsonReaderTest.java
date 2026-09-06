/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryJsonReaderTest {

  private final GlossaryJsonWriter writer = new GlossaryJsonWriter();
  private final GlossaryJsonReader reader = new GlossaryJsonReader();

  @Test
  void roundTripsFullGlossaryThroughWriteAndRead() {
    var glossary =
        new Glossary(
            1,
            Map.of(
                "billing",
                    new BoundedContext("billing", List.of("com.acme.billing"), "Money \"stuff\""),
                "_unassigned", new BoundedContext("_unassigned", List.of(), null)),
            List.of(
                new GlossaryTerm(
                    "overdraft account",
                    "billing",
                    TermKind.NOUN_PHRASE,
                    TermStatus.CURATED,
                    "Account permitted to go below zero.",
                    Map.of("es", "cuenta con descubierto", "de", "Dispokonto"),
                    List.of(
                        new SynonymAlias("account with overdraft", "legacy phrasing"),
                        new SynonymAlias("minus account", null)),
                    List.of("billing.OverdraftService.openOverdraftAccount"),
                    LocalDate.of(2026, 8, 11)),
                new GlossaryTerm(
                    "ticket",
                    "_unassigned",
                    TermKind.WORD,
                    TermStatus.HARVESTED,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    LocalDate.of(2026, 8, 12))));

    var roundTripped = reader.read(writer.write(glossary));

    assertThat(roundTripped).isEqualTo(glossary);
  }

  @Test
  void acceptsExplicitNullForOptionalFields() {
    var glossary =
        reader.read(
            """
            {
              "schemaVersion": 1,
              "contexts": {
                "billing": { "packages": [], "description": null }
              },
              "terms": [
                {
                  "term": "overdraft account",
                  "context": "billing",
                  "kind": "noun-phrase",
                  "status": "harvested",
                  "definition": null,
                  "firstSeen": "2026-08-11"
                }
              ]
            }
            """);

    assertThat(glossary.contexts().get("billing").description()).isNull();
    assertThat(glossary.terms().get(0).definition()).isNull();
  }

  @Test
  void rejectsSchemaViolations() {
    assertRejected("[]", "document root must be a JSON object");
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {}, \"terms\": [], \"extra\": 1}",
        "unknown key 'extra'");
    assertRejected("{\"contexts\": {}, \"terms\": []}", "missing required key 'schemaVersion'");
    assertRejected(
        "{\"schemaVersion\": \"1\", \"contexts\": {}, \"terms\": []}",
        "schemaVersion must be a JSON integer");
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": [], \"terms\": []}",
        "contexts must be a JSON object");
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {}, \"terms\": {}}", "terms must be a JSON array");
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {\"a\": {\"packages\": [], \"typo\": 1}}, \"terms\": []}",
        "unknown key 'typo' in context 'a'");
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {\"a\": {}}, \"terms\": []}",
        "missing required key 'packages'");
  }

  @Test
  void rejectsTermViolations() {
    assertRejectedTerm(
        "{\"context\": \"a\", \"kind\": \"word\", \"status\": \"harvested\", \"firstSeen\": \"2026-08-11\"}",
        "missing required key 'term'");
    assertRejectedTerm(term("word", "harvested", "not-a-date"), "invalid firstSeen date");
    assertRejectedTerm(
        term("adjective", "harvested", "2026-08-11"), "unknown term kind 'adjective'");
    assertRejectedTerm(term("word", "reviewed", "2026-08-11"), "unknown term status 'reviewed'");
    assertRejectedTerm(
        "{\"term\": \"t\", \"context\": \"a\", \"kind\": \"word\", \"status\": \"harvested\", \"firstSeen\": \"2026-08-11\", \"occurrences\": 3}",
        "unknown key 'occurrences'");
    assertRejectedTerm(
        "{\"term\": 1, \"context\": \"a\", \"kind\": \"word\", \"status\": \"harvested\", \"firstSeen\": \"2026-08-11\"}",
        "term must be a JSON string");
    assertRejectedTerm(
        "{\"term\": \"t\", \"context\": \"a\", \"kind\": \"word\", \"status\": \"harvested\", \"firstSeen\": \"2026-08-11\", \"synonyms\": [{\"alias\": \"x\", \"extra\": 1}]}",
        "unknown key 'extra' in synonym");
  }

  @Test
  void rejectsExplicitNullForRequiredKeys() {
    assertRejected(
        "{\"schemaVersion\": null, \"contexts\": {}, \"terms\": []}",
        "missing required key 'schemaVersion'");
  }

  @Test
  void rejectsTermReferencingUndeclaredContextViaModelInvariant() {
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {}, \"terms\": ["
            + term("word", "harvested", "2026-08-11")
            + "]}",
        "undeclared context");
  }

  private static String term(String kind, String status, String firstSeen) {
    return "{\"term\": \"t\", \"context\": \"a\", \"kind\": \"%s\", \"status\": \"%s\", \"firstSeen\": \"%s\"}"
        .formatted(kind, status, firstSeen);
  }

  private void assertRejectedTerm(String termJson, String expectedMessagePart) {
    assertRejected(
        "{\"schemaVersion\": 1, \"contexts\": {\"a\": {\"packages\": []}}, \"terms\": ["
            + termJson
            + "]}",
        expectedMessagePart);
  }

  private void assertRejected(String json, String expectedMessagePart) {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(json))
        .withMessageContaining(expectedMessagePart);
  }

  @Test
  void readsAnAbbreviationsSection() {
    var glossary =
        reader.read(
            """
            {
              "schemaVersion": 2,
              "contexts": {},
              "abbreviations": { "fx": "foreign exchange", "calc": "calculate" },
              "terms": []
            }
            """);

    assertThat(glossary.abbreviations())
        .containsEntry("fx", "foreign exchange")
        .containsEntry("calc", "calculate");
  }

  @Test
  void acceptsASchemaOneFileWithNoAbbreviationsSection() {
    var glossary = reader.read("{ \"schemaVersion\": 1, \"contexts\": {}, \"terms\": [] }");

    assertThat(glossary.schemaVersion()).isEqualTo(1);
    assertThat(glossary.abbreviations()).isEmpty();
  }

  @Test
  void acceptsTheSectionOnASchemaOneFileBecauseItIsAdditive() {
    var glossary =
        reader.read(
            """
            {
              "schemaVersion": 1,
              "contexts": {},
              "abbreviations": { "fx": "foreign exchange" },
              "terms": []
            }
            """);

    assertThat(glossary.abbreviations()).containsEntry("fx", "foreign exchange");
  }

  @Test
  void rejectsANonStringExpansion() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                reader.read(
                    """
                    {
                      "schemaVersion": 2,
                      "contexts": {},
                      "abbreviations": { "fx": 42 },
                      "terms": []
                    }
                    """))
        .withMessageContaining("fx");
  }

  @Test
  void rejectsAnAbbreviationsSectionThatIsNotAnObject() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                reader.read(
                    """
                    { "schemaVersion": 2, "contexts": {}, "abbreviations": [], "terms": [] }
                    """))
        .withMessageContaining("abbreviations");
  }
}
