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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * List 2's precondition, exercised through the transformer's public entry point: a class matching
 * {@code packages=} that ALSO matches an excluded prefix — the shipped default or an explicit
 * {@code exclude=} — is not instrumented, and the exclusion is reported once per matched prefix,
 * never once per class, mirroring {@link LoaderVisibilityTransformTest}'s shape for a different
 * gate.
 */
class ExcludedPackagesTransformTest {

  private static final String SAMPLE_RESOURCE = "ai/narrativetrace/agent/sample/Calculator.class";

  private byte[] sampleBytes() throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream(SAMPLE_RESOURCE)) {
      return stream.readAllBytes();
    }
  }

  private String withCapturedStderr(Runnable action) {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      action.run();
    } finally {
      System.setErr(originalErr);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  @Test
  void aClassMatchingTheShippedDefaultExcludeIsNotInstrumented() throws Exception {
    var transformer = new NarrativeClassFileTransformer(AgentConfig.parse("packages=org.mockito"));

    var result =
        transformer.transform(
            getClass().getClassLoader(), "org/mockito/internal/Foo", null, null, sampleBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassMatchingAnExplicitExcludeIsNotInstrumented() throws Exception {
    var transformer =
        new NarrativeClassFileTransformer(
            AgentConfig.parse("packages=com.example,exclude=com.example.generated"));

    var result =
        transformer.transform(
            getClass().getClassLoader(), "com/example/generated/Foo", null, null, sampleBytes());

    assertThat(result).isNull();
  }

  /** The ruling's precedence rule: exclude wins even for a sub-package of a matched package. */
  @Test
  void anExcludedSubPackageLosesEvenThoughItsParentPackageIsIncluded() throws Exception {
    var transformer =
        new NarrativeClassFileTransformer(
            AgentConfig.parse("packages=com.example,exclude=com.example.generated"));

    var excluded =
        transformer.transform(
            getClass().getClassLoader(), "com/example/generated/Foo", null, null, sampleBytes());
    var included =
        transformer.transform(
            getClass().getClassLoader(), "com/example/Bar", null, null, sampleBytes());

    assertThat(excluded).isNull();
    assertThat(included).isNotNull();
  }

  @Test
  void aSiblingPrefixNearAnExcludedPackageIsNotMistakenForIt() throws Exception {
    var transformer =
        new NarrativeClassFileTransformer(
            AgentConfig.parse("packages=org.acmeExtra,exclude=org.acme"));

    var result =
        transformer.transform(
            getClass().getClassLoader(), "org/acmeExtra/Service", null, null, sampleBytes());

    assertThat(result).as("org.acme must not match org.acmeExtra").isNotNull();
  }

  @Test
  void anExcludedPrefixIsReportedOncePerPrefixNotOncePerClass() throws Exception {
    var transformer = new NarrativeClassFileTransformer(AgentConfig.parse("packages=org.mockito"));

    var stderr =
        withCapturedStderr(
            () -> {
              try {
                transformer.transform(
                    getClass().getClassLoader(),
                    "org/mockito/internal/Foo",
                    null,
                    null,
                    sampleBytes());
                transformer.transform(
                    getClass().getClassLoader(),
                    "org/mockito/internal/Bar",
                    null,
                    null,
                    sampleBytes());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(stderr.lines()).hasSize(1);
  }

  @Test
  void twoDifferentExcludedPrefixesAreEachReportedOnce() throws Exception {
    var transformer =
        new NarrativeClassFileTransformer(
            AgentConfig.parse("packages=com.example,exclude=com.example.one;com.example.two"));

    var stderr =
        withCapturedStderr(
            () -> {
              try {
                transformer.transform(
                    getClass().getClassLoader(), "com/example/one/Foo", null, null, sampleBytes());
                transformer.transform(
                    getClass().getClassLoader(), "com/example/two/Bar", null, null, sampleBytes());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(stderr.lines()).hasSize(2);
  }

  @Test
  void aMatchedClassOutsideEveryExcludedPrefixIsStillInstrumented() throws Exception {
    var transformer = new NarrativeClassFileTransformer(AgentConfig.parse("packages=com.example"));

    var result =
        transformer.transform(
            getClass().getClassLoader(), "com/example/Service", null, null, sampleBytes());

    assertThat(result).isNotNull();
  }
}
