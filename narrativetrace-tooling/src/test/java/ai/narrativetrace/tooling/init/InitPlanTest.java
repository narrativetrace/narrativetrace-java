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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.Action.AppendBlock;
import ai.narrativetrace.tooling.init.Action.AppendLine;
import ai.narrativetrace.tooling.init.Action.CreateFile;
import ai.narrativetrace.tooling.init.Action.DeleteDirectory;
import ai.narrativetrace.tooling.init.Action.DeleteFile;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.catdd.ContractVerifiable;
import ai.narrativetrace.tooling.init.catdd.InvariantCheckExtension;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A plan is the whole of what an install would do, computed before anything is written. These cases
 * pin what an action promises the executor — a complete before and after, never a path outside the
 * project — and what a plan promises a reader: one action per path, and a refusal that says why.
 */
@ExtendWith(InvariantCheckExtension.class)
class InitPlanTest implements ContractVerifiable<InitPlan> {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private InitPlan subject;

  @BeforeEach
  void aPlanToCheckAround() {
    subject = plan(new CreateFile(Path.of("AGENTS.md"), "block\n"));
  }

  @Override
  public InitPlan subject() {
    return subject;
  }

  @Override
  public boolean checkInvariant() {
    return subject == null || subject.invariant();
  }

  private static InitPlan plan(Action... actions) {
    return new InitPlan(COORDINATE, false, List.of(actions));
  }

  // --- what each action promises --------------------------------------------------------------

  @Test
  void aCreatedFileHasNoBefore() {
    CreateFile action = new CreateFile(Path.of("AGENTS.md"), "block\n");

    assertThat(action.before()).isEmpty();
    assertThat(action.after()).isEqualTo("block\n");
    assertThat(action.kind()).isEqualTo("create");
  }

  @Test
  void aReplacementCarriesBothWholeFileTexts() {
    ReplaceBlock action = new ReplaceBlock(Path.of("AGENTS.md"), "old\n", "new\n");

    assertThat(action.before()).isEqualTo("old\n");
    assertThat(action.after()).isEqualTo("new\n");
    assertThat(action.kind()).isEqualTo("replace");
  }

  @Test
  void anAppendComputesItsAfterFromWhatWasThere() {
    AppendBlock action = new AppendBlock(Path.of("AGENTS.md"), "# Title\n", "block\n");

    assertThat(action.after()).isEqualTo("# Title\n\nblock\n");
    assertThat(action.kind()).isEqualTo("append");
  }

  @Test
  void anAppendedLineEndsWithTheFilesOwnLineEnding() {
    AppendLine unix = new AppendLine(Path.of("CLAUDE.md"), "# C\n", "@AGENTS.md");
    AppendLine windows = new AppendLine(Path.of("CLAUDE.md"), "# C\r\n", "@AGENTS.md");

    assertThat(unix.after()).isEqualTo("# C\n\n@AGENTS.md\n");
    assertThat(windows.after()).isEqualTo("# C\r\n\r\n@AGENTS.md\r\n");
    assertThat(unix.kind()).isEqualTo("append-line");
  }

  @Test
  void aDeletionEndsWithNothing() {
    DeleteFile file = new DeleteFile(Path.of("AGENTS.md"), "gone\n");
    DeleteDirectory directory = new DeleteDirectory(Path.of(".agents/skills/doctor"));

    assertThat(file.after()).isEmpty();
    assertThat(file.before()).isEqualTo("gone\n");
    assertThat(file.kind()).isEqualTo("delete");
    assertThat(directory.kind()).isEqualTo("delete-directory");
  }

  @Test
  void aRefusalCarriesItsReason() {
    Refuse refusal = new Refuse(Path.of("AGENTS.md"), "two blocks");

    assertThat(refusal.reason()).isEqualTo("two blocks");
    assertThat(refusal.kind()).isEqualTo("refuse");
  }

