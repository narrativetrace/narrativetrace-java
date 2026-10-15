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
package ai.narrativetrace.tooling.platforms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Every repository file that is a view of the platform table equals a fresh render of it. The
 * writer is {@link RenderPlatformsMain}; never hand-edit a file this test names.
 */
class PlatformFilesDriftTest {

  static final String CURSOR_RULE = ".cursor/rules/narrativetrace.mdc";

  private static final Path REPO = Path.of(System.getProperty("projectDir", "."));

  static String cursorRule() {
    return RenderPlatformsMain.render();
  }

  @Test
  void theCursorRuleThePluginShipsIsTheTableRowsPointerWithNoStamp() throws IOException {
    Path onDisk = REPO.resolve(CURSOR_RULE);

    assertThat(onDisk).as(CURSOR_RULE + " must exist — run renderPlatforms and commit it").exists();
    assertThat(Files.readString(onDisk)).isEqualTo(cursorRule());
  }

  @Test
  void theCursorPluginManifestPointsAtTheRuleFileThisTestHolds() throws IOException {
    String plugin = Files.readString(REPO.resolve(".cursor-plugin/plugin.json"));

    assertThat(plugin).contains("\"rules\": \"./" + CURSOR_RULE + "\"");
    assertThat(PlatformTable.row("cursor").orElseThrow().pointer().path()).isEqualTo(CURSOR_RULE);
  }

  @Test
  void theDocsPageTableIsTheTableRendered() throws IOException {
    String page = Files.readString(REPO.resolve(PlatformTableDocs.PAGE));

    assertThat(PlatformTableDocs.splice(page))
        .as(PlatformTableDocs.PAGE + " must match a fresh render — run renderPlatforms")
        .isEqualTo(page);
  }
}
