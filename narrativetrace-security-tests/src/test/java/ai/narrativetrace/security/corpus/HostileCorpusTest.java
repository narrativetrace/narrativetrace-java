/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The corpus is data every NarrativeTrace runtime copies, so its shape is a contract in its own
 * right.
 *
 * <p>INTENT: A fixture that silently stopped loading would turn every property below it green
 * without testing anything — the failure mode of every data-driven suite. These assertions are what
 * makes "the corpus ran" observable: the counts are non-trivial, the ids are unique, the generated
 * cases materialize to the size they claim, and every declared graph shape builds.
 *
 * <p><b>@llmNote</b> Hostile code points are written here as {@code (char) 0xNNNN} rather than as
 * Java escapes. Java processes {@code \\uXXXX} before lexing, so a literal would put a raw bidi
 * override or an unpaired surrogate into this source file — the exact thing the corpus format
 * exists to avoid.
 */
class HostileCorpusTest {

  private static final int EM_DASH = 0x2014;

  @Test
  void everyFixtureLoadsWithCases() {
    assertThat(HostileCorpus.strings()).hasSizeGreaterThan(50);
    assertThat(HostileCorpus.injections()).hasSizeGreaterThan(30);
    assertThat(HostileCorpus.traceparents()).hasSizeGreaterThan(30);
    assertThat(HostileCorpus.tracestates()).hasSizeGreaterThan(5);
    assertThat(HostileCorpus.templates()).hasSizeGreaterThan(30);
    assertThat(HostileCorpus.graphs()).hasSizeGreaterThan(40);
    assertThat(HostileCorpus.names()).hasSizeGreaterThan(15);
    assertThat(HostileCorpus.redactions()).hasSizeGreaterThan(60);
  }

  @Test
  void caseIdentifiersAreUniqueWithinEachFixture() {
    assertUniqueIds(HostileCorpus.strings().stream().map(CorpusCase::id).toList());
    assertUniqueIds(HostileCorpus.injections().stream().map(CorpusCase::id).toList());
    assertUniqueIds(HostileCorpus.traceparents().stream().map(HeaderCase::id).toList());
    assertUniqueIds(HostileCorpus.templates().stream().map(TemplateCase::id).toList());
    assertUniqueIds(HostileCorpus.graphs().stream().map(GraphCase::id).toList());
    assertUniqueIds(HostileCorpus.names().stream().map(CorpusCase::id).toList());
    assertUniqueIds(HostileCorpus.redactions().stream().map(RedactionCase::id).toList());
  }

  @Test
  void everyCaseCarriesADescriptionSayingWhatBreaks() {
    assertThat(HostileCorpus.strings()).allSatisfy(c -> assertThat(c.description()).isNotBlank());
    assertThat(HostileCorpus.graphs()).allSatisfy(c -> assertThat(c.description()).isNotBlank());
    assertThat(HostileCorpus.names()).allSatisfy(c -> assertThat(c.description()).isNotBlank());
    assertThat(HostileCorpus.redactions())
        .allSatisfy(c -> assertThat(c.description()).isNotBlank());
  }

  @Test
  void theGeneratedCasesMaterializeToTheSizeTheyClaim() {
    assertThat(valueOf("long-1mib")).hasSize(1024 * 1024);
    assertThat(valueOf("long-512")).hasSize(512);
    assertThat(valueOf("long-truncation-boundary")).hasSize(200);
    assertThat(valueOf("long-astral")).hasSize(300);
  }

  @Test
  void theEscapedCasesCarryTheCodePointsTheyName() {
    assertThat(valueOf("nul")).isEqualTo(charOf(0x0000));
    assertThat(valueOf("rtl-override")).startsWith(charOf(0x202e));
    assertThat(valueOf("unpaired-high-surrogate")).isEqualTo(charOf(0xd800));
    assertThat(valueOf("unpaired-low-surrogate")).isEqualTo(charOf(0xdc00));
    assertThat(valueOf("zero-width")).contains(charOf(0x200b)).contains(charOf(0x2060));
    assertThat(valueOf("noncharacter")).isEqualTo(charOf(0xfffe) + charOf(0xffff));
    assertThat(valueOf("noncharacter-arabic-block")).isEqualTo(charOf(0xfdd0) + charOf(0xfdef));
    assertThat(valueOf("noncharacter-supplementary"))
        .as("U+1FFFE and U+1FFFF are two chars each, and both halves must survive the fixture")
        .isEqualTo(charOf(0xd83f) + charOf(0xdffe) + charOf(0xd83f) + charOf(0xdfff));
  }

