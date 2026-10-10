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
package ai.narrativetrace.tooling.frameworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrameworkTableDocsTest {

  private static final Path REPO = Path.of(System.getProperty("projectDir", "."));
  private static final String VERSION = System.getProperty("narrativetrace.buildVersion", "0.0.0");

  @Test
  void theIntegrationSectionHasOneTableRowPerFrameworkRow() {
    String section = FrameworkTableDocs.llmsFullSection("1.2.3");
    for (FrameworkRow row : FrameworkTable.ROWS) {
      assertThat(section).contains("| " + row.name() + " |");
    }
    assertThat(section)
        .contains("`config.spring-enabled`")
        .contains("`trap.silent-sink` (existing)")
        .contains("none — runtime-only")
        .contains("`implementation(\"ai.narrativetrace:narrativetrace-spring:1.2.3\")`")
        .contains("`narrativeTrace { scope.set(\"production\"); mode.set(\"spring\") }`")
        .contains("| `ai.narrativetrace:narrativetrace-agent:1.2.3` |");
  }

  /**
   * Each source-wired row's lines appear under the same snippet marker the resource uses, so the
   * build's snippet gate holds this page to the compiled fixture too.
   */
  @Test
  void theIntegrationSectionEmbedsEachSnippetUnderItsFixtureMarker() {
    String section = FrameworkTableDocs.llmsFullSection("1.2.3");
    WiringSnippets.entries()
        .forEach(
            (id, entry) ->
                assertThat(section)
                    .contains(
                        "<!-- snippet: "
                            + entry.fixture()
                            + (entry.region() == null ? "" : " region=" + entry.region())
                            + " -->")
                    .contains(entry.body()));
  }

  @Test
  void theCoveredFrameworksLineNamesEveryRowInTableOrder() {
    String line = FrameworkTableDocs.llmsTxtLine();
    int last = -1;
    for (FrameworkRow row : FrameworkTable.ROWS) {
      int at = line.indexOf(row.name());
      assertThat(at).as(row.id()).isGreaterThan(last);
      last = at;
    }
    assertThat(line).doesNotContain("\n");
  }

  @Test
  void splicingReplacesOnlyWhatIsBetweenTheMarkers() {
    String doc = "before\n<!-- a:begin -->\nold\n<!-- a:end -->\nafter\n";
    assertThat(FrameworkTableDocs.splice(doc, "a", "new\n"))
        .isEqualTo("before\n<!-- a:begin -->\nnew\n<!-- a:end -->\nafter\n");
  }

  @Test
  void splicingIntoADocumentWithoutTheMarkersIsRefused() {
    assertThatThrownBy(() -> FrameworkTableDocs.splice("no markers here", "a", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a:begin");
  }

  /**
   * llms-full.md's integration table and llms.txt's covered-frameworks line render from the table:
   * a row added, renamed or rewired fails here until {@code renderFrameworkTable} runs.
   */
  @Test
  void theCommittedDocsMatchTheTable() throws IOException {
    String full = Files.readString(REPO.resolve(FrameworkTableDocs.LLMS_FULL));
    String txt = Files.readString(REPO.resolve(FrameworkTableDocs.LLMS_TXT));
    assertThat(full)
        .as("run ./gradlew :narrativetrace-tooling:renderFrameworkTable")
        .isEqualTo(FrameworkTableDocs.renderLlmsFull(full, VERSION));
    assertThat(txt)
        .as("run ./gradlew :narrativetrace-tooling:renderFrameworkTable")
        .isEqualTo(FrameworkTableDocs.renderLlmsTxt(txt));
  }

  @Test
  void splicingIsRefusedWhenTheEndMarkerComesFirstOrIsMissing() {
    assertThatThrownBy(
            () -> FrameworkTableDocs.splice("<!-- a:end -->\n<!-- a:begin -->\n", "a", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FrameworkTableDocs.splice("<!-- a:begin -->\nold\n", "a", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void splicingAtTheVeryStartOfTheDocumentWorks() {
    assertThat(FrameworkTableDocs.splice("<!-- a:begin -->\n<!-- a:end -->", "a", "n\n"))
        .isEqualTo("<!-- a:begin -->\nn\n<!-- a:end -->");
  }
}
