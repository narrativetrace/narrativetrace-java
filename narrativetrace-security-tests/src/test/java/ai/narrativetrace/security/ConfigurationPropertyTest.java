/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.ConfigResolver;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.output.OutputDirectoryResolver;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.oracle.Oracles;
import java.nio.file.Path;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * Target 6 of the parity document's fuzzing list: configuration loading from hostile values.
 *
 * <p>INTENT: Configuration is the input a tracing library reads earliest and trusts most. It
 * arrives from a system property, an environment variable, a {@code narrativetrace.properties}
 * anyone on the classpath can ship, or a JVM argument in a deployment script — none of them typed,
 * and all of them able to fail a process at startup if a knob is parsed carelessly.
 *
 * <p><b>@llmNote</b> The contract is not uniform, and the oracle follows it rather than flattening
 * it. A mis-sized buffer <em>degrades</em>, because failing startup over a typo in an observability
 * knob is the worse outcome; an unknown pipeline strategy <em>fails</em>, because silently falling
 * back would change the durability guarantee the operator asked for. So the assertion is "the
 * declared result or the declared exception, never a third thing".
 *
 * <p><b>@sideEffects</b> Sets and clears system properties. Every case restores the previous value
 * in a {@code finally}, so no case can leak into the next.
 */
class ConfigurationPropertyTest {

  private static final String CAPACITY_KEY = "narrativetrace.buffer.capacity";
  private static final String STRATEGY_KEY = "narrativetrace.pipeline";
  private static final String LEVEL_KEY = "narrativetrace.level";

  /** Small enough that building hundreds of pipelines in one test costs nothing. */
  private static final int TEST_CAPACITY = 64;

  @Test
  void everyCorpusStringIsSafeAsABufferCapacity() {
    for (var hostile : HostileCorpus.strings()) {
      withProperty(
          CAPACITY_KEY,
          hostile.value(),
          () ->
              assertThatCode(() -> buildPipeline())
                  .as("%s: %s", hostile.id(), hostile.description())
                  .doesNotThrowAnyException());
    }
  }

  /**
   * An unknown strategy is refused, and that refusal is the contract: a topology the deployment did
   * not ask for would change what "durable" means. The oracle is that it is refused <em>by its own
   * exception</em>, never by a parse error or a null dereference.
   */
  @Test
  void everyCorpusStringIsEitherAKnownStrategyOrRefusedByName() {
    for (var hostile : HostileCorpus.strings()) {
      withProperty(
          STRATEGY_KEY,
          hostile.value(),
          () ->
              assertThat(strategyOutcome())
                  .as("%s: %s", hostile.id(), hostile.description())
                  .isIn("built", "IllegalStateException"));
    }
  }

  @Test
  void everyCorpusStringIsSafeAsATracingLevel() {
    for (var hostile : HostileCorpus.strings()) {
      assertThat(TracingLevel.fromName(hostile.value(), TracingLevel.NARRATIVE))
          .as("%s: %s", hostile.id(), hostile.description())
          .isNotNull();
    }
  }

  @Test
  void everyCorpusStringIsSafeAsACaptureFlag() {
    for (var hostile : HostileCorpus.strings()) {
      withProperty(
          "narrativetrace.capture.resource",
          hostile.value(),
          () ->
              assertThatCode(
                      () -> new NarrativeTraceConfig().resolveCaptureFlags(new ConfigResolver()))
                  .as("%s: %s", hostile.id(), hostile.description())
                  .doesNotThrowAnyException());
    }
  }

  @Test
  void resolvingConfigurationStartsNoBackgroundThread() {
    withProperty(CAPACITY_KEY, "abc", () -> buildPipeline());
    withProperty(LEVEL_KEY, "not-a-level", () -> buildPipeline());

    Oracles.noLibraryThreadLeft();
  }

  /**
   * A test class or method name reaches the artifact path from the runner, not from the library.
   * The oracle is containment: whatever the name, the resolved file stays under the base directory.
   *
   * <p><b>@llmNote</b> Compared as text rather than with AssertJ's {@code Path.startsWith}, which
   * resolves real paths and therefore fails on a directory that was never created — a filesystem
   * question, not the traversal question this asks.
   */
  @Test
  void noCorpusStringEscapesTheOutputDirectory() {
    var base = Path.of("/tmp/narrativetrace-base").toAbsolutePath().normalize();
    var resolver = new OutputDirectoryResolver(base);
    for (var hostile : HostileCorpus.strings()) {
      var file = resolver.traceFile("com.example." + hostile.value(), hostile.value());

      assertThat(file.toAbsolutePath().normalize().toString())
          .as("%s: %s", hostile.id(), hostile.description())
          .startsWith(base.toString() + "/");
    }
  }

  @Property(tries = 100)
  void anyPropertyValueLeavesTheBufferSizeUsable(@ForAll("propertyValues") String value) {
    withProperty(
        CAPACITY_KEY,
        value,
        () -> assertThatCode(() -> buildPipeline()).doesNotThrowAnyException());
  }

  @Property(tries = 100)
  void anyPropertyValueResolvesToAKnownLevel(@ForAll("propertyValues") String value) {
    assertThat(TracingLevel.values()).contains(TracingLevel.fromName(value, TracingLevel.DETAIL));
  }

  @Property(tries = 100)
  void anyPropertyValueEscapesNoOutputDirectory(@ForAll("propertyValues") String value) {
    var base = Path.of("/tmp/narrativetrace-base").toAbsolutePath().normalize();

    var file = new OutputDirectoryResolver(base).traceFile("com.example." + value, value);

    assertThat(file.toAbsolutePath().normalize().toString()).startsWith(base.toString() + "/");
  }

  private static String strategyOutcome() {
    try {
      buildPipeline();
      return "built";
    } catch (IllegalStateException e) {
      return "IllegalStateException";
    }
  }

  /**
   * A fresh {@link ConfigResolver} rescans the classpath for {@code narrativetrace.properties}, and
   * a system property outranks the file anyway — so one resolver serves every case, and the
   * per-case value still decides the outcome.
   */
  private static final ConfigResolver RESOLVER = new ConfigResolver();

  private static void buildPipeline() {
    new PipelineBootstrap(RESOLVER, new ai.narrativetrace.core.spi.ExtensionRegistry())
        .build("narrativetrace-security", TEST_CAPACITY);
  }

  /** Runs {@code body} with one system property set, restoring whatever was there before. */
  private static void withProperty(String key, String value, Runnable body) {
    var previous = System.getProperty(key);
    try {
      System.setProperty(key, value);
      body.run();
    } finally {
      restore(key, previous);
    }
  }

  private static void restore(String key, String previous) {
    if (previous == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, previous);
    }
  }

  /** Values a deployment script, an environment variable or a stray properties file can produce. */
  @Provide
  Arbitrary<String> propertyValues() {
    var alphabet =
        Arbitraries.of(
            "",
            " ",
            "0",
            "-1",
            "9",
            "999999999999999999999",
            "0x10",
            "1e9",
            "true",
            "TRUE",
            "narrative",
            "OFF",
            "off",
            "\n",
            "\t",
            "-",
            "+",
            ".",
            ",",
            "'",
            "\"",
            "${x}",
            "%s",
            "../..",
            "NaN",
            "Infinity",
            String.valueOf((char) 0x0000),
            String.valueOf((char) 0x202e),
            "🙈");
    return alphabet.list().ofMinSize(0).ofMaxSize(8).map(parts -> String.join("", parts));
  }
}
