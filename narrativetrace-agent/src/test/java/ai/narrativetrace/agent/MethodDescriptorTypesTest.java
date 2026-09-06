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

import org.junit.jupiter.api.Test;

/**
 * The parsed names must match {@link Class#getTypeName()} exactly — that is what the proxy path
 * records, and agent/proxy divergence is a shipped bug class.
 */
class MethodDescriptorTypesTest {

  @Test
  void parsesAllPrimitiveParameterTypes() {
    assertThat(MethodDescriptorTypes.parameterTypes("(ZBCSIFJD)V"))
        .containsExactly("boolean", "byte", "char", "short", "int", "float", "long", "double");
  }

  @Test
  void parsesObjectAndArrayParameterTypes() {
    assertThat(MethodDescriptorTypes.parameterTypes("(Ljava/lang/String;[J[[Ljava/util/Map;)V"))
        .containsExactly("java.lang.String", "long[]", "java.util.Map[][]");
  }

  @Test
  void parsesInnerClassNamesLikeClassGetTypeName() {
    assertThat(MethodDescriptorTypes.parameterTypes("(Lcom/acme/Outer$Inner;)V"))
        .containsExactly("com.acme.Outer$Inner");
  }

  @Test
  void emptyParameterListYieldsEmptyArray() {
    assertThat(MethodDescriptorTypes.parameterTypes("()Ljava/lang/String;")).isEmpty();
  }

  @Test
  void parsesReturnTypes() {
    assertThat(MethodDescriptorTypes.returnType("()V")).isEqualTo("void");
    assertThat(MethodDescriptorTypes.returnType("()I")).isEqualTo("int");
    assertThat(MethodDescriptorTypes.returnType("(I)Ljava/lang/String;"))
        .isEqualTo("java.lang.String");
    assertThat(MethodDescriptorTypes.returnType("()[[I")).isEqualTo("int[][]");
  }

  @Test
  void matchesClassGetTypeNameForRepresentativeShapes() throws Exception {
    assertThat(MethodDescriptorTypes.parameterTypes("([Ljava/lang/String;)V")[0])
        .isEqualTo(String[].class.getTypeName());
    assertThat(MethodDescriptorTypes.returnType("()[[J")).isEqualTo(long[][].class.getTypeName());
  }

  @Test
  void rejectsMalformedDescriptors() {
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes("(Q)V"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes("(Ljava/lang/String)V"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes("I)V"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.returnType("()"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.returnType(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.returnType("(I)Ijunk"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes("(["))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MethodDescriptorTypes.parameterTypes("(I"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
