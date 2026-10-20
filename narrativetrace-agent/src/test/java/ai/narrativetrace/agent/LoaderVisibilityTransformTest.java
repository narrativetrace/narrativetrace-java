/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The instrumentation precondition: a class may only be woven when the loader that defines it can
 * resolve {@link AgentRuntime}.
 *
 * <p>Instrumented bytecode carries a compiled-in {@code INVOKESTATIC
 * ai/narrativetrace/agent/AgentRuntime} at every hook, so weaving a class whose loader cannot see
 * that type turns the agent into a {@link NoClassDefFoundError} inside the host's own call — which
 * is exactly what mockito's inline mock maker hit: it injects {@code MockMethodDispatcher} into the
 * BOOTSTRAP loader, and {@code packages=org.mockito.*} matched it (B-125).
 *
 * <p>The rule is stated over loader <em>visibility</em>, never over {@code loader == null}: an
 * isolated loader with no parent delegation is the same defect without any bootstrap involved.
 */
class LoaderVisibilityTransformTest {

  private static final String SAMPLE_RESOURCE = "ai/narrativetrace/agent/sample/Calculator.class";
  private static final String SAMPLE_CLASS = "ai/narrativetrace/agent/sample/Calculator";
  private static final String OTHER_SAMPLE_CLASS = "ai/narrativetrace/agent/sample/LineService";

  private NarrativeClassFileTransformer transformer() {
    return new NarrativeClassFileTransformer(
        AgentConfig.parse("packages=ai.narrativetrace.agent.sample"));
  }

  private byte[] sampleBytes() throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream(SAMPLE_RESOURCE)) {
      return stream.readAllBytes();
    }
  }

  @Test
  void aBootstrapDefinedClassLoadsUninstrumented() throws Exception {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      var result = transformer().transform(null, SAMPLE_CLASS, null, null, sampleBytes());

      assertThat(result).as("the bootstrap loader can never resolve AgentRuntime").isNull();
    } finally {
      System.setErr(originalErr);
    }
    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("cannot instrument " + SAMPLE_CLASS)
        .contains("bootstrap class loader")
        .contains("load uninstrumented");
  }

  @Test
  void anIsolatedLoaderWithNoParentDelegationLoadsUninstrumented() throws Exception {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (var isolated = new URLClassLoader(new URL[0], null)) {
      var result = transformer().transform(isolated, SAMPLE_CLASS, null, null, sampleBytes());

      assertThat(result).as("an isolated loader cannot resolve AgentRuntime either").isNull();
    } finally {
      System.setErr(originalErr);
    }
    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("cannot instrument " + SAMPLE_CLASS)
        .contains("load uninstrumented");
  }

  @Test
  void theLoaderThatCanSeeTheRuntimeIsInstrumented() throws Exception {
    var original = sampleBytes();

    var result =
        transformer().transform(getClass().getClassLoader(), SAMPLE_CLASS, null, null, original);

    assertThat(result).isNotNull().isNotEqualTo(original);
  }

  /**
   * Resolution is not identity (B-125 follow-up): a loader that defines its OWN copy of {@code
   * AgentRuntime} resolves the name, but weaving would link to that copy — a runtime nothing
   * initialised, tracing and narrating nothing. OpenTelemetry's {@code IgnoredClassLoadersMatcher}
   * and Datadog's {@code ClassLoaderMatchers.incompatibleClassLoader} both require the resolved
   * class to BE the agent's own; this class now matches that rule.
   */
  @Test
  void anIsolatedLoaderCarryingItsOwnCopyOfAgentRuntimeIsRefused(@TempDir Path tempDir)
      throws Exception {
    var packageDir = tempDir.resolve("ai/narrativetrace/agent");
    Files.createDirectories(packageDir);
    try (var runtimeBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/AgentRuntime.class")) {
      Files.write(packageDir.resolve("AgentRuntime.class"), runtimeBytes.readAllBytes());
    }
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (var ownCopy = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
      var result = transformer().transform(ownCopy, SAMPLE_CLASS, null, null, sampleBytes());

      assertThat(result)
          .as("a second AgentRuntime would link but narrate into an uninitialised runtime")
          .isNull();
    } finally {
      System.setErr(originalErr);
    }
    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("cannot instrument " + SAMPLE_CLASS)
        .contains("load uninstrumented");
  }

  @Test
  void aChildOfTheAgentsOwnLoaderIsInstrumented() throws Exception {
    var original = sampleBytes();
    try (var delegating = new URLClassLoader(new URL[0], getClass().getClassLoader())) {
      var result = transformer().transform(delegating, SAMPLE_CLASS, null, null, original);

      assertThat(result).as("delegation reaches AgentRuntime, so weaving links").isNotNull();
    }
  }

  /**
   * A hot loader must not flood stderr: the verdict is cached per loader and the diagnostic is
   * printed once for it, however many of its classes match the package filter.
   */
  @Test
  void anInvisibleLoaderIsReportedOncePerLoaderNotOncePerClass() throws Exception {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (var isolated = new URLClassLoader(new URL[0], null)) {
      var subject = transformer();
      subject.transform(isolated, SAMPLE_CLASS, null, null, sampleBytes());
      subject.transform(isolated, OTHER_SAMPLE_CLASS, null, null, sampleBytes());
    } finally {
      System.setErr(originalErr);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8).lines())
        .as("one line per loader, not one per class")
        .hasSize(1);
  }

  /**
   * Two invisible loaders are two facts, so each is reported: the cache suppresses repetition, it
   * does not suppress a second loader the operator has not been told about yet.
   */
  @Test
  void eachInvisibleLoaderIsReportedOnItsOwn() throws Exception {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (var first = new URLClassLoader(new URL[0], null);
        var second = new URLClassLoader(new URL[0], null)) {
      var subject = transformer();
      subject.transform(first, SAMPLE_CLASS, null, null, sampleBytes());
      subject.transform(second, SAMPLE_CLASS, null, null, sampleBytes());
    } finally {
      System.setErr(originalErr);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8).lines()).hasSize(2);
  }

  /** The filter decides first: a class nobody asked to instrument says nothing about its loader. */
  @Test
  void anUnmatchedClassFromAnInvisibleLoaderIsSilent() throws Exception {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (var isolated = new URLClassLoader(new URL[0], null)) {
      var result =
          transformer().transform(isolated, "com/other/SomeClass", null, null, new byte[0]);

      assertThat(result).isNull();
    } finally {
      System.setErr(originalErr);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
  }
}
