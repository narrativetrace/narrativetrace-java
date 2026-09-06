/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.ArrayList;

/**
 * Parses JVM method descriptors into {@link Class#getTypeName()}-style type names.
 *
 * <p>INTENT: {@link AgentRuntime} derives declared parameter and return types from the descriptor
 * the instrumentation bakes in. Names must match what the proxy path records via {@code
 * Class.getTypeName()} ({@code "int"}, {@code "java.lang.String"}, {@code "long[]"}, {@code
 * "com.acme.Outer$Inner"}) — agent/proxy output divergence is a shipped bug class.
 *
 * <p><b>@llmNote</b> Deliberately hand-rolled instead of using ASM's {@code Type}: the architecture
 * rule confines ASM to bytecode-transformation classes, and {@code AgentRuntime} runs inside the
 * application.
 */
final class MethodDescriptorTypes {

  private MethodDescriptorTypes() {}

  /**
   * Declared parameter type names of the descriptor, in order.
   *
   * @throws IllegalArgumentException if the descriptor is malformed
   */
  static String[] parameterTypes(String descriptor) {
    if (descriptor == null || !descriptor.startsWith("(")) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    var names = new ArrayList<String>();
    int i = 1;
    while (i < descriptor.length() && descriptor.charAt(i) != ')') {
      int end = typeEnd(descriptor, i);
      names.add(typeName(descriptor, i, end));
      i = end;
    }
    if (i >= descriptor.length()) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    return names.toArray(new String[0]);
  }

  /**
   * Declared return type name of the descriptor.
   *
   * @throws IllegalArgumentException if the descriptor is malformed
   */
  static String returnType(String descriptor) {
    int close = descriptor != null ? descriptor.indexOf(')') : -1;
    if (close < 0 || close + 1 >= descriptor.length()) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    int start = close + 1;
    int end = typeEnd(descriptor, start);
    if (end != descriptor.length()) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    return typeName(descriptor, start, end);
  }

  /** End index (exclusive) of the field-type descriptor starting at {@code start}. */
  private static int typeEnd(String descriptor, int start) {
    int i = start;
    while (i < descriptor.length() && descriptor.charAt(i) == '[') {
      i++;
    }
    if (i >= descriptor.length()) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    char c = descriptor.charAt(i);
    if (c == 'L') {
      int semi = descriptor.indexOf(';', i);
      if (semi < 0) {
        throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
      }
      return semi + 1;
    }
    if ("ZBCSIFJDV".indexOf(c) < 0) {
      throw new IllegalArgumentException("Malformed method descriptor: " + descriptor);
    }
    return i + 1;
  }

  private static String typeName(String descriptor, int start, int end) {
    int dims = 0;
    int i = start;
    while (descriptor.charAt(i) == '[') {
      dims++;
      i++;
    }
    var base = baseName(descriptor, i, end);
    return base + "[]".repeat(dims);
  }

  private static final java.util.Map<Character, String> PRIMITIVES =
      java.util.Map.of(
          'Z', "boolean",
          'B', "byte",
          'C', "char",
          'S', "short",
          'I', "int",
          'F', "float",
          'J', "long",
          'D', "double",
          'V', "void");

  private static String baseName(String descriptor, int i, int end) {
    char c = descriptor.charAt(i);
    if (c == 'L') {
      return descriptor.substring(i + 1, end - 1).replace('/', '.');
    }
    return PRIMITIVES.get(c);
  }
}
