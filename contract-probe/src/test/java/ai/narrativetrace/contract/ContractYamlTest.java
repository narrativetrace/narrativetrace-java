/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractYamlTest {

  @TempDir File tempDir;

  @Test
  void readsDocumentedDefaultAndExpectedEffectIntoTheSameField() throws IOException {
    File file = new File(tempDir, "contract.yaml");
    Files.writeString(
        file.toPath(),
        """
        version_source: "gradle.properties#narrativetraceVersion"
        entries:
          - id: e1
            kind: entry-point
            registry: maven-central
            coordinate: "ai.narrativetrace:narrativetrace-core"
            page: "documentation/foo.md#a"
            claim: "c1"
            since: "0.1.0"
            documented_default: "PRESENT"
            probe: "probe.java"
          - id: e2
            kind: config-shape
            page: "documentation/foo.md#b"
            claim: "c2"
            since: "0.1.0"
            expected_effect: "true"
            probe: "probe.java"
        """);

    List<ContractEntry> entries = ContractYaml.read(file);

    assertEquals(2, entries.size());
    assertEquals("PRESENT", entries.get(0).expect());
    assertEquals("ai.narrativetrace:narrativetrace-core", entries.get(0).coordinate());
    assertEquals("true", entries.get(1).expect());
  }
}
