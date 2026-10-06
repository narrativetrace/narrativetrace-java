/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A case declares the registry that delivered its skill pages in its own {@code case.json}, beside
 * the fixture it scaffolds. Most cases declare none: the harness copies this repository's rendered
 * pages in and the case is about the prompt, not about a registry.
 */
class CaseRegistryTest {

  private static Path caseWith(Path dir, String manifest) throws IOException {
    Files.writeString(dir.resolve("case.json"), manifest);
    return dir;
  }

  @Test
  void readsTheDeclaredRegistryDelivery(@TempDir Path dir) throws IOException {
    Path caseDir =
        caseWith(
            dir, "{ \"fixture\": \"evals/fixtures/empty-project\", \"registry\": \"npx-skills\" }");

    assertThat(CaseRegistry.registryFor(caseDir)).contains(RegistryPreStep.NPX_SKILLS);
  }

  @Test
  void aCaseThatDeclaresNoRegistryHasNoPreStep(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"fixture\": \"evals/fixtures/empty-project\" }");

    assertThat(CaseRegistry.registryFor(caseDir)).isEmpty();
  }

  @Test
  void aCaseWithNoManifestAtAllHasNoPreStep(@TempDir Path dir) {
    assertThat(CaseRegistry.registryFor(dir)).isEmpty();
  }

  /**
   * A typo must never read as "no registry": the pre-step is the whole arrangement a registry case
   * measures, and a case that silently skipped it would pass as a plain prompt replay while its
   * name and its ledger row still claimed a registry.
   */
  @Test
  void refusesAnIdOutsideTheVocabularyRatherThanSkippingThePreStep(@TempDir Path dir)
      throws IOException {
    Path caseDir = caseWith(dir, "{ \"registry\": \"gemini-skills\" }");

    assertThatThrownBy(() -> CaseRegistry.registryFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gemini-skills")
        .hasMessageContaining("npx-skills");
  }
}
