/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
    Path promotionPath = REPO_ROOT.resolve("narrativetrace-skills/ledger/promotion.md");
    Path runsPath = REPO_ROOT.resolve("narrativetrace-skills/ledger/runs.jsonl");

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
