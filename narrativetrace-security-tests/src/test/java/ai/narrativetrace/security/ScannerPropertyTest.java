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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityJsonExporter;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClassNameScorer;
import ai.narrativetrace.clarity.IdentifierTokenizer;
import ai.narrativetrace.clarity.MethodNameScorer;
import ai.narrativetrace.clarity.ParameterNameScorer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.glossary.ContextResolver;
import ai.narrativetrace.glossary.Glossary;
import ai.narrativetrace.glossary.GlossaryHarvester;
import ai.narrativetrace.glossary.TermNormalizer;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.oracle.Formats;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * Target 5 of the parity document's fuzzing list: the clarity and glossary scanners over arbitrary
 * identifier text.
 *
 * <p>INTENT: These read names, not values, which is why they look safe — but a name reaches them
 * from bytecode the library did not write. A Kotlin lambda, a Groovy synthetic, a Scala mangled
 * method, an obfuscated jar and a bytecode-generated proxy all produce identifiers that no
 * hand-written Java name resembles: {@code $}, {@code <init>}, digits, non-ASCII, and the empty
 * string. Scoring must survive all of them, and the reports built from them must stay parseable.
 *
 * <p><b>@llmNote</b> The scorers return a number, so the oracle is a <em>range</em> as well as the
 * absence of a crash. A score outside 0..1 silently corrupts every average computed above it, which
 * is the failure a "does not throw" assertion would miss entirely.
 */
class ScannerPropertyTest {

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final MethodNameScorer methodScorer = new MethodNameScorer();
  private final ClassNameScorer classScorer = new ClassNameScorer();
  private final ParameterNameScorer parameterScorer = new ParameterNameScorer();
  private final TermNormalizer normalizer = new TermNormalizer();

  /** Building one costs a dictionary load; the analyzer is stateless across traces. */
  private final ClarityAnalyzer analyzer = new ClarityAnalyzer();

  @Test
  void everyCorpusStringSurvivesEveryScorer() {
    for (var hostile : HostileCorpus.strings()) {
      assertScoresAreUsable(hostile.value(), hostile.id());
    }
  }

  /**
   * {@link TermNormalizer#phrase} guards a blank identifier with {@code IllegalArgumentException},
   * which is a declared outcome and not a crash. The oracle is therefore the same shape as the
   * traceparent one: <em>nothing but</em> the declared result may come out.
   */
  @Test
  void everyCorpusStringLeavesTheTermNormalizerWithADeclaredOutcome() {
    for (var hostile : HostileCorpus.strings()) {
      assertNormalizesOrRejects(hostile.value(), hostile.id() + ": " + hostile.description());
    }
  }

  /** A whole trace of hostile identifiers, scored and then reported on, end to end. */
  @Test
  void aTraceOfHostileIdentifiersProducesAParseableClarityReport() {
    for (var hostile : HostileCorpus.strings()) {
      var tree = treeNamed(hostile.value());
      var result = Oracles.withinBudget("clarity " + hostile.id(), () -> analyzer.analyze(tree));

      assertThat(result.overallScore()).isBetween(0.0, 1.0);
      var json = new ClarityJsonExporter().export(Map.of("scenario", result));
      Formats.parseJson("clarity-json for " + hostile.id(), json);
      assertThat(new ClarityReportRenderer().render("scenario", result)).isNotNull();
    }
  }

  @Property(tries = 100)
  void aTraceOfGeneratedIdentifiersSurvivesGlossaryHarvest(
      @ForAll("identifiers") String identifier) {
    var trees = List.of(treeNamed(identifier));

    assertThatCode(() -> harvester().harvest(trees)).doesNotThrowAnyException();
  }

  @Property(tries = 100)
  void aTraceOfGeneratedIdentifiersProducesAClarityScore(@ForAll("identifiers") String identifier) {
    var result = analyzer.analyze(treeNamed(identifier));

    assertThat(result.overallScore()).isBetween(0.0, 1.0);
  }

  @Test
  void aTraceOfHostileIdentifiersSurvivesGlossaryHarvest() {
    var harvester = harvester();
    for (var hostile : HostileCorpus.strings()) {
      var trees = List.of(treeNamed(hostile.value()));
      assertThatCode(() -> harvester.harvest(trees))
          .as("%s: %s", hostile.id(), hostile.description())
          .doesNotThrowAnyException();
      assertThatCode(() -> harvester.harvestStatic(trees))
          .as("%s: %s", hostile.id(), hostile.description())
          .doesNotThrowAnyException();
    }
  }

