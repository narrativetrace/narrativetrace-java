/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * {@code ledger/promotion.md} is committed BUILD OUTPUT — this test is its drift check: what {@link
 * CatalogueIndex#ALL} and {@code ledger/runs.jsonl} render TODAY must byte-match what is committed.
 * Mirrors {@code render.RenderDriftTest}'s own discipline for {@code SKILL.md}/{@code AGENTS.md}.
 */
class PromotionDriftTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  @Test
  void promotionMdMatchesRunsJsonlAndTheTypedCatalogue() {
    Path promotionPath = REPO_ROOT.resolve("narrativetrace-skills-catalogue/ledger/promotion.md");
    Path runsPath = REPO_ROOT.resolve("narrativetrace-skills-catalogue/ledger/runs.jsonl");

    String runsContent = readText(runsPath);
    String expected =
        PromotionRenderer.render(CatalogueIndex.ALL, RunLedgerRow.parseJsonl(runsContent));

    assertThat(promotionPath)
        .as(promotionPath + " must exist — regenerate it from ledger/runs.jsonl and the catalogue")
        .exists();
    assertThat(readText(promotionPath)).isEqualTo(expected);
  }

  private static String readText(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
