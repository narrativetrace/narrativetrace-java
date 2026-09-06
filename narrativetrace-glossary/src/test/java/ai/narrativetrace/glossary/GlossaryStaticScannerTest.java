/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.glossary.fixtures.OverdraftFixtureService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryStaticScannerTest {

  private final GlossaryStaticScanner scanner = new GlossaryStaticScanner();

  private static MethodSignature signatureOf(List<TraceTree> trees, String methodName) {
    return trees.stream()
        .flatMap(tree -> tree.roots().stream())
        .map(TraceNode::signature)
        .filter(signature -> signature.methodName().equals(methodName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no node for " + methodName));
  }

  @Test
  void carriesRawAnnotationTextSoTemplatesKeepTheirPlaceholders() {
    var trees = scanner.scan(List.of(OverdraftFixtureService.class));

    var signature = signatureOf(trees, "openOverdraftAccount");
    assertThat(signature.narration()).isEqualTo("Opening overdraft account for {customerId}");
    assertThat(signature.errorContext()).isEqualTo("Overdraft refused for {customerId}");
  }

  @Test
  void capturesClassAndParameterNamesForUnannotatedMethods() {
    var trees = scanner.scan(List.of(OverdraftFixtureService.class));

    var signature = signatureOf(trees, "closeOverdraftAccount");
    assertThat(signature.className()).isEqualTo("OverdraftFixtureService");
    assertThat(signature.narration()).isNull();
    assertThat(signature.parameters())
        .extracting(p -> p.name())
        .containsExactly("overdraftAccountId");
  }

  @Test
  void scansCompiledClassFilesFromADirectory(@TempDir Path classesDir) throws Exception {
    copyFixtureClassInto(classesDir);

    var trees = scanner.scan(classesDir);

    assertThat(signatureOf(trees, "openOverdraftAccount").narration())
        .isEqualTo("Opening overdraft account for {customerId}");
  }

  @Test
  void anEmptyDirectoryYieldsNoTreesRatherThanFailing(@TempDir Path classesDir) throws Exception {
    assertThat(scanner.scan(classesDir)).isEmpty();
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> scanner.scan((List<Class<?>>) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scanner.scan((Path) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void copyFixtureClassInto(Path classesDir) throws Exception {
    var classFile = OverdraftFixtureService.class.getName().replace('.', '/') + ".class";
    try (var source = getClass().getClassLoader().getResourceAsStream(classFile)) {
      assertThat(source).isNotNull();
      var target = classesDir.resolve(classFile);
      Files.createDirectories(target.getParent());
      Files.copy(source, target);
    }
  }
}
