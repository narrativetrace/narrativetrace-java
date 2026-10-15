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

import org.junit.jupiter.api.Test;

class PlatformTableTest {

  private static PlatformRow row(String id) {
    return PlatformTable.row(id).orElseThrow();
  }

  /**
   * The rows Phase 8 starts with, in their published order. The ids name a row in the docs page,
   * the doctor's lines and the ports' own tables, so a reorder or a rename is a contract change.
   */
  @Test
  void listsTheFivePlatformsInTheirPublishedOrder() {
    assertThat(PlatformTable.ROWS)
        .extracting(PlatformRow::id)
        .containsExactly("cursor", "replit", "antigravity", "github-copilot", "claude-code");
  }

  @Test
  void cursorIsDetectedByItsDirectoryAndPointedAtByOneRule() {
    PlatformRow cursor = row("cursor");

    assertThat(cursor.markerPaths()).containsExactly(".cursor");
    assertThat(cursor.pointer())
        .isInstanceOf(Pointer.Rule.class)
        .extracting(Pointer::path)
        .isEqualTo(".cursor/rules/narrativetrace.mdc");
    assertThat(((Pointer.Rule) cursor.pointer()).text())
        .contains(".agents/skills")
        .contains("doctor");
    assertThat(cursor.evidenceLane()).isEqualTo("cursor");
  }

  @Test
  void replitIsDetectedByEitherOfItsFilesAndCarriesTheManagedSectionInItsMemoryFile() {
    PlatformRow replit = row("replit");

    assertThat(replit.markerPaths()).containsExactly("replit.md", ".replit");
    assertThat(replit.pointer()).isEqualTo(new Pointer.ManagedSection("replit.md"));
    assertThat(replit.installPath()).contains("GitHub");
    assertThat(replit.evidenceLane()).isEqualTo(PlatformRow.MANUAL);
  }

  /**
   * Antigravity's own project layout is {@code .agents/rules} and {@code .agents/workflows} (legacy
   * {@code .agent/}); {@code .agents/skills} is NOT a marker because {@code init} writes it, so
   * every project that ran {@code init} would "be on Antigravity". No registry install path: {@code
   * agy plugin install} reads only a plugin's top-level {@code skills/} directory, which this
   * repository does not ship.
   */
  @Test
  void antigravityAndCopilotNeedNoPointerBecauseTheyReadTheOpenStandardDirectly() {
    assertThat(row("antigravity").name()).isEqualTo("Antigravity");
    assertThat(row("antigravity").pointer()).isEqualTo(new Pointer.None());
    assertThat(row("antigravity").markerPaths())
        .containsExactly(".agents/rules", ".agents/workflows", ".agent");
    assertThat(row("antigravity").installPath())
        .contains("narrativetrace init")
        .doesNotContain("plugin install");
    assertThat(row("antigravity").agentFinds()).contains(".agents/skills").contains("AGENTS.md");
    assertThat(row("antigravity").evidenceLane()).isEqualTo("antigravity");
    assertThat(row("github-copilot").pointer()).isEqualTo(new Pointer.None());
    assertThat(row("github-copilot").markerPaths())
        .containsExactly(".github/copilot-instructions.md");
  }

  @Test
  void claudeCodeIsTheNativePlatformWithTheClaudeLane() {
    PlatformRow claude = row("claude-code");

    assertThat(claude.markerPaths()).containsExactly(".claude");
    assertThat(claude.pointer()).isEqualTo(new Pointer.None());
    assertThat(claude.evidenceLane()).isEqualTo("claude");
  }

  @Test
  void everyRowCarriesTheDateItsFactsWereVerified() {
    assertThat(PlatformTable.ROWS)
        .allSatisfy(r -> assertThat(r.verified()).isEqualTo("2026-10-10"));
  }

  @Test
  void anUnknownIdIsNoRow() {
    assertThat(PlatformTable.row("cursorx")).isEmpty();
  }
}