  /**
   * The laxness family: every one of these is a well-formed header with something around it, which
   * is exactly what a parser that trims, or splits and ignores the tail, would accept. The corpus
   * loop asserts the outcome each case declares; this pins that the family is present and declares
   * rejection, so deleting a case is a visible change rather than a quiet loss of coverage.
   */
  @Test
  void theTraceparentLaxnessFamilyIsPresentAndMustBeRejected() {
    var laxness =
        List.of(
            "leading-whitespace",
            "trailing-whitespace",
            "leading-tab",
            "trailing-newline",
            "surrounding-whitespace",
            "inner-whitespace",
            "trailing-garbage-v00",
            "trailing-garbage-v00-two-fields",
            "trailing-garbage-v00-empty-fields");

    for (var id : laxness) {
      var header = HostileCorpus.traceparents().stream().filter(c -> c.id().equals(id)).findFirst();
      assertThat(header).as("%s must be in the corpus", id).isPresent();
      assertThat(header.orElseThrow().accepted())
          .as("%s must be declared unacceptable", id)
          .isFalse();
    }
  }

  /**
   * The fixture files are ASCII on disk. Enforced rather than agreed: an editor that helpfully
   * normalises a raw bidi character would change what a case tests without changing what it reads.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "strings.json",
        "headers.json",
        "templates.json",
        "graphs.json",
        "injection.json",
        "names.json",
        "redaction.json"
      })
  void theFixtureFilesStayAsciiOnDisk(String fileName) throws Exception {
    var path = Path.of("src/test/resources/hostile-corpus", fileName);
    var text = Files.readString(path, StandardCharsets.UTF_8);

    var offending = text.chars().filter(c -> c > 126 && c != EM_DASH).boxed().distinct().toList();

    assertThat(offending)
        .as("%s must spell hostile characters as escapes, not raw bytes", fileName)
        .isEmpty();
  }

  /**
   * A redaction row that declared neither a name nor a value, or an unreadable {@code expect},
   * would be replayed as a silently trivial assertion — the same failure mode as a fixture that
   * stopped loading, one row at a time.
   */
  @Test
  void everyRedactionCaseDeclaresExactlyOneSubjectAndOneDirection() {
    for (var redactionCase : HostileCorpus.redactions()) {
      assertThat(redactionCase.secret())
          .as("%s must carry a canary or a value", redactionCase.id())
          .isNotBlank();
      assertThat(redactionCase.isName() == (redactionCase.value() == null))
          .as("%s must be a name case or a value case, never both or neither", redactionCase.id())
          .isTrue();
      assertThat(redactionCase.expect())
          .as("%s must declare which way it goes", redactionCase.id())
          .isIn("redacted", "visible");
    }
  }

  /**
   * A name case whose canary is itself secret-shaped would pass the hidden assertion for the wrong
   * reason — the value axis would catch it whatever the name said.
   */
  @Test
  void noRedactionCanaryIsItselfASecretShape() {
    for (var redactionCase : HostileCorpus.redactions()) {
      if (redactionCase.isName()) {
        assertThat(redactionCase.canary())
            .as("%s must test the name axis, not the value axis", redactionCase.id())
            .matches("canary-[a-z0-9-]+");
      }
    }
  }

  @Test
  void everyDeclaredGraphShapeBuilds() {
    for (var graphCase : HostileCorpus.graphs()) {
      assertThatCode(() -> HostileGraphs.build(graphCase, "sentinel-probe"))
          .as("graph shape %s must build", graphCase.id())
          .doesNotThrowAnyException();
    }
  }

  @Test
  void everyTemplateFixtureNameResolvesToAGraph() {
    for (var templateCase : HostileCorpus.templates()) {
      assertThatCode(() -> HostileGraphs.templateValues(templateCase.values(), "sentinel-probe"))
          .as("template case %s names an unknown fixture", templateCase.id())
          .doesNotThrowAnyException();
    }
  }

  @Test
  void theHeaderFixtureMarksBothOutcomes() {
    assertThat(HostileCorpus.traceparents()).anyMatch(HeaderCase::accepted);
    assertThat(HostileCorpus.traceparents()).anyMatch(header -> !header.accepted());
  }

  private static String charOf(int codePoint) {
    return String.valueOf((char) codePoint);
  }

  private static String valueOf(String id) {
    return HostileCorpus.strings().stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no corpus case with id " + id))
        .value();
  }

  private static void assertUniqueIds(List<String> ids) {
    assertThat(ids).doesNotHaveDuplicates();
  }
}
