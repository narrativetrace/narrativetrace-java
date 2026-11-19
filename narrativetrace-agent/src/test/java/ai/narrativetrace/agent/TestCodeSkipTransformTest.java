/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.agent.sample.JUnitAnnotatedSample;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import org.junit.jupiter.api.Test;

/**
 * List 3's decision, exercised through the transformer's public entry point: test code is not
 * instrumented by default. Origin decides first (the code source's output directory), content only
 * when the origin says nothing, and {@code tests=trace} restores instrumenting test code.
 */
class TestCodeSkipTransformTest {

  private static final String PRODUCTION_SAMPLE = "ai/narrativetrace/agent/sample/Calculator.class";

  private byte[] productionBytes() throws Exception {
    return bytesOf(PRODUCTION_SAMPLE);
  }

  private byte[] annotatedBytes() throws Exception {
    return bytesOf(JUnitAnnotatedSample.class.getName().replace('.', '/') + ".class");
  }

  private byte[] bytesOf(String resource) throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      return stream.readAllBytes();
    }
  }

  private static ProtectionDomain domainAt(String location) throws Exception {
    return new ProtectionDomain(
        new CodeSource(new URL(location), (java.security.cert.Certificate[]) null), null);
  }

  private static NarrativeClassFileTransformer transformer(String agentArgs) {
    return new NarrativeClassFileTransformer(AgentConfig.parse(agentArgs));
  }

  private byte[] transform(
      NarrativeClassFileTransformer transformer,
      String className,
      ProtectionDomain domain,
      byte[] bytes) {
    return transformer.transform(getClass().getClassLoader(), className, null, domain, bytes);
  }

  private static String withCapturedStderr(Runnable action) {
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
  void aClassLoadedFromMavenTestOutputIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/ServiceTest",
            domainAt("file:/work/app/target/test-classes/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassLoadedFromGradleJavaTestOutputIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/ServiceTest",
            domainAt("file:/work/app/build/classes/java/test/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassLoadedFromGradleKotlinTestOutputIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/ServiceTest",
            domainAt("file:/work/app/build/classes/kotlin/test/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassLoadedFromAGradleIntegrationTestSourceSetIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/ServiceIT",
            domainAt("file:/work/app/build/classes/java/integrationTest/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassLoadedFromTheGradleTestFixturesSourceSetIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Fixtures",
            domainAt("file:/work/app/build/classes/java/testFixtures/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void aClassLoadedFromMavenProductionOutputIsInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Service",
            domainAt("file:/work/app/target/classes/"),
            productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aClassLoadedFromGradleProductionOutputIsInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Service",
            domainAt("file:/work/app/build/classes/java/main/"),
            productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aProductionOutputUnderADirectoryNamedOutTestIsStillInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Service",
            domainAt("file:/home/u/out/test/myapp/target/classes/"),
            productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aProductionOutputUnderADirectoryNamedTestClassesIsStillInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Service",
            domainAt("file:/srv/test-classes/app/target/classes/"),
            productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aClassOfUnknownOriginDeclaringATestMethodIsNotInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"), "com/example/Checks", null, annotatedBytes());

    assertThat(result).isNull();
  }

  @Test
  void aPlainClassOfUnknownOriginIsInstrumented() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"), "com/example/Service", null, productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aProtectionDomainWithoutACodeSourceIsUnknownOriginAndFallsBackToContent() throws Exception {
    var noCodeSource = new ProtectionDomain(null, null);

    var annotated =
        transform(
            transformer("packages=com.example"),
            "com/example/Checks",
            noCodeSource,
            annotatedBytes());
    var plain =
        transform(
            transformer("packages=com.example"),
            "com/example/Service",
            noCodeSource,
            productionBytes());

    assertThat(annotated).isNull();
    assertThat(plain).isNotNull();
  }

  @Test
  void aProductionOriginIsConclusiveEvenForAClassDeclaringATestMethod() throws Exception {
    var result =
        transform(
            transformer("packages=com.example"),
            "com/example/Checks",
            domainAt("file:/work/app/target/classes/"),
            annotatedBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void aNestedClassOfRecognisedTestCodeIsNotInstrumentedEvenWithoutAnnotationsOrOrigin()
      throws Exception {
    var transformer = transformer("packages=com.example");
    transform(
        transformer,
        "com/example/ServiceTest",
        domainAt("file:/work/app/target/test-classes/"),
        productionBytes());

    var nested = transform(transformer, "com/example/ServiceTest$Helper", null, productionBytes());

    assertThat(nested).isNull();
  }

  @Test
  void aNestedClassOfAnAnnotatedTestClassIsNotInstrumentedEvenWithoutAnnotations()
      throws Exception {
    var transformer = transformer("packages=com.example");
    transform(transformer, "com/example/Checks", null, annotatedBytes());

    var nested = transform(transformer, "com/example/Checks$Fixture$Deep", null, productionBytes());

    assertThat(nested).isNull();
  }

  @Test
  void aNestedClassOfAProductionClassIsStillInstrumented() throws Exception {
    var transformer = transformer("packages=com.example");
    transform(transformer, "com/example/Service", null, productionBytes());

    var nested = transform(transformer, "com/example/Service$Part", null, productionBytes());

    assertThat(nested).isNotNull();
  }

  @Test
  void testsTraceInstrumentsMavenTestOutput() throws Exception {
    var result =
        transform(
            transformer("packages=com.example,tests=trace"),
            "com/example/ServiceTest",
            domainAt("file:/work/app/target/test-classes/"),
            productionBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void testsTraceInstrumentsAClassDeclaringATestMethod() throws Exception {
    var result =
        transform(
            transformer("packages=com.example,tests=trace"),
            "com/example/Checks",
            null,
            annotatedBytes());

    assertThat(result).isNotNull();
  }

  @Test
  void testsSkipIsTheSameAsTheDefault() throws Exception {
    var result =
        transform(
            transformer("packages=com.example,tests=skip"),
            "com/example/ServiceTest",
            domainAt("file:/work/app/target/test-classes/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void anUnknownTestsValueBehavesAsTheDefaultAndDoesNotFailTheAgent() throws Exception {
    var transformer = transformer("packages=com.example,tests=banana");

    var result =
        transform(
            transformer,
            "com/example/ServiceTest",
            domainAt("file:/work/app/target/test-classes/"),
            productionBytes());

    assertThat(result).isNull();
  }

  @Test
  void theTestsPropertyOnTheFallbackPathSelectsTheSwitch() throws Exception {
    System.setProperty("narrativetrace.tests", "trace");
    System.setProperty("narrativetrace.packages", "com.example");
    try {
      var result =
          transform(
              transformer(null),
              "com/example/ServiceTest",
              domainAt("file:/work/app/target/test-classes/"),
              productionBytes());

      assertThat(result).isNotNull();
    } finally {
      System.clearProperty("narrativetrace.tests");
      System.clearProperty("narrativetrace.packages");
    }
  }

  @Test
  void leavingOutTestCodeIsReportedOnceNamingTheSwitchNotOncePerClass() throws Exception {
    var transformer = transformer("packages=com.example");
    var domain = domainAt("file:/work/app/target/test-classes/");
    var bytes = productionBytes();

    var stderr =
        withCapturedStderr(
            () -> {
              transform(transformer, "com/example/OneTest", domain, bytes);
              transform(transformer, "com/example/TwoTest", domain, bytes);
            });

    assertThat(stderr.lines()).hasSize(1);
    assertThat(stderr).contains("com/example/OneTest").contains("tests=trace");
  }

  @Test
  void nothingIsReportedWhenNoClassIsLeftOutAsTestCode() throws Exception {
    var transformer = transformer("packages=com.example");

    var stderr =
        withCapturedStderr(
            () -> {
              try {
                transform(transformer, "com/example/Service", null, productionBytes());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(stderr).isEmpty();
  }

  @Test
  void anUnparsableClassFileOfUnknownOriginKeepsTheExistingUninstrumentedFailurePath() {
    var transformer = transformer("packages=com.example");
    var garbage = new byte[] {1, 2, 3, 4};

    var holder = new byte[1][];
    var stderr =
        withCapturedStderr(
            () ->
                assertThatCode(
                        () -> holder[0] = transform(transformer, "com/example/Bad", null, garbage))
                    .doesNotThrowAnyException());

    assertThat(holder[0]).isNull();
    assertThat(stderr).contains("cannot instrument com/example/Bad");
  }
}
