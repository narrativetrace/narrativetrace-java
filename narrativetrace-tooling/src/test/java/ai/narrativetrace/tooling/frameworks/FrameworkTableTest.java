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

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FrameworkTableTest {

  /**
   * The rows Java starts Phase 6 with, in their published order. The ids are stable — they name a
   * row in the doctor's fix lines and in the ports' own tables — so a reorder or a rename is a
   * contract change and has to edit this list.
   */
  @Test
  void listsTheNineRowsInTheirPublishedOrder() {
    assertThat(FrameworkTable.ROWS)
        .extracting(FrameworkRow::id)
        .containsExactly(
            "spring",
            "spring-web",
            "micronaut",
            "servlet",
            "junit4",
            "micrometer",
            "opentelemetry",
            "default-logger",
            "agent");
  }

  /** A deferral names a row that exists; a typo would make the row silently never stand down. */
  @Test
  void aDeferralToAnUnknownRowIsRefused() {
    FrameworkRow typo =
        new FrameworkRow(
            "typo",
            "Typo",
            new Marker("m", List.of(Pattern.compile("x")), List.of("sping")),
            new IntegrationModule(
                List.of("ai.narrativetrace:narrativetrace-x"),
                "implementation",
                List.of("ai.narrativetrace:narrativetrace-x"),
                null),
            new Wiring.OwnerOptIn("o"),
            new CheckBinding.NoCheck("r"),
            FrameworkRow.NO_TIER_B_CASE);
    assertThatThrownBy(() -> FrameworkTable.detected(typo, "x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sping");
  }

  @Test
  void everyDeferralInTheTableNamesARow() {
    FrameworkTable.ROWS.forEach(
        row ->
            row.marker()
                .deferTo()
                .forEach(id -> assertThat(FrameworkTable.row(id)).as(id).isPresent()));
  }
}
