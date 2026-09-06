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

import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.template.TemplateParser;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.corpus.TemplateCase;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Target 4 of the parity document's fuzzing list: template parsing and rendering.
 *
 * <p>INTENT: A narration is prose the author wrote, so it travels into every artifact without
 * passing through {@code ValueRenderer} at all — which is what made "a template naming a redacted
 * path prints it in full" a leak the renderer's own tests could not see. The owner's ruling of
 * 2026-08-31 settled the product question: redaction wins, at every depth of the path. This target
 * holds that ruling against generated paths rather than the five that were reported.
 *
 * <p><b>@edgeCase</b> A path naming <em>no</em> member stops the walk and redacts nothing, so a
 * mistyped {@code {custmer.cvv}} still survives literally and still raises its
 * unresolved-placeholder warning. That is deliberate, and the corpus carries cases for it: nothing
 * can leak through a path that resolves to nothing.
 */
class TemplateRedactionPropertyTest {

  @TestFactory
  List<DynamicTest> everyCorpusTemplateResolvesWithoutLeakingOrThrowing() {
    return HostileCorpus.templates().stream()
        .map(
            templateCase ->
                DynamicTest.dynamicTest(templateCase.id(), () -> assertResolves(templateCase)))
        .toList();
  }

  @Test
  void everyCorpusTemplateResolvesIdentricallyTwice() {
    for (var templateCase : HostileCorpus.templates()) {
      var values = HostileGraphs.templateValues(templateCase.values(), Oracles.freshSentinel());
      Oracles.idempotent(
          "template " + templateCase.id(),
          () -> TemplateParser.resolve(templateCase.template(), values));
    }
  }

  /**
   * The bug class: a redacted leaf stays redacted however deep the path that names it, and however
   * many segments precede it.
   */
  @Property(tries = 100)
  void aPathNamingARedactedMemberAlwaysRendersTheMarker(
      @ForAll("redactedPaths") String path, @ForAll("prose") String surrounding) {
    var sentinel = Oracles.freshSentinel();
    var values = HostileGraphs.templateValues(fixtureFor(path), sentinel);

    var resolved = TemplateParser.resolve(surrounding + "{" + path + "}" + surrounding, values);

    assertThat(resolved).as("path %s leaked", path).doesNotContain(sentinel);
    assertThat(resolved)
        .as("path %s fell silent instead of redacting", path)
        .contains(RedactionPolicy.MARKER);
  }

  /**
   * The other production of the grammar, and the one the property above cannot reach: a bare key
   * naming a value directly. Every generated path here routes through the branch that was already
   * correct, which is why {@code @Narrated("login {password}")} printing the password survived a
   * suite aimed at exactly this class of bug until 2026-09-04.
   */
  @Property(tries = 100)
  void aBareKeyNamingASecretAlwaysRendersTheMarker(
      @ForAll("redactedKeys") String key, @ForAll("prose") String surrounding) {
    var sentinel = Oracles.freshSentinel();

    var resolved =
        TemplateParser.resolve(
            surrounding + "{" + key + "}" + surrounding, Map.<String, Object>of(key, sentinel));

    assertThat(resolved).as("key %s leaked", key).doesNotContain(sentinel);
    assertThat(resolved)
        .as("key %s fell silent instead of redacting", key)
        .contains(RedactionPolicy.MARKER);
  }

  /** The second axis on the same production: the bytes are a credential under any name at all. */
  @Property(tries = 50)
  void aCredentialShapedScalarIsRefusedWhateverTheKeyIsCalled(@ForAll("innocuousKeys") String key) {
    var sentinel = Oracles.freshSentinel();
    var jwt = "eyJhbGciOiJIUzI1NiJ9." + sentinel + ".c2lnbmF0dXJl";

    var resolved = TemplateParser.resolve("issued {" + key + "}", Map.<String, Object>of(key, jwt));

    assertThat(resolved).as("key %s leaked the token", key).doesNotContain(sentinel);
    assertThat(resolved)
        .as("key %s fell silent instead of redacting", key)
        .contains(RedactionPolicy.MARKER);
  }

  @Property(tries = 100)
  void resolvingNeverThrowsWhateverTheTemplateContains(@ForAll("braceSoup") String template) {
    var values = HostileGraphs.templateValues("card", Oracles.freshSentinel());

    assertThatCode(() -> TemplateParser.resolve(template, values)).doesNotThrowAnyException();
  }