  @Test
  void refusesAnActionOnAPathOutsideTheProject() {
    assertThatThrownBy(() -> new CreateFile(Path.of("/etc/passwd"), "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("project-relative");
    assertThatThrownBy(() -> new CreateFile(Path.of("../outside.md"), "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("project-relative");
    assertThatThrownBy(() -> new DeleteDirectory(Path.of(".agents/../../x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("project-relative");
  }

  @Test
  void refusesARefusalWithoutAReason() {
    assertThatThrownBy(() -> new Refuse(Path.of("AGENTS.md"), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void refusesAnActionWithoutAPathOrContent() {
    assertThatThrownBy(() -> new CreateFile(null, "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
    assertThatThrownBy(() -> new CreateFile(Path.of("AGENTS.md"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text");
  }

  // --- what a plan promises ------------------------------------------------------------------

  @Test
  void anEmptyPlanIsWhatANothingToDoRunLooksLike() {
    InitPlan empty = plan();

    assertThat(empty.isEmpty()).isTrue();
    assertThat(empty.hasRefusals()).isFalse();
    assertThat(empty.exitCode()).isZero();
    assertThat(empty.invariant()).isTrue();
  }

  @Test
  void aRefusalMakesTheRunExitOne() {
    InitPlan refused = plan(new Refuse(Path.of("AGENTS.md"), "two blocks"));

    assertThat(refused.hasRefusals()).isTrue();
    assertThat(refused.refusals()).hasSize(1);
    assertThat(refused.exitCode()).isEqualTo(1);
  }

  @Test
  void aDryRunAlwaysExitsZeroEvenWithARefusal() {
    InitPlan dry = new InitPlan(COORDINATE, true, List.of(new Refuse(Path.of("AGENTS.md"), "no")));

    assertThat(dry.dryRun()).isTrue();
    assertThat(dry.hasRefusals()).isTrue();
    assertThat(dry.exitCode()).isZero();
  }

  @Test
  void refusesTwoActionsOnOnePath() {
    assertThatThrownBy(
            () ->
                plan(
                    new CreateFile(Path.of("AGENTS.md"), "a"),
                    new ReplaceBlock(Path.of("AGENTS.md"), "a", "b")))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void refusesAPlanWithoutACarrier() {
    assertThatThrownBy(() -> new InitPlan(" ", false, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carrier");
    assertThatThrownBy(() -> new InitPlan(COORDINATE, false, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actions");
  }

  @Test
  void keepsItsActionsImmutable() {
    InitPlan onlyOne = plan(new CreateFile(Path.of("AGENTS.md"), "a"));

    assertThatThrownBy(() -> onlyOne.actions().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // --- options -------------------------------------------------------------------------------

  @Test
  void theDefaultOptionsWriteNothingItWasNotAskedTo() {
    InitOptions options = InitOptions.defaults();

    assertThat(options.dryRun()).isFalse();
    assertThat(options.writeExisting()).isFalse();
    assertThat(options.force()).isFalse();
    assertThat(options.scope()).isEqualTo(InitOptions.Scope.BOTH);
    assertThat(options.vendorClaude()).isEqualTo(InitOptions.Vendor.AUTO);
  }

  @Test
  void eachScopeKnowsWhatItCovers() {
    assertThat(InitOptions.Scope.BOTH.includesSkills()).isTrue();
    assertThat(InitOptions.Scope.BOTH.includesAgentsMd()).isTrue();
    assertThat(InitOptions.Scope.SKILLS.includesSkills()).isTrue();
    assertThat(InitOptions.Scope.SKILLS.includesAgentsMd()).isFalse();
    assertThat(InitOptions.Scope.AGENTS_MD.includesSkills()).isFalse();
    assertThat(InitOptions.Scope.AGENTS_MD.includesAgentsMd()).isTrue();
  }

  @Test
  void changesOneOptionAtATime() {
    InitOptions options =
        InitOptions.defaults()
            .withDryRun(true)
            .withWriteExisting(true)
            .withForce(true)
            .withScope(InitOptions.Scope.SKILLS)
            .withVendorClaude(InitOptions.Vendor.OFF);

    assertThat(options.dryRun()).isTrue();
    assertThat(options.writeExisting()).isTrue();
    assertThat(options.force()).isTrue();
    assertThat(options.scope()).isEqualTo(InitOptions.Scope.SKILLS);
    assertThat(options.vendorClaude()).isEqualTo(InitOptions.Vendor.OFF);
  }

  @Test
  void refusesOptionsWithoutAScopeOrAVendorRule() {
    assertThatThrownBy(() -> InitOptions.defaults().withScope(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scope");
    assertThatThrownBy(() -> InitOptions.defaults().withVendorClaude(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vendor");
  }
}
