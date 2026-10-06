/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The one line the plugin prints when Gradle's own {@code --dry-run} swallowed an installer task.
 * It is the only thing a person sees in that case, so it has to name both the cause and the command
 * that does work.
 */
class InstallerTasksHintTest {

  @Test
  void theHintNamesTheTaskTheBuiltInFlagSkippedAndTheFlagThatWorks() {
    String hint = InstallerTasks.dryRunHint("narrativetraceInit");

    assertThat(hint)
        .contains("--dry-run is Gradle's own flag")
        .contains("narrativetraceInit will not run")
        .contains("./gradlew narrativetraceInit --diff");
  }

  @Test
  void theHintIsAboutWhicheverTaskWasAskedFor() {
    assertThat(InstallerTasks.dryRunHint("narrativetraceUninstall"))
        .contains("narrativetraceUninstall --diff")
        .doesNotContain("narrativetraceInit");
  }
}