  @Property(tries = 100)
  void noGeneratedTemplateLeaksTheRedactedComponent(@ForAll("braceSoup") String template) {
    var sentinel = Oracles.freshSentinel();
    var values = HostileGraphs.templateValues("card", sentinel);

    assertThat(TemplateParser.resolve(template, values)).doesNotContain(sentinel);
  }

  /**
   * Depth is not a way around the walk: a fifty-segment path is still refused segment by segment.
   */
  @Property(tries = 50)
  void aRedactedSegmentIsRefusedAtAnyDepthOfPath(@ForAll @IntRange(min = 1, max = 50) int depth) {
    var sentinel = Oracles.freshSentinel();
    var path = "card" + ".number".repeat(depth - 1) + ".cvv";

    var resolved =
        TemplateParser.resolve("{" + path + "}", HostileGraphs.templateValues("card", sentinel));

    assertThat(resolved).doesNotContain(sentinel);
  }

  /** A resolved narration travels into every artifact, so containment has to hold there too. */
  @Test
  void aRedactedPathReachesNoWrittenArtifact() {
    for (var templateCase : HostileCorpus.templates()) {
      if (!templateCase.expectsRedaction()) {
        continue;
      }
      var sentinel = Oracles.freshSentinel();
      var values = HostileGraphs.templateValues(templateCase.values(), sentinel);
      var narration = TemplateParser.resolve(templateCase.template(), values);

      Oracles.containsNoSentinel(
          Emitters.everyOutput(Emitters.treeNarrating(narration, narration)), sentinel);
    }
  }

  private void assertResolves(TemplateCase templateCase) {
    var sentinel = Oracles.freshSentinel();
    var values = HostileGraphs.templateValues(templateCase.values(), sentinel);

    var resolved =
        Oracles.withinBudget(
            "template " + templateCase.id(),
            () -> TemplateParser.resolve(templateCase.template(), values));

    assertThat(resolved).as("%s: %s", templateCase.id(), templateCase.description()).isNotNull();
    assertThat(resolved)
        .as("%s leaked the redacted value", templateCase.id())
        .doesNotContain(sentinel);
    if (templateCase.expectsRedaction()) {
      assertThat(resolved)
          .as("%s must show the marker rather than fall silent", templateCase.id())
          .contains(RedactionPolicy.MARKER);
    }
  }

  /** Which fixture graph a generated path resolves against, from its root segment. */
  private static String fixtureFor(String path) {
    var root = path.substring(0, Math.max(path.indexOf('.'), 0));
    return Map.of("card", "card", "user", "user", "order", "order", "a", "deep")
        .getOrDefault(root, "card");
  }

  /** Paths whose last segment is redacted, by annotation or by the deny-list, at varying depth. */
  @Provide
  Arbitrary<String> redactedPaths() {
    return Arbitraries.of(
        "card.cvv", "user.password", "user.secret", "order.card.cvv", "a.b.c.d.secret");
  }

  /**
   * Bare parameter names the deny-list knows, across its three matching modes: substring, whole
   * identifier token, and the multilingual vocabulary that folds accents away.
   */
  @Provide
  Arbitrary<String> redactedKeys() {
    return Arbitraries.of(
        "password", "apiToken", "cardCvv", "secret", "sessionId", "senha", "contraseña", "密码");
  }

  /** Names no deny-list knows: what is left is what the bytes themselves say. */
  @Provide
  Arbitrary<String> innocuousKeys() {
    return Arbitraries.of("value", "data", "header", "payload", "item");
  }

  /**
   * Text that surrounds a placeholder without redrawing its boundaries. Braces are deliberately
   * absent: a brace beside a placeholder makes a <em>different</em> placeholder, so asserting the
   * marker there would be asserting about a path nobody wrote. Brace-laden surroundings are covered
   * by {@link #noGeneratedTemplateLeaksTheRedactedComponent}, where the oracle is containment.
   */
  @Provide
  Arbitrary<String> prose() {
    return Arbitraries.of("", " ", "charging ", " for ", "$", "\n", "[", "]", "%s", "0");
  }

  /** Braces, dots and identifier fragments, recombined — the part nobody listed. */
  @Provide
  Arbitrary<String> braceSoup() {
    var alphabet =
        Arbitraries.of(
            "{",
            "}",
            ".",
            "card",
            "cvv",
            "number",
            "user",
            "password",
            "secret",
            "a",
            " ",
            "$",
            "\n",
            "[",
            "]",
            "0",
            String.valueOf((char) 0x200b),
            String.valueOf((char) 0x202e));
    return alphabet.list().ofMinSize(0).ofMaxSize(30).map(parts -> String.join("", parts));
  }
}
