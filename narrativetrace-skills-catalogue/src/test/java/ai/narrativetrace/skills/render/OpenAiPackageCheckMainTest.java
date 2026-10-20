/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAiPackageCheckMainTest {

  private static Path zipOf(Path staging, Path target) {
    try (var out = new ZipOutputStream(Files.newOutputStream(target));
        var walk = Files.walk(staging)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        out.putNextEntry(new ZipEntry(staging.relativize(file).toString().replace('\\', '/')));
        out.write(Files.readAllBytes(file));
        out.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return target;
  }

  @Test
  void passesOnAPackageMatchingTheLayout(@TempDir Path repoRoot, @TempDir Path staging)
      throws IOException {
    OpenAiPackageMainTestSupport.seedSources(repoRoot);
    String version = "1.2.3";
    OpenAiPackageMain.stage(repoRoot, staging, version);
    Path zip = zipOf(staging, repoRoot.resolve("package.zip"));

    assertThatCode(() -> OpenAiPackageCheckMain.main(new String[] {zip.toString(), version}))
        .doesNotThrowAnyException();
  }

  @Test
  void mainThrowsWhenThePackageFailsACheck(@TempDir Path repoRoot, @TempDir Path staging)
      throws IOException {
    OpenAiPackageMainTestSupport.seedSources(repoRoot);
    OpenAiPackageMain.stage(repoRoot, staging, "1.2.3");
    Path zip = zipOf(staging, repoRoot.resolve("package.zip"));

    assertThatThrownBy(() -> OpenAiPackageCheckMain.main(new String[] {zip.toString(), "9.9.9"}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not equal");
  }
}
