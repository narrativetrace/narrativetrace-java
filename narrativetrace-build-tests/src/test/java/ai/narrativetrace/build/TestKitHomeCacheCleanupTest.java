/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Gradle's periodic cache cleanup reclaims an artifact file unread for 30 days even when its
 * resolved metadata descriptor is still read on every build: a plugin marker POM is parsed into the
 * descriptor once and never read again, so the file ages out from under a descriptor that stays
 * warm. The next resolution finds the descriptor, misses the file, and falls through to the network
 * — unreachable for an offline nested build. Driving an actual 30-day cleanup inside a test would
 * mean faking Gradle's own access journal, so this pins the wiring instead: the TestKit home this
 * module's nested builds use must carry an init script that disables cleanup, the one the real
 * Gradle home already carries.
 */
class TestKitHomeCacheCleanupTest {

  @Test
  void testKitHomeDisablesGradleCacheCleanup() throws IOException {
    var testKitHome = new File(System.getProperty("org.gradle.testkit.dir"));
    var initScript = new File(testKitHome, "init.d/no-cache-cleanup.gradle.kts");

    assertThat(initScript).as("%s", initScript).exists();
    assertThat(Files.readString(initScript.toPath()))
        .contains("beforeSettings { caches { cleanup.set(Cleanup.DISABLED) } }");
  }
}
