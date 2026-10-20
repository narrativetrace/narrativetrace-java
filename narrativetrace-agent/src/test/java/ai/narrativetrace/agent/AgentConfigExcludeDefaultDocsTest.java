/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The two exclusion lists are published verbatim in the installation guide, never typed twice: the
 * shipped default for list 2 ({@link AgentConfig#DEFAULT_EXCLUDE}), and list 1's name-shape markers
 * ({@link GeneratedClassNames}). This test is the thing that would catch either list drifting away
 * from what the guide tells a reader to expect.
 */
class AgentConfigExcludeDefaultDocsTest {

  private static final Path INSTALLATION_GUIDE =
      Path.of(System.getProperty("projectDir", "."), "documentation", "installation-guide.md");

  private String installationGuide() throws IOException {
    return Files.readString(INSTALLATION_GUIDE);
  }

  @Test
  void everyDefaultExcludePrefixIsPublishedInTableOrder() throws IOException {
    var guide = installationGuide();

    var last = -1;
    for (var prefix : AgentConfig.DEFAULT_EXCLUDE) {
      var at = guide.indexOf("`" + prefix + "`");
      assertThat(at).as(prefix).isGreaterThan(last);
      last = at;
    }
  }

  @Test
  void noPrefixIsPublishedThatTheCodeDoesNotShip() throws IOException {
    var guide = installationGuide();
    var tableSection =
        guide.substring(
            guide.indexOf("Shipped default"), guide.indexOf("The key's presence decides"));

    for (var row : tableSection.split("\n")) {
      if (!row.startsWith("| `")) {
        continue;
      }
      var prefix = row.substring(3, row.indexOf('`', 3));
      assertThat(AgentConfig.DEFAULT_EXCLUDE).as(row).contains(prefix);
    }
  }

  @Test
  void list1NameShapeMarkersArePublished() throws IOException {
    var guide = installationGuide();

    assertThat(guide)
        .contains(GeneratedClassNames.GENERIC_DOUBLE_DOLLAR_MARKER)
        .contains(GeneratedClassNames.BYTE_BUDDY_MARKER)
        .contains(GeneratedClassNames.MOCKITO_MOCK_MARKER);
  }
}
