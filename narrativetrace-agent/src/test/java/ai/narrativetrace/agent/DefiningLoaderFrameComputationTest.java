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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * COMPUTE_FRAMES must resolve types through the class's DEFINING loader, not the agent's own.
 *
 * <p>Under an app server (WildFly spike) the transformed class references types the agent's loader
 * cannot see — deployment neighbours and container APIs — and any method whose frames merge such
 * types then fails to instrument; the JVM swallows the failure and the class silently loads
 * uninstrumented. This test rebuilds that shape in miniature: classes compiled at test runtime into
 * a temp dir exist only in a child URLClassLoader, never on the test classpath.
 *
 * <p>"Resolve through the defining loader" means read its class FILES, never ask it to load a class
 * (B-213). The transform runs inside the JVM's own {@code defineClass} for the woven class, so a
 * load request there re-enters the loader for a name whose definition is in flight and the host's
 * define fails as a duplicate. {@link #aTransformNeverAsksTheDefiningLoaderToLoadAClass} is that
 * invariant; {@code AgentAttachDuplicateDefinitionTest} is the same defect under a real attach.
 */
class DefiningLoaderFrameComputationTest {

  @TempDir static Path classesDir;

  private static byte[] pickerBytes;
  private RecordingClassLoader definingLoader;

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
  }

  /** A loader per test: the recorded load attempts of one test must not reach another. */
  @BeforeEach
  void freshDefiningLoader() throws IOException {
    definingLoader =
        new RecordingClassLoader(
            classesDir.toUri().toURL(), DefiningLoaderFrameComputationTest.class.getClassLoader());
  }

  @AfterEach
  void closeDefiningLoader() throws IOException {
    definingLoader.close();
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

  @Test
  void aTransformNeverAsksTheDefiningLoaderToLoadAClass() {
    ClassTransformer.transform(pickerBytes, "hidden/Picker", definingLoader);

    assertThat(definingLoader.loadAttempts())
        .as("a transform runs inside the host's defineClass — loading from there defines twice")
        .isEmpty();
  }

  /** A {@link URLClassLoader} that records every class it is asked to load, and still loads it. */
  private static final class RecordingClassLoader extends URLClassLoader {

    private final List<String> attempts = new CopyOnWriteArrayList<>();

    RecordingClassLoader(URL classes, ClassLoader parent) {
      super(new URL[] {classes}, parent);
    }

    List<String> loadAttempts() {
      return List.copyOf(attempts);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      attempts.add(name);
      return super.loadClass(name, resolve);
    }
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
