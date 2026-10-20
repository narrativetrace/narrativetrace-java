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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-125, in a real JVM: a host that injects a class into the BOOTSTRAP class loader must keep
 * working when that class matches the agent's package filter.
 *
 * <p>This is mockito's inline mock maker, reduced. It appends a jar holding {@code
 * MockMethodDispatcher} with {@link
 * java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch}, and {@code
 * -javaagent:…=packages=org.mockito.*} matched it: the woven class carried {@code INVOKESTATIC
 * ai/narrativetrace/agent/AgentRuntime} and the bootstrap loader cannot resolve that, so every test
 * in {@code SpyAnnotationTest} died with {@code NoClassDefFoundError:
 * ai/narrativetrace/agent/AgentRuntime}. The same command without the agent passed.
 *
 * <p>The reproduction uses the real mechanism rather than {@code -Xbootclasspath/a:} — which would
 * put the class on the boot path too, but at startup instead of from a premain, and the ordering is
 * half of what makes the host's injection interesting.
 *
 * <p>The second half of the assertion is the regression guard: a class the APPLICATION loader
 * defines, under the same filter in the same JVM, must still narrate. "Instrument nothing" would
 * pass the first half on its own.
 */
class BootstrapInjectedClassAttachTest {

  private static final String BOOT_SOURCE =
      """
      package bootfixture;

      public class BootService {
        public String describe(String token) {
          return "boot:" + token;
        }
      }
      """;

  private static final String APPENDER_SOURCE =
      """
      package bootappender;

      import java.lang.instrument.Instrumentation;
      import java.util.jar.JarFile;

      public final class BootstrapAppenderAgent {
        private BootstrapAppenderAgent() {}

        public static void premain(String agentArgs, Instrumentation inst) throws Exception {
          inst.appendToBootstrapClassLoaderSearch(new JarFile(agentArgs));
        }
      }
      """;

  private static final String LAUNCHER_SOURCE =
      """
      package com.ntfixture;

      public class BootstrapMain {
        public static void main(String[] args) throws Exception {
          var type = Class.forName("bootfixture.BootService", true, null);
          System.out.println(
              "loader=" + (type.getClassLoader() == null ? "bootstrap" : type.getClassLoader()));
          var instance = type.getDeclaredConstructor().newInstance();
          var method = type.getMethod("describe", String.class);
          System.out.println("result=" + method.invoke(instance, "A-1"));
          System.out.println("local=" + new BootstrapMain().handleLocal("A-1"));
        }

        String handleLocal(String token) {
          return "local:" + token;
        }
      }
      """;

  @TempDir Path tempDir;

  @Test
  void aClassInjectedIntoTheBootstrapLoaderDoesNotBreakTheHost() throws Exception {
    var bootJar = buildBootJar();
    var appenderJar = buildAppenderJar(bootJar);
    var launcherClasses = compile("BootstrapMain.java", LAUNCHER_SOURCE, "launcher");

    var process = launch(appenderJar, bootJar, launcherClasses);
    var output = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }

    assertThat(finished).as("fixture JVM must terminate; output so far:%n%s", output).isTrue();
    assertThat(output)
        .as("the host's own call must not become a NarrativeTrace linkage error:%n%s", output)
        .doesNotContain("NoClassDefFoundError");
    assertThat(process.exitValue()).as("fixture JVM exit code; output:%n%s", output).isZero();
    assertThat(output)
        .as("the reproduction must actually target the bootstrap loader:%n%s", output)
        .contains("loader=bootstrap")
        .contains("result=boot:A-1");
    assertThat(output)
        .as("the bootstrap-defined class loads uninstrumented, and the agent says so:%n%s", output)
        .contains("cannot instrument bootfixture/BootService")
        .contains("load uninstrumented")
        .doesNotContain("BootService.describe(");
    assertThat(output)
        .as("a class the application loader defines must still be woven:%n%s", output)
        .contains("BootstrapMain.handleLocal(")
        .contains("returned: \"local:A-1\"");
  }

  private Path buildBootJar() throws IOException {
    var classes = compile("BootService.java", BOOT_SOURCE, "boot");
    return writeJar(classes, tempDir.resolve("boot-fixture.jar"), null);
  }

  private Path buildAppenderJar(Path bootJar) throws IOException {
    var classes = compile("BootstrapAppenderAgent.java", APPENDER_SOURCE, "appender");
    assertThat(bootJar).exists();
    return writeJar(
        classes, tempDir.resolve("boot-appender.jar"), "bootappender.BootstrapAppenderAgent");
  }

  private Process launch(Path appenderJar, Path bootJar, Path launcherClasses) throws IOException {
    var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath =
        launcherClasses
            + File.pathSeparator
            + requiredProperty("narrativetrace.test.slf4jProviderJar");
    return new ProcessBuilder(
            List.of(
                javaBinary,
                "-javaagent:" + appenderJar + "=" + bootJar,
                "-javaagent:"
                    + requiredProperty("narrativetrace.test.standaloneJar")
                    + "=packages=bootfixture;com.ntfixture",
                "-Dorg.slf4j.simpleLogger.defaultLogLevel=trace",
                "-cp",
                classpath,
                "com.ntfixture.BootstrapMain"))
        .redirectErrorStream(true)
        .start();
  }

  private Path compile(String fileName, String source, String outputName) throws IOException {
    var sourceDir = Files.createDirectories(tempDir.resolve("src-" + outputName));
    var sourceFile = sourceDir.resolve(fileName);
    Files.writeString(sourceFile, source);
    var classesDir = Files.createDirectories(tempDir.resolve(outputName));
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
    assertThat(result).as("%s compilation exit code", fileName).isZero();
    return classesDir;
  }

  /** The classes directory as a jar; {@code premainClass} makes it an agent jar. */
  private Path writeJar(Path classesDir, Path jarFile, String premainClass) throws IOException {
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (premainClass != null) {
      manifest.getMainAttributes().putValue("Premain-Class", premainClass);
    }
    try (var out = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
      for (var entry : regularFilesUnder(classesDir)) {
        out.putNextEntry(new JarEntry(classesDir.relativize(entry).toString().replace('\\', '/')));
        out.write(Files.readAllBytes(entry));
        out.closeEntry();
      }
    }
    return jarFile;
  }

  private static List<Path> regularFilesUnder(Path root) throws IOException {
    try (var walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile).toList();
    }
  }

  private String requiredProperty(String name) {
    var value = System.getProperty(name);
    assertThat(value).as("%s system property", name).isNotNull();
    return value;
  }
}
