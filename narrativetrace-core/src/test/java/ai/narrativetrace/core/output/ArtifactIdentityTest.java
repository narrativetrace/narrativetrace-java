/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The family's master definition of per-invocation artifact naming. Every literal here is the
 * cross-port contract: another runtime that produces different bytes for the same inputs is wrong.
 */
class ArtifactIdentityTest {

  @Test
  void anOrdinaryMethodKeepsTheNameItHasAlwaysHad() {
    var identity = ArtifactIdentity.ofMethod("com.example.OrderTest", "customerPlacesOrder");

    assertThat(identity.fileSlug()).isEqualTo("customer_places_order");
    assertThat(identity.isInvocation()).isFalse();
  }

  @Test
  void anInvocationCarriesItsPaddedIndexAndItsSluggedLabel() {
    var identity =
        ArtifactIdentity.ofInvocation(
            "com.example.CatalogTest", "equipmentCanBeFound", 2, "find TENT");

    assertThat(identity.fileSlug()).isEqualTo("equipment_can_be_found-002-find_tent");
    assertThat(identity.isInvocation()).isTrue();
  }

  @Test
  void junitsDefaultDisplayNameReadsAsAName() {
    var identity = ArtifactIdentity.ofInvocation("T", "findsIt", 1, "[1] KAYAK");

    assertThat(identity.fileSlug()).isEqualTo("finds_it-001-1_kayak");
  }

  @Test
  void anIndexBeyondThreeDigitsKeepsEveryDigit() {
    var identity = ArtifactIdentity.ofInvocation("T", "findsIt", 1234, "case");

    assertThat(identity.fileSlug()).isEqualTo("finds_it-1234-case");
  }

  /**
   * The point of the index: two display names that a path cannot tell apart still land on two
   * files. Sanitizing alone would map both to {@code find_tent} and the second would overwrite the
   * first — the defect this scheme exists to close.
   */
  @Test
  void displayNamesDifferingOnlyInUnsafeCharactersStillGetSeparateFiles() {
    var first = ArtifactIdentity.ofInvocation("T", "finds", 1, "find/TENT");
    var second = ArtifactIdentity.ofInvocation("T", "finds", 2, "find TENT");

    assertThat(first.fileSlug()).isNotEqualTo(second.fileSlug());
    assertThat(first.fileSlug()).isEqualTo("finds-001-find_tent");
    assertThat(second.fileSlug()).isEqualTo("finds-002-find_tent");
  }

  /**
   * An approval baseline is committed once and compared on every later run, on other machines and
   * other JVMs. Nothing in the name may vary per process.
   */
  @Test
  void theSameInvocationNamesTheSameFileOnEveryRun() {
    var slug = ArtifactIdentity.ofInvocation("T", "findsIt", 3, "find TENT").fileSlug();

    assertThat(ArtifactIdentity.ofInvocation("T", "findsIt", 3, "find TENT").fileSlug())
        .isEqualTo(slug);
  }

  @Test
  void aLabelThatSlugsToNothingLeavesTheIndexToNameTheInvocation() {
    var identity = ArtifactIdentity.ofInvocation("T", "findsIt", 7, "///");

    assertThat(identity.fileSlug()).isEqualTo("finds_it-007");
  }

  @Test
  void anAbsentLabelIsTheSameAsABlankOne() {
    assertThat(ArtifactIdentity.ofInvocation("T", "findsIt", 7, null).fileSlug())
        .isEqualTo("finds_it-007");
    assertThat(new ArtifactIdentity("T", "findsIt", 7, null).invocationLabel()).isEmpty();
  }

  /** A hyphen cannot come out of the slug alphabet, so no ordinary method can claim this name. */
  @Test
  void anOrdinaryMethodCanNeverCollideWithAnInvocationArtifact() {
    var ordinary = ArtifactIdentity.ofMethod("T", "finds-001-find_tent");
    var invocation = ArtifactIdentity.ofInvocation("T", "finds", 1, "find TENT");

    assertThat(ordinary.fileSlug()).isEqualTo("finds_001_find_tent");
    assertThat(invocation.fileSlug()).isEqualTo("finds-001-find_tent");
  }

  /**
   * The method half absorbs the shortening: an artifact whose index was truncated away would be
   * unreachable, and two invocations would land on one file again.
   */
  @Test
  void aMethodNameTooLongForTheFilesystemIsShortenedAroundTheIndex() {
    var identity = ArtifactIdentity.ofInvocation("T", "a".repeat(400), 2, "find TENT");

    var slug = identity.fileSlug();
    assertThat(slug).endsWith("-002-find_tent");
    assertThat(slug.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(255 - 16);
    assertThat(ArtifactIdentity.ofInvocation("T", "a".repeat(400), 2, "find TENT").fileSlug())
        .isEqualTo(slug);
  }

  @Test
  void aVeryLongLabelIsBoundedBeforeTheMethodNameIs() {
    var identity = ArtifactIdentity.ofInvocation("T", "findsIt", 2, "label ".repeat(60));

    var slug = identity.fileSlug();
    assertThat(slug).startsWith("finds_it-002-label");
    assertThat(slug.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(255 - 16);
  }

  @Test
  void everyPerTestArtifactOfOneInvocationSharesItsName() {
    var identity = ArtifactIdentity.ofInvocation("com.example.CatalogTest", "finds", 2, "TENT");
    var resolver = new OutputDirectoryResolver(Path.of("build/narrativetrace"));

    assertThat(resolver.traceFile(identity))
        .isEqualTo(Path.of("build/narrativetrace/traces/CatalogTest/finds-002-tent.md"));
    assertThat(resolver.traceArtifact(identity, ".canonical.json"))
        .isEqualTo(
            Path.of("build/narrativetrace/traces/CatalogTest/finds-002-tent.canonical.json"));
    assertThat(resolver.diagramFile(identity))
        .isEqualTo(Path.of("build/narrativetrace/diagrams/CatalogTest/finds-002-tent.mmd"));
    assertThat(resolver.structuralFile(identity))
        .isEqualTo(Path.of("build/narrativetrace/structural/CatalogTest/finds-002-tent.nt"));
    assertThat(NarrativeApproval.approvedFile(Path.of("src/test/narratives"), identity))
        .isEqualTo(Path.of("src/test/narratives/CatalogTest/finds-002-tent.approved.nt"));
  }

  @Test
  void rejectsWhatCannotNameAFile() {
    assertThatThrownBy(() -> ArtifactIdentity.ofMethod(null, "runs"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("testClassName");
    assertThatThrownBy(() -> ArtifactIdentity.ofMethod("T", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("methodName");
    assertThatThrownBy(() -> new ArtifactIdentity("T", "runs", -1, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
    assertThatThrownBy(() -> ArtifactIdentity.ofInvocation("T", "runs", 0, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1-based");
  }
}
