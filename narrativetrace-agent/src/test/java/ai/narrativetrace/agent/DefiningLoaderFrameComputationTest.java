/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * COMPUTE_FRAMES must resolve types through the class's DEFINING loader, not the agent's own.
 *
 * <p>Under an app server (WildFly spike, plan §Spike findings) the transformed class references
 * types the agent's loader cannot see — deployment neighbours and container APIs — and any method
 * whose frames merge such types then fails to instrument; the JVM swallows the failure and the
 * class silently loads uninstrumented. This test rebuilds that shape in miniature: classes compiled
 * at test runtime into a temp dir exist only in a child URLClassLoader, never on the test
 * classpath.
 */
class DefiningLoaderFrameComputationTest {

  @TempDir static Path classesDir;

  private static byte[] pickerBytes;
  private static URLClassLoader definingLoader;

  @BeforeAll
  static void compileHierarchyVisibleOnlyToChildLoader() throws IOException {
    // Picker.pick merges Circle/Square into a Shape local — COMPUTE_FRAMES must load all three.
    compile(
        "Shape", //
        "package hidden;",
        "public interface Shape {}");
    compile(
        "Circle", //
        "package hidden;",
        "public class Circle implements Shape {}");
    compile(
        "Square", //
        "package hidden;",
        "public class Square implements Shape {}");
    compile(
        "Picker",
        "package hidden;",
        "public class Picker {",
        "  public Shape pick(boolean round) {",
        "    Shape s = round ? new Circle() : new Square();",
        "    return s;",
        "  }",
        "}");
    pickerBytes = Files.readAllBytes(classesDir.resolve("hidden/Picker.class"));
    definingLoader =
        new URLClassLoader(
            new URL[] {classesDir.toUri().toURL()},
            DefiningLoaderFrameComputationTest.class.getClassLoader());
  }

  @Test
  void transformsClassWhoseTypesOnlyItsDefiningLoaderSees() {
    var transformed = ClassTransformer.transform(pickerBytes, "hidden/Picker", definingLoader);

    assertThat(transformed).isNotNull().isNotEqualTo(pickerBytes);
  }

  @Test
  void agentLoaderAloneCannotComputeThoseFrames() {
    // Pins the failure mode the defining loader fixes; if this starts passing, the
    // defining-loader plumbing has become untestable this way — find a new probe.
    assertThatThrownBy(() -> ClassTransformer.transform(pickerBytes, "hidden/Picker"))
        .isInstanceOf(TypeNotPresentException.class);
  }

  private static void compile(String className, String... sourceLines) throws IOException {
    var source = classesDir.resolve("hidden").resolve(className + ".java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, String.join("\n", sourceLines));
    var compiler = ToolProvider.getSystemJavaCompiler();
    int result =
        compiler.run(
            null,
            null,
            null,
            "-d",
            classesDir.toString(),
            "-cp",
            classesDir.toString(),
            "-parameters",
            source.toString());
    assertThat(result).as("javac exit code for %s", className).isZero();
  }
}
