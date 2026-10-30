/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Attaching the agent must never make a host's own class be defined twice (B-213).
 *
 * <p>INTENT: Frame computation runs <em>inside</em> the JVM's {@code defineClass} for the class
 * being woven — the class-file load hook is posted from there. A transform that asks a loader to
 * resolve a type therefore re-enters that loader for a name whose definition is still in flight:
 * {@code findLoadedClass} says "not loaded", the loader reads the class file and defines it, and
 * the define the host started then fails with {@code LinkageError: loader 'app' attempted duplicate
 * … definition}. Nothing in the host's stack names the agent, because the inner define has already
 * unwound by the time the error surfaces.
 *
 * <p>Measured on the published 0.5.3 agent against fastjson2 2.0.26: weaving {@code JSONWriter}
 * made ASM ask for the common supertype of {@code JSONWriter} and {@code JSONWriterUTF16}, and that
 * one call defined {@code JSONWriter} from the class path; every test of the subject's suite then
 * errored. This fixture is that shape in miniature — an abstract class whose own static factory
 * merges two of its subclasses — and it needs neither Kotlin nor MockK: any load of the woven class
 * is enough, reflective or not.
 *
 * <p><b>@llmNote</b> Only a real {@code -javaagent} attach reproduces this. Calling {@link
 * ClassTransformer} directly runs the transform <em>outside</em> any define, so the re-entrant load
 * resolves harmlessly; the companion unit test ({@code DefiningLoaderFrameComputationTest}) pins
 * the invariant that makes it impossible instead — a transform loads no class at all.
 */
class AgentAttachDuplicateDefinitionTest {

  private static final String WRITER_SOURCE =
      """
      package com.ntmerge;

      /** Abstract on purpose: the JVM's duplicate-definition message names the kind. */
      public abstract class Writer {

        public static Writer of(boolean wide) {
          Writer chosen;
          if (wide) {
            chosen = new WideWriter();
          } else {
            chosen = new NarrowWriter();
          }
          return chosen;
        }

        public abstract String describe();
      }
      """;

  private static final String WIDE_SOURCE =
      """
      package com.ntmerge;

      public final class WideWriter extends Writer {
        @Override
        public String describe() {
          return "wide";
        }
      }
      """;

  private static final String NARROW_SOURCE =
      """
      package com.ntmerge;

      public final class NarrowWriter extends Writer {
        @Override
        public String describe() {
          return "narrow";
        }
      }
      """;

  private static final String MAIN_SOURCE =
      """
      package com.ntmerge;

      public final class FixtureMain {

        public static void main(String[] args) {
          try {
            var loaded =
                Class.forName("com.ntmerge.Writer", false, FixtureMain.class.getClassLoader());
            System.out.println("LOADED " + loaded.getName());
          } catch (Throwable failure) {
            System.out.println("LOAD_FAILED " + failure);
          }
          try {
            System.out.println("OF_RETURNED " + Writer.of(true).describe());
          } catch (Throwable failure) {
            System.out.println("OF_FAILED " + failure);
          }
        }
      }
      """;

  @TempDir Path tempDir;

  @Test
  void aWovenClassIsNotDefinedASecondTimeByItsOwnFrameComputation() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("the host's own load of the woven class must succeed:%n%s", output)
        .contains("LOADED com.ntmerge.Writer")
        .doesNotContain("LOAD_FAILED")
        .doesNotContain("duplicate");
  }

  @Test
  void theWovenMethodWhoseFramesWereComputedStillRunsAndVerifies() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("computed frames must satisfy the real verifier, not merely be written:%n%s", output)
        .contains("OF_RETURNED wide")
        .doesNotContain("OF_FAILED");
  }

  @Test
  void theFixtureIsActuallyWovenSoTheFixCannotBeToSkipInstrumentation() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("the merging method itself must narrate, or this test proves nothing:%n%s", output)
        .contains("Writer.of(wide: true)");
  }

  private String runFixtureUnderAgent() throws Exception {
    var process = launchFixtureJvm(compileFixture());
    var output = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("fixture JVM must terminate; output so far:%n%s", output).isTrue();
    assertThat(process.exitValue()).as("fixture JVM exit code; output:%n%s", output).isZero();
    return output;
  }

  private Path compileFixture() throws IOException {
    var sources = Files.createDirectories(tempDir.resolve("src"));
    var classesDir = Files.createDirectories(tempDir.resolve("classes"));
    var files =
        new String[][] {
          {"Writer.java", WRITER_SOURCE},
          {"WideWriter.java", WIDE_SOURCE},
          {"NarrowWriter.java", NARROW_SOURCE},
          {"FixtureMain.java", MAIN_SOURCE}
        };
    var arguments = new ArrayList<>(List.of("-parameters", "-d", classesDir.toString()));
    for (var file : files) {
      var path = sources.resolve(file[0]);
      Files.writeString(path, file[1]);
      arguments.add(path.toString());
    }
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, arguments.toArray(String[]::new));
    assertThat(result).as("fixture compilation exit code").isZero();
    return classesDir;
  }

  private static Process launchFixtureJvm(Path fixtureClassesDir) throws IOException {
    var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return new ProcessBuilder(
            javaBinary,
            "-javaagent:"
                + requiredProperty("narrativetrace.test.standaloneJar")
                + "=packages=com.ntmerge",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=trace",
            "-cp",
            fixtureClassesDir
                + File.pathSeparator
                + requiredProperty("narrativetrace.test.slf4jProviderJar"),
            "com.ntmerge.FixtureMain")
        .redirectErrorStream(true)
        .start();
  }

  private static String requiredProperty(String name) {
    var value = System.getProperty(name);
    assertThat(value).as("%s system property", name).isNotNull();
    return value;
  }
}
