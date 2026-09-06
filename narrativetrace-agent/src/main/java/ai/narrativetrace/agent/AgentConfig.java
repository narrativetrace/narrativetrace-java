/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

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
 */
public record AgentConfig(
    List<String> packages, String loggerName, TracingLevel level, List<Path> loggingJars) {

  private static final int KEY_VALUE_PAIR = 2;
  private static final String DEFAULT_LOGGER_NAME = "narrativetrace";
  private static final TracingLevel DEFAULT_LEVEL = TracingLevel.DETAIL;

  /** Convenience for callers without logging jars (most tests and programmatic use). */
  public AgentConfig(List<String> packages, String loggerName, TracingLevel level) {
    this(packages, loggerName, level, List.of());
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
      return new AgentConfig(packages, loggerName, level, loggingJars);
    }
    var resolver = new ConfigResolver(classLoader);
    var packagesValue = resolver.resolve("narrativetrace.packages", "");
    var loggerName = resolver.resolve("narrativetrace.loggerName", DEFAULT_LOGGER_NAME);
    var level =
        TracingLevel.fromName(resolver.resolve("narrativetrace.level", null), DEFAULT_LEVEL);
    var loggingJars = parseLoggingJars(resolver.resolve("narrativetrace.loggingJars", ""));
    return new AgentConfig(parsePackages(packagesValue), loggerName, level, loggingJars);
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
