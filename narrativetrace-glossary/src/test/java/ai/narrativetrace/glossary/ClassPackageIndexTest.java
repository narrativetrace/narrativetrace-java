/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPackageIndexTest {

  @TempDir Path tempDir;

  private Path classFile(String relativePath) throws IOException {
    var file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.createFile(file);
    return file;
  }

  @Test
  void resolvesSimpleClassNameToItsPackage() throws IOException {
    classFile("com/acme/billing/OverdraftService.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("OverdraftService")).isEqualTo("com.acme.billing");
  }

  @Test
  void ambiguousSimpleNameResolvesToUnknown() throws IOException {
    classFile("com/acme/billing/Account.class");
    classFile("com/acme/inventory/Account.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("Account")).isNull();
  }

  @Test
  void skipsPackageInfoModuleInfoAndAnonymousClasses() throws IOException {
    classFile("com/acme/billing/package-info.class");
    classFile("module-info.class");
    classFile("com/acme/billing/OverdraftService$1.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("package-info")).isNull();
    assertThat(index.apply("module-info")).isNull();
    assertThat(index.apply("OverdraftService$1")).isNull();
  }

  @Test
  void nestedClassIndexesUnderItsInnerSimpleName() throws IOException {
    classFile("com/acme/billing/OverdraftService$Builder.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("Builder")).isEqualTo("com.acme.billing");
  }

  @Test
  void fromClasspathKeepsOnlyExistingDirectories() throws IOException {
    classFile("com/acme/billing/OverdraftService.class");
    var classpath =
        String.join(
            java.io.File.pathSeparator,
            tempDir.toString(),
            tempDir.resolve("library.jar").toString(),
            tempDir.resolve("does-not-exist").toString());

    var index = ClassPackageIndex.fromClasspath(classpath);

    assertThat(index.apply("OverdraftService")).isEqualTo("com.acme.billing");
  }

  @Test
  void defaultPackageClassResolvesToEmptyPackage() throws IOException {
    classFile("Standalone.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("Standalone")).isEmpty();
  }

  @Test
  void unknownNameResolvesToNull() {
    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.apply("NeverSeen")).isNull();
  }

  @Test
  void guardsRejectNullArguments() {
    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThatThrownBy(() -> ClassPackageIndex.fromClasspath(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClassPackageIndex.fromDirectories(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> index.apply(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invariantHoldsForConstructedIndex() throws IOException {
    classFile("com/acme/billing/OverdraftService.class");
    classFile("com/acme/billing/Account.class");
    classFile("com/acme/inventory/Account.class");

    var index = ClassPackageIndex.fromDirectories(List.of(tempDir));

    assertThat(index.invariant()).isTrue();
  }
}
