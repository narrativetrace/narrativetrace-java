/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The name-shape half of list 1 (structural, not configurable): a class whose binary name carries a
 * bytecode generator's own marker, a JDK dynamic proxy's shape, or a hidden class's loader-
 * assigned suffix.
 */
class GeneratedClassNamesTest {

  @Test
  void jdkDynamicProxyShapeIsGenerated() {
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/$Proxy0")).isTrue();
    assertThat(GeneratedClassNames.isGenerated("$Proxy17")).isTrue();
  }

  @Test
  void aPlainDollarNameThatIsNotAProxyShapeIsNotGenerated() {
    // "Proxy" without digits, or with other characters between the prefix and the digits, is not
    // the JDK's own shape — a boundary a bare "$Proxy" substring check would get wrong.
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/$Proxy")).isFalse();
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/$ProxyHelper123")).isFalse();
  }

  @Test
  void byteBuddyDefaultSubclassMarkerIsGenerated() {
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Service$ByteBuddy$1a2b3c4d"))
        .isTrue();
  }

  @Test
  void mockitoInlineMockSubclassMarkerIsGenerated() {
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Service$MockitoMock$12345678"))
        .isTrue();
  }

  @Test
  void cglibEnhancerDoubleDollarMarkerIsGenerated() {
    assertThat(
            GeneratedClassNames.isGenerated(
                "com/example/sample/Service$$EnhancerByCGLIB$$abcdef12"))
        .isTrue();
  }

  @Test
  void springCglibDoubleDollarMarkerIsGenerated() {
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Service$$SpringCGLIB$$abcdef12"))
        .isTrue();
  }

  @Test
  void lambdaHiddenClassDoubleDollarMarkerIsGenerated() {
    assertThat(
            GeneratedClassNames.isGenerated(
                "com/example/sample/Service$$Lambda$14/0x0000006801003c00"))
        .isTrue();
  }

  @Test
  void hiddenClassSuffixAloneIsGenerated() {
    assertThat(
            GeneratedClassNames.isGenerated("com/example/sample/Service$Outer/0x0000006801006400"))
        .isTrue();
  }

  @Test
  void anOrdinaryNestedClassIsNotGenerated() {
    // Single "$" between nesting levels is ordinary javac output, never a generator marker.
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Outer$Inner")).isFalse();
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Outer$Inner$Deep")).isFalse();
  }

  @Test
  void anOrdinaryTopLevelClassIsNotGenerated() {
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/Calculator")).isFalse();
  }

  @Test
  void aSiblingPrefixNearAGeneratorMarkerIsNotMistakenForOne() {
    // The literal marker, not just any nearby text, must be present.
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/ByteBuddyHelper")).isFalse();
    assertThat(GeneratedClassNames.isGenerated("com/example/sample/MockitoMockService")).isFalse();
  }
}
