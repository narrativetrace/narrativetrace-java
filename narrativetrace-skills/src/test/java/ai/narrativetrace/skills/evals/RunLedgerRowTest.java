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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunLedgerRowTest {

  @Test
  void constructorRejectsEachBlankFieldOrNullPlatform() {
    assertThatThrownBy(() -> new RunLedgerRow(" ", "s", "c", Platform.CLAUDE, "m", 1, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RunLedgerRow("d", " ", "c", Platform.CLAUDE, "m", 1, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RunLedgerRow("d", "s", " ", Platform.CLAUDE, "m", 1, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RunLedgerRow("d", "s", "c", null, "m", 1, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RunLedgerRow("d", "s", "c", Platform.CLAUDE, " ", 1, true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resultIsPassOrFail() {
    assertThat(new RunLedgerRow("d", "s", "c", Platform.CLAUDE, "m", 1, true).result())
        .isEqualTo("pass");
    assertThat(new RunLedgerRow("d", "s", "c", Platform.CLAUDE, "m", 1, false).result())
        .isEqualTo("fail");
  }

  @Test
  void jsonRoundTripsThroughToJsonLineAndParseJsonLine() {
    RunLedgerRow row =
        new RunLedgerRow(
            "2026-09-13T00:00:00Z",
            "narrativetrace-doctor",
            "happy-path",
            Platform.CODEX,
            "mini",
            2,
            false);
    String json = row.toJsonLine();
    assertThat(json)
        .isEqualTo(
            "{\"date\":\"2026-09-13T00:00:00Z\",\"skill\":\"narrativetrace-doctor\",\"case\":"
                + "\"happy-path\",\"platform\":\"codex\",\"model\":\"mini\",\"trial\":2,\"result\":\"fail\"}");
    assertThat(RunLedgerRow.parseJsonLine(json)).isEqualTo(row);
  }

  @Test
  void jsonRoundTripsEscapedQuotesAndBackslashes() {
    RunLedgerRow row =
        new RunLedgerRow("d", "skill \"quoted\" \\ name", "c", Platform.CLAUDE, "m", 1, true);
    assertThat(RunLedgerRow.parseJsonLine(row.toJsonLine())).isEqualTo(row);
  }

  @Test
  void parseJsonLineRejectsAMissingFieldOrUnknownPlatform() {
    assertThatThrownBy(() -> RunLedgerRow.parseJsonLine("{\"date\":\"d\"}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                RunLedgerRow.parseJsonLine(
                    "{\"date\":\"d\",\"skill\":\"s\",\"case\":\"c\",\"platform\":\"chatgpt\","
                        + "\"model\":\"m\",\"trial\":1,\"result\":\"pass\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseJsonlSkipsBlankLinesAndParsesEveryRow() {
    String content =
        "\n"
            + new RunLedgerRow("d1", "s", "c", Platform.CLAUDE, "m", 1, true).toJsonLine()
            + "\n\n"
            + new RunLedgerRow("d2", "s", "c", Platform.CODEX, "m", 2, false).toJsonLine()
            + "\n";
    List<RunLedgerRow> rows = RunLedgerRow.parseJsonl(content);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).date()).isEqualTo("d1");
    assertThat(rows.get(1).date()).isEqualTo("d2");
  }

  @Test
  void parseJsonlOfEmptyContentIsEmpty() {
    assertThat(RunLedgerRow.parseJsonl("")).isEmpty();
    assertThat(RunLedgerRow.parseJsonl("\n\n")).isEmpty();
  }
}
