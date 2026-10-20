/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B-99's ledger lint, part of {@code check}: every committed {@code ledger/runs.jsonl} row for
 * platform Claude carries a full model id ({@code claude-...}), never a bare shorthand like {@code
 * haiku} — the alias {@link EvalRunnerArgs#parse} now resolves away ({@link ModelAlias}) before it
 * can reach a ledger row. A row that still carries one is either pre-{@link ModelAlias} history
 * left unnormalized, or a future writer that bypassed {@link EvalRunnerArgs#parse}; either way the
 * ledger has stopped being a reliable record of which model actually ran.
 */
class LedgerModelIdTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  @Test
  void everyClaudeRowCarriesAFullModelIdNeverABareAlias() {
    Path runsPath = REPO_ROOT.resolve("narrativetrace-skills-catalogue/ledger/runs.jsonl");
    List<RunLedgerRow> rows = RunLedgerRow.parseJsonl(readText(runsPath));

    List<RunLedgerRow> aliased =
        rows.stream()
            .filter(row -> row.platform() == Platform.CLAUDE)
            .filter(row -> !row.model().startsWith("claude-"))
            .toList();

    assertThat(aliased)
        .as("ledger/runs.jsonl rows carrying a bare Claude model alias instead of a full id")
        .isEmpty();
  }

  private static String readText(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
