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

class ClarityScanArgumentsTest {

  @Test
  void namesTheClassesOutputAndGlossaryDirectories() {
    var args =
        ClarityScanArguments.forScan(
            new File("build/classes/java/main"),
            new File("build/narrativetrace"),
            new File("repo-root"));

    assertThat(args)
        .containsSequence("--classes-dir", new File("build/classes/java/main").getAbsolutePath())
        .containsSequence("--output-dir", new File("build/narrativetrace").getAbsolutePath())
        .containsSequence("--glossary-dir", new File("repo-root").getAbsolutePath());
  }

  @Test
  void pointsTheGlossaryAtTheRepositoryRootNotTheSubproject() {
    var args =
        ClarityScanArguments.forScan(
            new File("app/build/classes/java/main"),
            new File("app/build/narrativetrace"),
            new File("repo-root"));

    assertThat(args.get(args.indexOf("--glossary-dir") + 1))
        .isEqualTo(new File("repo-root").getAbsolutePath());
  }

  @Test
  void usesAbsolutePathsSoTheWorkingDirectoryOfTheForkedJvmCannotMatter() {
    var args = ClarityScanArguments.forScan(new File("a"), new File("b"), new File("c"));

    assertThat(args)
        .filteredOn(arg -> !arg.startsWith("--"))
        .allSatisfy(path -> assertThat(new File(path).isAbsolute()).isTrue());
  }
}
