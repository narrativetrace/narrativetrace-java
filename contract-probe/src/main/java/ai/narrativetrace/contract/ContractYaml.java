/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads a {@code contract.yaml} — the schema itself is validated per commit by buildSrc's {@code
 * contractLint}; this reader trusts that and only extracts what {@link ContractRunner} needs to
 * run.
 *
 * @llmNote The file handed to it is normally NOT the working tree's: {@code
 *     scripts/contract-check.sh} extracts {@code documentation/contract.yaml} as of the tag whose
 *     artifact it installs. A file from an older release carries keys this reader no longer names
 *     ({@code since:}); keys it does not ask for are simply not read, which is what lets one runner
 *     check any release's contract.
 */
public final class ContractYaml {

  private ContractYaml() {}

  @SuppressWarnings("unchecked")
  public static List<ContractEntry> read(File file) throws IOException {
    try (InputStream in = Files.newInputStream(file.toPath())) {
      Map<String, Object> root = new Yaml().load(in);
      List<Map<String, Object>> rawEntries = (List<Map<String, Object>>) root.get("entries");
      List<ContractEntry> entries = new ArrayList<>();
      for (Map<String, Object> raw : rawEntries) {
        String expect = (String) raw.getOrDefault("documented_default", raw.get("expected_effect"));
        entries.add(
            new ContractEntry(
                (String) raw.get("id"),
                (String) raw.get("kind"),
                (String) raw.get("page"),
                (String) raw.get("claim"),
                expect,
                (String) raw.get("probe"),
                (String) raw.get("coordinate"),
                (String) raw.get("registry")));
      }
      return entries;
    }
  }
}
