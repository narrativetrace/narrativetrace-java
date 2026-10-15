/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.glossary.GlossaryAwareClarityScannerMain;
import ai.narrativetrace.glossary.GlossaryVocabulary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code clarityScan} entry point, exercised from outside both modules' packages: a committed
 * glossary must reach the scan's scores and notes, and a project without one must be unaffected.
 */
class GlossaryAwareClarityScanTest {

  private static final String GLOSSARY =
      """
      {
        "schemaVersion": 1,
        "contexts": {"trading": {"packages": ["ai.narrativetrace.glossary.e2e"]}},
        "terms": [
          {
            "term": "fold tranche",
            "context": "trading",
            "kind": "verb-phrase",
            "status": "curated",
            "firstSeen": "2020-01-01"
          }
        ]
      }
      """;

  /**
   * A classes directory holding exactly one compiled class, so the scan's result is about {@link
   * TrancheService} and not about every test class that happens to sit beside it.
   */
  private static Path classesDirWithOnlyTrancheService(Path tempDir) throws Exception {
    return classesDirWithOnly(TrancheService.class, tempDir);
  }

  private static Path classesDirWithOnly(Class<?> clazz, Path tempDir) throws Exception {
    var relative =
        Path.of("ai", "narrativetrace", "glossary", "e2e", clazz.getSimpleName() + ".class");
    var source =
        Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().getPath())
            .resolve(relative);
    var classesDir = tempDir.resolve("classes");
    Files.createDirectories(classesDir.resolve(relative).getParent());
    Files.copy(source, classesDir.resolve(relative));
    return classesDir;
  }

  private static double scoreOf(String json) {
    var marker = "\"overallScore\":";
    int start = json.indexOf(marker) + marker.length();
    int end = start;
    while (end < json.length() && "-0123456789.eE".indexOf(json.charAt(end)) >= 0) {
      end++;
    }
    return Double.parseDouble(json.substring(start, end).trim());
  }

  private static String scan(Path classesDir, Path outputDir, Path glossaryDir) throws Exception {
    var args =
        glossaryDir == null
            ? new String[] {
              "--classes-dir", classesDir.toString(),
              "--output-dir", outputDir.toString(),
              "--format", "json"
            }
            : new String[] {
              "--classes-dir", classesDir.toString(),
              "--output-dir", outputDir.toString(),
              "--format", "json",
              "--glossary-dir", glossaryDir.toString()
            };
    GlossaryAwareClarityScannerMain.main(args);
    return Files.readString(outputDir.resolve("clarity-scan-results.json"));
  }

  @Test
  void aCommittedGlossaryRaisesTheScanScoreOfItsOwnVocabulary(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), GLOSSARY);
    var classesDir = classesDirWithOnlyTrancheService(tempDir);

    var without = scan(classesDir, tempDir.resolve("plain"), null);
    var with = scan(classesDir, tempDir.resolve("glossed"), tempDir);

    assertThat(scoreOf(with)).isGreaterThan(scoreOf(without));
    assertThat(with).contains("Domain verb 'fold' + domain noun 'tranche'");
    assertThat(without).doesNotContain("Domain verb 'fold'");
  }

  /**
   * Parity check 2026-10-11, capability C12 (Pro §169, milestone 0): a glossary verb-phrase names
   * the preferred verb for its noun ({@code fold tranche}), so a generic-verb collocation issue on
   * that noun ({@code handleTranche} — {@code tranche} is unknown to the built-in {@code
   * CollocationDictionary}, so only the glossary can supply a suggestion here) should propose
   * {@code foldTranche}. {@link GlossaryVocabulary#of} only ever flattens a verb-phrase into
   * separate domain-verb/domain-noun sets ({@code collect}), discarding the pairing, so {@code
   * ClarityAnalyzer.findCollocationIssues} — which only ever consults the hardcoded {@code
   * CollocationDictionary} — has no way to learn it. Confirmed red against today's code: the
   * scenario's {@code issues} array comes back empty, with or without the glossary. {@link
   * Disabled @Disabled} — pending the fix — so {@code check} stays green; the cross-runtime parity
   * ledger tracks it as gap C12.
   */
  @Test
  @Disabled("parity gap C12 — see the method javadoc")
  void aGlossaryVerbPhraseIsProposedAsTheCollocationSuggestionForItsNoun(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), GLOSSARY);
    var classesDir = classesDirWithOnly(TrancheHandlingService.class, tempDir);

    var with = scan(classesDir, tempDir.resolve("glossed"), tempDir);

    assertThat(with).contains("TrancheHandlingService.handleTranche");
    assertThat(with).contains("foldTranche");
  }

  @Test
  void aProjectWithNoCommittedGlossaryScansExactlyAsBefore(@TempDir Path tempDir) throws Exception {
    var classesDir = classesDirWithOnlyTrancheService(tempDir);

    var withoutArgument = scan(classesDir, tempDir.resolve("plain"), null);
    var withEmptyDirectory = scan(classesDir, tempDir.resolve("glossed"), tempDir);

    assertThat(withEmptyDirectory).isEqualTo(withoutArgument);
  }
}
