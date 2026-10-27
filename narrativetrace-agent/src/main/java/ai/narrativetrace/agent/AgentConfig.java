/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import ai.narrativetrace.api.config.TraceDepth;
import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.ConfigResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Parsed configuration for the Java agent.
 *
 * <p>INTENT: Centralize how agent arguments and property-based defaults are interpreted so the
 * transformer only has to ask whether a class should be instrumented.
 *
 * <p>{@code loggingJars} supports the standalone-attach scenario on hosts with no reachable
 * classpath (app servers): each {@code ;}-separated entry is a jar file, or a directory whose
 * {@code *.jar} files are added in sorted order. Paths are validated eagerly at parse time — a
 * missing path fails the premain loudly rather than silently producing a trace-less agent.
 *
 * <p>{@code exclude} is list 2 of the agent's two exclusion lists: configurable, package-named,
 * with a shipped default. It is always fully resolved by the time this record exists — {@link
 * #exclude} already carries either the caller's explicit list or {@link #DEFAULT_EXCLUDE},
 * normalized exactly like {@link #packages}. The key's <em>presence</em> decides which: {@code
 * exclude=} absent from the agent arguments (and {@code narrativetrace.exclude} absent from both
 * the system property and the properties file) means the shipped default applies; the key present —
 * even with nothing after it — means the caller is stating the whole list explicitly, replacing the
 * default (an empty value disables list 2 entirely). There is deliberately no second key to
 * "extend" the default: the nine prefixes are short and published verbatim, so extending is one
 * `exclude=` value with the default's entries plus the caller's own.
 *
 * <p>{@code depth} is the opt-in deep mode: {@code depth=internal} (agent argument) or {@code
 * narrativetrace.agent.depth=internal} (the fallback path) makes the transformer also weave the
 * <em>private</em> methods of the classes it already instruments — {@code packages=} include,
 * {@code exclude=} and the generated-class rule all still apply, so a JDK or library class is never
 * deepened. The default, and the value of anything unrecognised, is {@link TraceDepth#PUBLIC}: a
 * test-time verbosity switch must never be reached by a typo or a profile. Weaving alone records
 * nothing — private spans are recorded only inside a deep scope the test integration opens for one
 * test ({@link AgentRuntime#beginDeepScope()}). {@code maxInternalSpans} bounds the deep spans of
 * one scope (default {@value #DEFAULT_MAX_INTERNAL_SPANS}); beyond it deep spans are dropped, never
 * the public ones.
 */
public record AgentConfig(
    List<String> packages,
    String loggerName,
    TracingLevel level,
    List<Path> loggingJars,
    List<String> exclude,
    TraceDepth depth,
    int maxInternalSpans) {

  private static final int KEY_VALUE_PAIR = 2;
  private static final String DEFAULT_LOGGER_NAME = "narrativetrace";
  private static final TracingLevel DEFAULT_LEVEL = TracingLevel.DETAIL;

  /** The documented property naming the depth on the fallback path. */
  private static final String DEPTH_KEY = "narrativetrace.agent.depth";

  private static final String MAX_INTERNAL_SPANS_KEY = "narrativetrace.agent.maxInternalSpans";

  /** Deep spans one scope may record before the rest are dropped. */
  static final int DEFAULT_MAX_INTERNAL_SPANS = 5000;

  /** The agent argument naming list 2's explicit package list. */
  private static final String EXCLUDE_ARG = "exclude";

  /** The documented property naming it on the fallback path. */
  private static final String EXCLUDE_KEY = "narrativetrace.exclude";

  /**
   * List 2's shipped default, published verbatim in the installation guide and asserted there by
   * {@code AgentConfigExcludeDefaultDocsTest} — never typed twice.
   */
  static final List<String> DEFAULT_EXCLUDE =
      List.of(
          "org.mockito",
          "net.bytebuddy",
          "org.junit",
          "org.testng",
          "net.sf.cglib",
          "org.springframework.cglib",
          "org.aspectj",
          "datadog",
          "io.opentelemetry.javaagent");

  /** The five-component shape predates {@code depth} and must keep meaning what it meant. */
  public AgentConfig(
      List<String> packages,
      String loggerName,
      TracingLevel level,
      List<Path> loggingJars,
      List<String> exclude) {
    this(
        packages,
        loggerName,
        level,
        loggingJars,
        exclude,
        TraceDepth.PUBLIC,
        DEFAULT_MAX_INTERNAL_SPANS);
  }

  /** Convenience for callers without logging jars (most tests and programmatic use). */
  public AgentConfig(List<String> packages, String loggerName, TracingLevel level) {
    this(packages, loggerName, level, List.of(), resolveExclude(null));
  }

  /** The four-component shape predates {@code exclude} and must keep meaning what it meant. */
  public AgentConfig(
      List<String> packages, String loggerName, TracingLevel level, List<Path> loggingJars) {
    this(packages, loggerName, level, loggingJars, resolveExclude(null));
  }

  public static AgentConfig parse(String agentArgs) {
    return parse(agentArgs, Thread.currentThread().getContextClassLoader());
  }

  public static AgentConfig parse(String agentArgs, ClassLoader classLoader) {
    if (agentArgs != null && !agentArgs.isBlank()) {
      var config = parseKeyValuePairs(agentArgs);
      var packages = parsePackages(config.getOrDefault("packages", ""));
      var loggerName = config.getOrDefault("loggerName", DEFAULT_LOGGER_NAME);
      var level = TracingLevel.fromName(config.get("level"), DEFAULT_LEVEL);
      var loggingJars = parseLoggingJars(config.getOrDefault("loggingJars", ""));
      var exclude = resolveExclude(config.get(EXCLUDE_ARG));
      var depth = TraceDepth.fromName(config.get("depth"), TraceDepth.PUBLIC);
      var maxInternal = positiveOrDefault(config.get("maxInternalSpans"));
      return new AgentConfig(packages, loggerName, level, loggingJars, exclude, depth, maxInternal);
    }
    var resolver = new ConfigResolver(classLoader);
    var packagesValue = resolver.resolve("narrativetrace.packages", "");
    var loggerName = resolver.resolve("narrativetrace.loggerName", DEFAULT_LOGGER_NAME);
    var level =
        TracingLevel.fromName(resolver.resolve("narrativetrace.level", null), DEFAULT_LEVEL);
    var loggingJars = parseLoggingJars(resolver.resolve("narrativetrace.loggingJars", ""));
    var exclude = resolveExclude(resolver.resolve(EXCLUDE_KEY, null));
    var depth = TraceDepth.fromName(resolver.resolve(DEPTH_KEY, null), TraceDepth.PUBLIC);
    var maxInternal = positiveOrDefault(resolver.resolve(MAX_INTERNAL_SPANS_KEY, null));
    return new AgentConfig(
        parsePackages(packagesValue), loggerName, level, loggingJars, exclude, depth, maxInternal);
  }

  /** A positive integer, else the default — a typo in a verbosity cap must not fail the premain. */
  private static int positiveOrDefault(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_MAX_INTERNAL_SPANS;
    }
    try {
      var parsed = Integer.parseInt(value.trim());
      return parsed >= 1 ? parsed : DEFAULT_MAX_INTERNAL_SPANS;
    } catch (NumberFormatException e) {
      return DEFAULT_MAX_INTERNAL_SPANS;
    }
  }

  /**
   * List 2's effective list: the shipped default when the key was never given, or the caller's own
   * value (even empty) when it was — see the record javadoc for why presence is the switch.
   */
  private static List<String> resolveExclude(String explicitValue) {
    if (explicitValue == null) {
      return parsePackages(String.join(";", DEFAULT_EXCLUDE));
    }
    return parsePackages(explicitValue);
  }

  private static List<Path> parseLoggingJars(String value) {
    return Arrays.stream(value.split(";"))
        .filter(s -> !s.isBlank())
        .map(String::trim)
        .flatMap(AgentConfig::expandLoggingJarEntry)
        .toList();
  }

  private static Stream<Path> expandLoggingJarEntry(String entry) {
    var path = Path.of(entry);
    if (Files.isDirectory(path)) {
      return jarsInDirectorySorted(path);
    }
    if (Files.isRegularFile(path)) {
      return Stream.of(path);
    }
    throw new IllegalArgumentException("loggingJars path does not exist: " + entry);
  }

  private static Stream<Path> jarsInDirectorySorted(Path directory) {
    try (var entries = Files.list(directory)) {
      return entries.filter(p -> p.toString().endsWith(".jar")).sorted().toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException("loggingJars directory unreadable: " + directory, e);
    }
  }

  private static Map<String, String> parseKeyValuePairs(String agentArgs) {
    var result = new LinkedHashMap<String, String>();
    for (var part : agentArgs.split(",")) {
      var entry = parseEntry(part);
      if (result.containsKey(entry.getKey())) {
        throw new IllegalArgumentException("Duplicate agent config key: " + entry.getKey());
      }
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static Map.Entry<String, String> parseEntry(String part) {
    var kv = part.split("=", KEY_VALUE_PAIR);
    if (kv.length != KEY_VALUE_PAIR) {
      throw new IllegalArgumentException("Invalid agent config entry: " + part.trim());
    }
    return Map.entry(kv[0].trim(), kv[1].trim());
  }

  private static List<String> parsePackages(String value) {
    return Arrays.stream(value.split(";"))
        .filter(s -> !s.isBlank())
        .map(AgentConfig::normalizePackage)
        .toList();
  }

  static String normalizePackage(String pkg) {
    var normalized = pkg.trim();
    if (normalized.endsWith(".**")) {
      normalized = normalized.substring(0, normalized.length() - 3);
    } else if (normalized.endsWith(".*")) {
      normalized = normalized.substring(0, normalized.length() - 2);
    }
    normalized = normalized.replace('.', '/');
    if (!normalized.endsWith("/")) {
      normalized = normalized + "/";
    }
    return normalized;
  }

  public boolean shouldTransform(String className) {
    if (packages.isEmpty()) return false;
    for (var pkg : packages) {
      if (className.startsWith(pkg)) {
        return true;
      }
    }
    return false;
  }
}
