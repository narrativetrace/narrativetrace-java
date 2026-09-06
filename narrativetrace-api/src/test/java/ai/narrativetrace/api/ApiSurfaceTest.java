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
package ai.narrativetrace.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The decision record and the jar must say the same thing.
 *
 * <p>INTENT: {@code documentation/api-surface.md} is the list of types this project has decided to
 * support forever. A type that reaches the jar without a row there is a type nobody decided on, and
 * withdrawing it later costs a major version — so the two are compared mechanically rather than by
 * review.
 *
 * <p><b>@llmNote</b> Nested types are admitted with their enclosing type and deliberately excluded
 * from both sides of the comparison; package-private types are not surface at all.
 */
class ApiSurfaceTest {

  private static final Path RECORD = Path.of("..", "documentation", "api-surface.md");

  private static final Pattern ROW =
      Pattern.compile(
          "^\\| `(ai\\.narrativetrace\\.api\\.[A-Za-z0-9_.]+)` \\| \\d{4}-\\d{2}-\\d{2} \\|");

  @Test
  void everyPublicTypeInTheJarHasADecisionRecordRow() {
    assertThat(publicTypesInJar())
        .as("a public type with no row in api-surface.md is a contract nobody agreed to")
        .isSubsetOf(recordedTypes());
  }

  @Test
  void everyDecisionRecordRowNamesATypeThatStillExists() {
    assertThat(recordedTypes())
        .as("a row for a type that no longer ships is a promise the jar cannot keep")
        .isSubsetOf(publicTypesInJar());
  }

  @Test
  void theDecisionRecordIsNotEmpty() {
    assertThat(recordedTypes()).hasSizeGreaterThan(20);
  }

  /**
   * Read from the main output directory, not from the classpath: the classpath also carries the
   * test-fixtures output, and a fixture is not something the jar promises anyone.
   */
  private static final Path MAIN_CLASSES = Path.of("build", "classes", "java", "main");

  private static Set<String> publicTypesInJar() {
    assertThat(MAIN_CLASSES).as("api main classes must be compiled before this runs").exists();
    var imported = new ClassFileImporter().importPath(MAIN_CLASSES);
    var names = new TreeSet<String>();
    for (JavaClass type : imported) {
      if (type.getModifiers().toString().contains("PUBLIC")
          && !type.isNestedClass()
          && !type.getSimpleName().equals("package-info")
          && !type.getSimpleName().isEmpty()) {
        names.add(type.getName());
      }
    }
    return names;
  }

  private static Set<String> recordedTypes() {
    var names = new TreeSet<String>();
    for (var line : readRecord().split("\n")) {
      var matcher = ROW.matcher(line);
      if (matcher.find()) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }

  private static String readRecord() {
    try {
      return Files.readString(RECORD);
    } catch (IOException e) {
      throw new UncheckedIOException("api-surface.md must be readable from the api module", e);
    }
  }
}
