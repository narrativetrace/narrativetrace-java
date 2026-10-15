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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlatformTableDocsTest {

  @Test
  void theTableHasOneRowPerPlatformInTableOrderUnderOneHeader() {
    String table = PlatformTableDocs.table();

    assertThat(table)
        .startsWith(
            "| Platform | How a stranger gets the skills | What the agent finds after `init` |"
                + " Evidence | Verified |\n|---|---|---|---|---|\n");
    int last = -1;
    for (PlatformRow row : PlatformTable.ROWS) {
      int at = table.indexOf("| " + row.name() + " |");
      assertThat(at).as(row.id()).isGreaterThan(last);
      last = at;
      assertThat(table)
          .contains(row.installPath())
          .contains(row.agentFinds())
          .contains(row.verified());
    }
    assertThat(table.lines().count()).isEqualTo(2 + PlatformTable.ROWS.size());
  }

  @Test
  void aRowNamesItsEvidenceLaneOrSaysItIsAManualTrial() {
    String table = PlatformTableDocs.table();

    assertThat(table).contains("Tier B lane `cursor`").contains("Tier B lane `antigravity`");
    assertThat(table).contains("manual trial");
  }

  @Test
  void noCellBreaksTheTableWithAPipeOrANewline() {
    PlatformTableDocs.table()
        .lines()
        .skip(2)
        .forEach(line -> assertThat(line.split("\\|", -1)).hasSize(7));
  }

  @Test
  void splicesTheTableBetweenItsMarkersAndNowhereElse() {
    String page =
        "before\n<!-- platform-table:begin -->\nstale\n<!-- platform-table:end -->\nafter\n";

    String rendered = PlatformTableDocs.splice(page);

    assertThat(rendered)
        .startsWith("before\n<!-- platform-table:begin -->\n| Platform |")
        .endsWith("<!-- platform-table:end -->\nafter\n")
        .doesNotContain("stale");
    assertThat(PlatformTableDocs.splice(rendered)).isEqualTo(rendered);
  }

  @Test
  void aPageWithoutTheMarkersIsRefused() {
    assertThatThrownBy(() -> PlatformTableDocs.splice("no markers here"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("platform-table");
  }
}
