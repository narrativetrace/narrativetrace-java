/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

class GlossaryScanAnnouncementTest {

  @Test
  void namesBothGlossaryFilesUnderTheProjectRoot() {
    var root = new File("/tmp/my-project");

    var message = GlossaryScanAnnouncement.forRoot(root);

    assertThat(message).contains(new File(root, "glossary.json").getAbsolutePath());
    assertThat(message).contains(new File(root, "glossary.md").getAbsolutePath());
    assertThat(message).contains("project root");
  }
}
