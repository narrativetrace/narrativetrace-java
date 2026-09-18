/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bullet 3 of {@link RenderingReadsStateNeverRunsBehaviourTest}: a record in a named module that
 * {@code exports} its package (so its public accessor is legitimately callable) but does not {@code
 * opens} it (so deep reflection into its private backing field is refused).
 *
 * <p><b>@llmNote</b> No module-test idiom exists anywhere else in this repo (the whole tree has no
 * {@code module-info.java}), so this builds the real thing rather than a fake: a tiny module is
 * compiled with {@link javax.tools.JavaCompiler} into a temp directory once, per
 * {@code @BeforeAll}, and loaded into its own {@link ModuleLayer} — the only way to reproduce
 * genuine "exported, not opened" semantics, which the SecurityManager has nothing to do with (that
 * is a capability-check mechanism; module encapsulation is a separate, JPMS-native one). This is
 * why the task's "otherwise simulated" fallback is not used here: the real mechanism was reachable
 * without disproportionate cost.
 *
 * <p>The one existing precedent in this codebase ({@code
 * ValueRendererTest#objectWithModuleEncapsulatedFieldsRendersGracefully}, using {@code
 * java.lang.ref.Cleaner}) cannot stand in: no public JDK 17 type is both a record and lives in a
 * package that is exported without being opened, so a real record fixture has to be built.
 */
class ValueRendererModuleEncapsulationTest {

  private static Path moduleDir;
  private static Class<?> secretClass;

  @BeforeAll
  static void compileAndLoadFixtureModule() throws Exception {
    var sourceDir = Files.createTempDirectory("rendering-rule-module-src");
    var packageDir = sourceDir.resolve("fixture/recordmodule");
    Files.createDirectories(packageDir);
    Files.writeString(
        sourceDir.resolve("module-info.java"),
        "module fixture.recordmodule { exports fixture.recordmodule; }",
        StandardCharsets.UTF_8);
    Files.writeString(
        packageDir.resolve("Secret.java"),
        "package fixture.recordmodule;" + "public record Secret(String value) {}",
        StandardCharsets.UTF_8);

    moduleDir = Files.createTempDirectory("rendering-rule-module-out");
    var compiler = ToolProvider.findFirst("javac").orElseThrow();
    var exitCode =
        compiler.run(
            System.out,
            System.err,
            "-d",
            moduleDir.toString(),
            sourceDir.resolve("module-info.java").toString(),
            packageDir.resolve("Secret.java").toString());
    assertThat(exitCode).as("the fixture module must compile").isZero();

    var finder = ModuleFinder.of(moduleDir);
    var configuration =
        Configuration.resolve(
            finder,
            List.of(ModuleLayer.boot().configuration()),
            ModuleFinder.of(),
            Set.of("fixture.recordmodule"));
    var layer =
        ModuleLayer.boot()
            .defineModulesWithOneLoader(
                configuration, ValueRendererModuleEncapsulationTest.class.getClassLoader());
    secretClass = layer.findLoader("fixture.recordmodule").loadClass("fixture.recordmodule.Secret");
  }

  @AfterAll
  static void cleanUp() throws IOException {
    deleteRecursively(moduleDir);
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths
          .sorted((a, b) -> b.compareTo(a))
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  /**
   * Pending: today's {@code accessor.invoke(record)} calls the record's public accessor method,
   * which needs no "opens" — a public method on a public type in an exported package is ordinary
   * access, not deep reflection — so it reads the real value straight through the module boundary.
   * The target instead reads the backing field via {@link java.lang.reflect.Field#get}, which DOES
   * need deep reflection; against a package that only exports and never opens, {@code
   * Field.setAccessible(true)} throws {@code InaccessibleObjectException}, and the component must
   * render the dedicated {@code <inaccessible>} marker instead of the typed {@code <error: Type>}
   * one every other unreadable member uses — the accessor must never be the fallback.
   */
  @Test
  void aRecordInAnExportedButNotOpenedModuleRendersAnInaccessibleMarkerNeverTheAccessor()
      throws Exception {
    var instance = secretClass.getConstructor(String.class).newInstance("hunter2");
    var renderer = new ValueRenderer();

    var rendered = renderer.render(instance);

    assertThat(rendered).doesNotContain("hunter2");
    assertThat(rendered).contains("<inaccessible>");
  }
}
