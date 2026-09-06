/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryJsonWriterTest {

  private final GlossaryJsonWriter writer = new GlossaryJsonWriter();

  @Test
  void rejectsNullGlossary() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> writer.write(null))
        .withMessageContaining("glossary");
  }

  @Test
  void writesEmptyGlossaryWithFixedKeyOrderAndTrailingNewline() {
    var json = writer.write(new Glossary(1, Map.of(), List.of()));

    assertThat(json)
        .isEqualTo(
            """
            {
              "schemaVersion": 1,
              "contexts": {},
              "terms": []
            }
            """);
  }

  @Test
  void writesContextsSortedByNameRegardlessOfMapOrder() {
    var glossary =
        new Glossary(
            1,
            Map.of(
                "support", new BoundedContext("support", List.of(), null),
                "billing",
                    new BoundedContext(
                        "billing",
                        List.of("com.acme.billing", "com.acme.funds"),
                        "Charging \"and\" invoicing")),
            List.of());

    var json = writer.write(glossary);

    assertThat(json)
        .isEqualTo(
            """
            {
              "schemaVersion": 1,
              "contexts": {
                "billing": {
                  "packages": ["com.acme.billing", "com.acme.funds"],
                  "description": "Charging \\"and\\" invoicing"
                },
                "support": {
                  "packages": []
                }
              },
              "terms": []
            }
            """);
  }

  @Test
  void writesTermsSortedByContextThenTermWithOptionalFieldsOmitted() {
    var contexts =
        Map.of(
            "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
            "support", new BoundedContext("support", List.of(), null));
    var curated =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            "Account permitted to go below zero.",
            Map.of("es", "cuenta con descubierto", "de", "Dispokonto"),
            List.of(
                new SynonymAlias("account with overdraft", "legacy v1 API phrasing"),
                new SynonymAlias("minus account", null)),
            List.of("billing.OverdraftService.openOverdraftAccount"),
            LocalDate.of(2026, 8, 11));
    var harvested =
        new GlossaryTerm(
            "ticket",
            "support",
            TermKind.WORD,
            TermStatus.HARVESTED,
            null,
            Map.of(),
            List.of(),
            List.of(),
            LocalDate.of(2026, 8, 12));

    var json = writer.write(new Glossary(1, contexts, List.of(harvested, curated)));

    assertThat(json)
        .isEqualTo(
            """
            {
              "schemaVersion": 1,
              "contexts": {
                "billing": {
                  "packages": ["com.acme.billing"]
                },
                "support": {
                  "packages": []
                }
              },
              "terms": [
                {
                  "term": "overdraft account",
                  "context": "billing",
                  "kind": "noun-phrase",
                  "status": "curated",
                  "definition": "Account permitted to go below zero.",
                  "translations": {
                    "de": "Dispokonto",
                    "es": "cuenta con descubierto"
                  },
                  "synonyms": [
                    { "alias": "account with overdraft", "note": "legacy v1 API phrasing" },
                    { "alias": "minus account" }
                  ],
                  "sources": ["billing.OverdraftService.openOverdraftAccount"],
                  "firstSeen": "2026-08-11"
                },
                {
                  "term": "ticket",
                  "context": "support",
                  "kind": "word",
                  "status": "harvested",
                  "firstSeen": "2026-08-12"
                }
              ]
            }
            """);
  }

  @Test
  void stampsSchemaTwoAndEmitsTheSectionWhenAbbreviationsAreDeclared() {
    var glossary =
        new Glossary(1, Map.of(), Map.of("fx", "foreign exchange", "calc", "calculate"), List.of());

    assertThat(writer.write(glossary))
        .isEqualTo(
            """
            {
              "schemaVersion": 2,
              "contexts": {},
              "abbreviations": {
                "calc": "calculate",
                "fx": "foreign exchange"
              },
              "terms": []
            }
            """);
  }

  @Test
  void keepsStampingSchemaOneAndOmitsTheSectionWhenThereAreNoAbbreviations() {
    var glossary = new Glossary(2, Map.of(), Map.of(), List.of());

    assertThat(writer.write(glossary))
        .isEqualTo(
            """
            {
              "schemaVersion": 1,
              "contexts": {},
              "terms": []
            }
            """);
  }

  @Test
  void aGlossaryWithoutAbbreviationsRoundTripsByteIdenticalToItsSchemaOneForm() {
    var schemaOne =
        """
        {
          "schemaVersion": 1,
          "contexts": {},
          "terms": []
        }
        """;

    assertThat(writer.write(new GlossaryJsonReader().read(schemaOne))).isEqualTo(schemaOne);
  }

  @Test
  void escapesAbbreviationsAndExpansions() {
    var glossary = new Glossary(2, Map.of(), Map.of("q", "quote \"unquote\""), List.of());

    assertThat(writer.write(glossary)).contains("\"q\": \"quote \\\"unquote\\\"\"");
  }
}
