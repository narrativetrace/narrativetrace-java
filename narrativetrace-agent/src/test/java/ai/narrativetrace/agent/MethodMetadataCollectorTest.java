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
import org.objectweb.asm.ClassReader;

class MethodMetadataCollectorTest {

  @Test
  void collectsParameterNamesFromCalculator() throws Exception {
    var bytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var collector = new MethodMetadataCollector();
    new ClassReader(bytes).accept(collector, 0);

    var metadata = collector.getMetadata();
    var addMeta = metadata.get(MethodMetadataCollector.key("add", "(II)I"));
    assertThat(addMeta).isNotNull();
    assertThat(addMeta.parameterNames()).containsExactly("a", "b");
  }

  @Test
  void collectsNotTracedAnnotation() throws Exception {
    var bytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var collector = new MethodMetadataCollector();
    new ClassReader(bytes).accept(collector, 0);

    var metadata = collector.getMetadata();
    var loginMeta =
        metadata.get(
            MethodMetadataCollector.key(
                "login", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
    assertThat(loginMeta).isNotNull();
    assertThat(loginMeta.parameterNames()).containsExactly("username", "password");
    assertThat(loginMeta.redacted()).containsExactly(false, true);
  }

  @Test
  void collectsNarratedTemplate() throws Exception {
    var bytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var collector = new MethodMetadataCollector();
    new ClassReader(bytes).accept(collector, 0);

    var metadata = collector.getMetadata();
    var calcMeta =
        metadata.get(MethodMetadataCollector.key("calculatePrice", "(Ljava/lang/String;I)D"));
    assertThat(calcMeta).isNotNull();
    assertThat(calcMeta.narratedTemplate()).isEqualTo("Processing {item} for quantity {quantity}");
  }

  @Test
  void collectsOnErrorAnnotations() throws Exception {
    var bytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var collector = new MethodMetadataCollector();
    new ClassReader(bytes).accept(collector, 0);

    var metadata = collector.getMetadata();
    var transferMeta =
        metadata.get(
            MethodMetadataCollector.key("transfer", "(Ljava/lang/String;Ljava/lang/String;I)V"));
    assertThat(transferMeta).isNotNull();
    assertThat(transferMeta.onErrors()).hasSize(2);
    assertThat(transferMeta.onErrors()[0].template())
        .isEqualTo("Transfer failed for amount {amount}");
    assertThat(transferMeta.onErrors()[0].exceptionDescriptor())
        .isEqualTo("Ljava/lang/IllegalArgumentException;");
    assertThat(transferMeta.onErrors()[1].template())
        .isEqualTo("Transfer error for amount {amount}");
    assertThat(transferMeta.onErrors()[1].exceptionDescriptor()).isEqualTo("Ljava/lang/Throwable;");
  }
}