  /**
   * A harvester over an empty glossary: every class lands in the unassigned context, which is the
   * state a project is in before anyone writes a glossary — and the one a hostile identifier is
   * most likely to be scanned in.
   */
  private static GlossaryHarvester harvester() {
    return new GlossaryHarvester(
        new ContextResolver(new Glossary(1, Map.of(), List.of())), className -> "com.example");
  }

  @Property(tries = 250)
  void anyIdentifierScoresInsideTheUnitRange(@ForAll("identifiers") String identifier) {
    assertScoresAreUsable(identifier, "generated");
  }

  @Property(tries = 250)
  void tokenizingNeverThrowsAndNeverReturnsNull(@ForAll("identifiers") String identifier) {
    assertThat(tokenizer.tokenize(identifier)).isNotNull().doesNotContainNull();
  }

  @Property(tries = 150)
  void scoringIsDeterministic(@ForAll("identifiers") String identifier) {
    assertThat(methodScorer.score(identifier)).isEqualTo(methodScorer.score(identifier));
    assertThat(classScorer.score(identifier)).isEqualTo(classScorer.score(identifier));
    assertThat(parameterScorer.score(identifier)).isEqualTo(parameterScorer.score(identifier));
  }

  @Property(tries = 100)
  void normalizingOnlyEverThrowsItsDeclaredGuard(@ForAll("identifiers") String identifier) {
    assertNormalizesOrRejects(identifier, "generated identifier");
  }

  /**
   * Either every normalizer answers, or the declared guard refuses — never a third outcome. The
   * guard covers two cases, and the second is the one the suite found: a blank identifier, and an
   * identifier that is not blank but carries no readable word ({@code __}).
   */
  private void assertNormalizesOrRejects(String identifier, String label) {
    if (identifier.isBlank() || tokenizer.tokenize(identifier).isEmpty()) {
      assertThatThrownBy(() -> normalizer.phrase(identifier))
          .as("%s must be refused by the declared guard", label)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> normalizer.methodCandidates(identifier))
          .as("%s must be refused by the declared guard", label)
          .isInstanceOf(IllegalArgumentException.class);
      return;
    }
    assertThatCode(
            () -> {
              normalizer.phrase(identifier);
              normalizer.methodCandidates(identifier);
              normalizer.parameterCandidate(identifier);
              normalizer.classCandidate(identifier);
              normalizer.exceptionCandidate(identifier);
            })
        .as("%s", label)
        .doesNotThrowAnyException();
  }

  private void assertScoresAreUsable(String identifier, String label) {
    assertThat(methodScorer.score(identifier)).as("method score for %s", label).isBetween(0.0, 1.0);
    assertThat(classScorer.score(identifier)).as("class score for %s", label).isBetween(0.0, 1.0);
    assertThat(parameterScorer.score(identifier))
        .as("parameter score for %s", label)
        .isBetween(0.0, 1.0);
    assertThat(tokenizer.tokenize(identifier)).as("tokens for %s", label).isNotNull();
  }

  private static TraceTree treeNamed(String identifier) {
    var node =
        new TraceNode(
            new MethodSignature(
                identifier, identifier, List.of(new ParameterCapture(identifier, "\"v\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /**
   * The shapes bytecode actually produces, not the shapes a Java author writes: synthetic names,
   * constructor markers, lambda spellings, digits, separators and non-ASCII.
   */
  @Provide
  Arbitrary<String> identifiers() {
    var alphabet =
        Arbitraries.of(
            "a",
            "Z",
            "0",
            "9",
            "_",
            "$",
            "<",
            ">",
            ".",
            "-",
            " ",
            "get",
            "set",
            "is",
            "Service",
            "lambda",
            "init",
            "clinit",
            "anonfun",
            "é",
            "你",
            "🙈",
            "\n",
            String.valueOf((char) 0x200b),
            String.valueOf((char) 0x0000));
    return alphabet.list().ofMinSize(0).ofMaxSize(20).map(parts -> String.join("", parts));
  }
}
