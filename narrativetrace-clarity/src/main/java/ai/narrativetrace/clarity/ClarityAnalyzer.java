/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores the naming clarity of a trace tree across five weighted dimensions.
 *
 * <p>INTENT: Use this as the canonical scoring engine. Other clarity components exist to feed it,
 * render its results, or adapt them to build tooling.
 *
 * <p>The analyzer evaluates method names (0.30), class names (0.20), parameter names (0.25),
 * structural quality (0.15), and cohesion (0.10). Scores range from 0.0 (poor) to 1.0 (excellent).
 *
 * <p>Structural quality penalizes methods with more than 4 parameters and call depths beyond 5.
 * Cohesion measures whether methods within a class share a consistent vocabulary.
 *
 * <p>The analyzer also collects actionable {@link ClarityIssue} instances ranked by severity (HIGH,
 * MEDIUM, LOW) with suggestions for improvement.
 *
 * <p>Pass a {@link DomainVocabulary} to score in the project's own language — the vocabulary of its
 * committed glossary (ADR-012). Without one every dictionary is the built-in set, which is exactly
 * what a project with no glossary gets.
 *
 * <pre>{@code
 * var analyzer = new ClarityAnalyzer();
 * ClarityResult result = analyzer.analyze(traceTree);
 * System.out.println("Overall score: " + result.overallScore());
 * result.issues().forEach(System.out::println);
 * }</pre>
 *
 * @see ClarityResult
 * @see ClarityIssue
 */
public final class ClarityAnalyzer {

  private static final double METHOD_WEIGHT = 0.30;
  private static final double CLASS_WEIGHT = 0.20;
  private static final double PARAM_WEIGHT = 0.25;
  private static final double STRUCTURAL_WEIGHT = 0.15;
  private static final double COHESION_WEIGHT = 0.10;

  private static final double HIGH_SEVERITY_THRESHOLD = 0.20;
  private static final double MEDIUM_SEVERITY_THRESHOLD = 0.50;

  private final CohesionScorer cohesionScorer = new CohesionScorer();
  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final CollocationDictionary collocationDictionary = new CollocationDictionary();
  private final MethodNameScorer methodNameScorer;
  private final ClassNameScorer classNameScorer;
  private final ParameterNameScorer parameterNameScorer;
  private final AbbreviationDictionary abbreviationDictionary;
  private final ElementNoteComposer noteComposer;

  /** An analyzer with no project vocabulary — the built-in dictionaries alone. */
  public ClarityAnalyzer() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public ClarityAnalyzer(DomainVocabulary vocabulary) {
    this.methodNameScorer = new MethodNameScorer(vocabulary);
    this.classNameScorer = new ClassNameScorer(vocabulary);
    this.parameterNameScorer = new ParameterNameScorer(vocabulary);
    this.abbreviationDictionary = new AbbreviationDictionary(vocabulary);
    this.noteComposer = new ElementNoteComposer(vocabulary);
  }

  /**
   * Analyzes one trace tree and produces weighted scores plus issues.
   *
   * @param tree Trace tree whose identifiers should be scored.
   * @return Weighted result with dimension scores and ranked issues.
   */
  public ClarityResult analyze(TraceTree tree) {
    return analyze(tree, Set.of());
  }

  /**
   * Analyzes one trace tree, scoring the named property accessors on the noun rubric.
   *
   * <p>A record accessor's name is its component name — a noun — so judging it against the
   * verb+noun method standard mis-calibrates every record (~0.50 per accessor). Callers that know
   * which methods are generated accessors (e.g. {@link ClarityScanner} via {@code
   * Class.isRecord()}) pass their names here; those methods are scored like parameter names, so
   * {@code amount} scores well while {@code data} still scores poorly.
   *
   * @param tree Trace tree whose identifiers should be scored.
   * @param propertyAccessors Method names to score as nouns instead of verb+noun.
   * @return Weighted result with dimension scores and ranked issues.
   */
  public ClarityResult analyze(TraceTree tree, Set<String> propertyAccessors) {
    var nodes = flattenNodes(tree.roots());

    double methodScore = averageMethodScore(nodes, propertyAccessors);
    double classScore = averageClassNameScore(nodes);
    double paramScore = averageParamScore(nodes);
    double structuralScore = structuralFactor(nodes, tree);
    double cohesionScore = computeCohesionScore(nodes);

    double overall =
        methodScore * METHOD_WEIGHT
            + classScore * CLASS_WEIGHT
            + paramScore * PARAM_WEIGHT
            + structuralScore * STRUCTURAL_WEIGHT
            + cohesionScore * COHESION_WEIGHT;

    var issues = collectAndDeduplicateIssues(nodes, propertyAccessors);
    var elementNotes = collectElementNotes(nodes, propertyAccessors);

    return new ClarityResult(
        overall,
        methodScore,
        classScore,
        paramScore,
        structuralScore,
        cohesionScore,
        List.copyOf(issues),
        elementNotes);
  }

  /**
   * One teaching note per element at every score — methods (or properties for record accessors),
   * classes, and parameters — deduplicated by element. Each note carries the element's own score
   * from the same scorer its dimension uses, so a bare 0.86 is no longer unexplained.
   */
  private List<ElementNote> collectElementNotes(
      List<TraceNode> nodes, Set<String> propertyAccessors) {
    var notes = new ArrayList<ElementNote>();
    notes.addAll(methodElementNotes(nodes, propertyAccessors));
    notes.addAll(classElementNotes(nodes));
    notes.addAll(parameterElementNotes(nodes));
    return List.copyOf(notes);
  }

  private List<ElementNote> methodElementNotes(
      List<TraceNode> nodes, Set<String> propertyAccessors) {
    var notes = new ArrayList<ElementNote>();
    var seen = new HashSet<String>();
    for (var node : nodes) {
      var sig = node.signature();
      var element = sig.className() + "." + sig.methodName();
      if (!seen.add(element)) {
        continue;
      }
      boolean isAccessor = propertyAccessors.contains(sig.methodName());
      double score = scoreMethodName(sig.methodName(), propertyAccessors);
      var note =
          isAccessor
              ? noteComposer.propertyNote(sig.methodName())
              : noteComposer.methodNote(sig.methodName());
      notes.add(new ElementNote(isAccessor ? "property" : "method", element, score, note));
    }
    return notes;
  }

  private List<ElementNote> classElementNotes(List<TraceNode> nodes) {
    var notes = new ArrayList<ElementNote>();
    var seen = new HashSet<String>();
    for (var node : nodes) {
      var className = node.signature().className();
      if (!seen.add(className)) {
        continue;
      }
      notes.add(
          new ElementNote(
              "class",
              className,
              classNameScorer.score(className),
              noteComposer.classNote(className)));
    }
    return notes;
  }

  private List<ElementNote> parameterElementNotes(List<TraceNode> nodes) {
    var notes = new ArrayList<ElementNote>();
    var seen = new HashSet<String>();
    for (var node : nodes) {
      for (var param : node.signature().parameters()) {
        if (!seen.add(param.name())) {
          continue;
        }
        notes.add(
            new ElementNote(
                "parameter",
                param.name(),
                parameterNameScorer.score(param.name()),
                noteComposer.parameterNote(param.name())));
      }
    }
    return notes;
  }

  private double averageMethodScore(List<TraceNode> nodes, Set<String> propertyAccessors) {
    if (nodes.isEmpty()) return 0.0;
    return nodes.stream()
        .mapToDouble(n -> scoreMethodName(n.signature().methodName(), propertyAccessors))
        .average()
        .orElse(0.0);
  }

  private double scoreMethodName(String methodName, Set<String> propertyAccessors) {
    if (propertyAccessors.contains(methodName)) {
      return parameterNameScorer.score(methodName);
    }
    return methodNameScorer.score(methodName);
  }

  private double averageClassNameScore(List<TraceNode> nodes) {
    var uniqueClassNames = nodes.stream().map(n -> n.signature().className()).distinct().toList();
    if (uniqueClassNames.isEmpty()) return 0.0;
    return uniqueClassNames.stream().mapToDouble(classNameScorer::score).average().orElse(0.0);
  }

  private double averageParamScore(List<TraceNode> nodes) {
    var allParams = nodes.stream().flatMap(n -> n.signature().parameters().stream()).toList();
    if (allParams.isEmpty()) return 1.0;
    return allParams.stream()
        .mapToDouble(p -> parameterNameScorer.score(p.name()))
        .average()
        .orElse(0.0);
  }

  private double structuralFactor(List<TraceNode> nodes, TraceTree tree) {
    int maxParams = maxParamCount(nodes);
    int depth = maxDepth(tree.roots());

    double penalty = 0.0;
    if (maxParams > 4) penalty += 0.1 * (maxParams - 4);
    if (depth > 5) penalty += 0.05 * (depth - 5);

    return Math.max(0.0, 1.0 - penalty);
  }

  private double computeCohesionScore(List<TraceNode> nodes) {
    if (nodes.isEmpty()) return 0.7;

    Map<String, List<String>> classMethods =
        nodes.stream()
            .collect(
                Collectors.groupingBy(
                    n -> n.signature().className(),
                    Collectors.mapping(n -> n.signature().methodName(), Collectors.toList())));

    return cohesionScorer.scoreTrace(classMethods);
  }

  private int maxParamCount(List<TraceNode> nodes) {
    return nodes.stream().mapToInt(n -> n.signature().parameters().size()).max().orElse(0);
  }

  /**
   * The deepest level below {@code nodes} (the roots count as level 1), bounded and cycle-safe via
   * {@link TreeWalk} — a hand-built or replayed tree is not guaranteed acyclic, and a genuinely
   * deep one is ordinary for a recursive business method.
   */
  private int maxDepth(List<TraceNode> nodes) {
    if (nodes.isEmpty()) return 0;
    var max = new int[] {0};
    for (var root : nodes) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> max[0] = Math.max(max[0], depth + 1),
          (n, depth, reason) -> max[0] = Math.max(max[0], depth + 1));
    }
    return max[0];
  }

  private List<ClarityIssue> collectAndDeduplicateIssues(
      List<TraceNode> nodes, Set<String> propertyAccessors) {
    var rawIssues = new ArrayList<ClarityIssue>();
    rawIssues.addAll(findMethodNameIssues(nodes, propertyAccessors));
    rawIssues.addAll(findClassNameIssues(nodes));
    rawIssues.addAll(findParamNameIssues(nodes));
    rawIssues.addAll(findCollocationIssues(nodes, propertyAccessors));
    rawIssues.addAll(findAbbreviationIssues(nodes));

    return deduplicateAndRank(rawIssues);
  }

  /**
   * Unlike the score-threshold issue finders above, abbreviations are reported whenever a penalized
   * dictionary token appears — a name like {@code chkClaim} can score above the issue threshold
   * overall and still deserve the concrete rename the dictionary already knows ({@code chk →
   * check}). UNIVERSAL-tier tokens ({@code id}, {@code url}) are accepted usage and never flagged.
   */
  private List<ClarityIssue> findAbbreviationIssues(List<TraceNode> nodes) {
    var issues = new ArrayList<ClarityIssue>();
    var seenClasses = new HashSet<String>();
    for (var node : nodes) {
      var sig = node.signature();
      abbreviationIssueFor(sig.className() + "." + sig.methodName(), sig.methodName())
          .ifPresent(issues::add);
      if (seenClasses.add(sig.className())) {
        abbreviationIssueFor(sig.className(), sig.className()).ifPresent(issues::add);
      }
    }
    return issues;
  }

  private Optional<ClarityIssue> abbreviationIssueFor(String element, String identifier) {
    var spellOuts = new ArrayList<String>();
    var worst = ClarityIssue.Severity.LOW;
    for (var token : tokenizer.tokenize(identifier)) {
      var lower = token.toLowerCase(Locale.ROOT);
      var entry = abbreviationDictionary.lookup(lower);
      if (entry == null || entry.tier() == AbbreviationDictionary.Tier.UNIVERSAL) continue;
      spellOuts.add(lower + " → " + entry.expansion());
      if (entry.tier() == AbbreviationDictionary.Tier.AMBIGUOUS) {
        worst = ClarityIssue.Severity.MEDIUM;
      }
    }
    if (spellOuts.isEmpty()) return Optional.empty();
    return Optional.of(
        new ClarityIssue(
            "abbreviation",
            element,
            "Spell out: " + String.join(", ", spellOuts),
            worst,
            1,
            worst.weight()));
  }

  private List<ClarityIssue> deduplicateAndRank(List<ClarityIssue> issues) {
    var grouped = new HashMap<String, List<ClarityIssue>>();
    for (var issue : issues) {
      var key = issue.category() + "|" + issue.element();
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(issue);
    }

    return grouped.values().stream()
        .map(
            group -> {
              var first = group.get(0);
              return first.withOccurrences(group.size());
            })
        .sorted(Comparator.comparingDouble(ClarityIssue::impactScore).reversed())
        .toList();
  }

  private ClarityIssue.Severity classifySeverity(double score) {
    if (score <= HIGH_SEVERITY_THRESHOLD) return ClarityIssue.Severity.HIGH;
    if (score <= MEDIUM_SEVERITY_THRESHOLD) return ClarityIssue.Severity.MEDIUM;
    return ClarityIssue.Severity.LOW;
  }

  private List<ClarityIssue> findMethodNameIssues(
      List<TraceNode> nodes, Set<String> propertyAccessors) {
    var issues = new ArrayList<ClarityIssue>();
    for (var node : nodes) {
      var sig = node.signature();
      boolean isAccessor = propertyAccessors.contains(sig.methodName());
      double score = scoreMethodName(sig.methodName(), propertyAccessors);
      var severity = classifySeverity(score);
      if (score < MEDIUM_SEVERITY_THRESHOLD) {
        issues.add(
            new ClarityIssue(
                isAccessor ? "property-name" : "method-name",
                sig.className() + "." + sig.methodName(),
                isAccessor
                    ? "Name the component after its domain concept (e.g., customerId, orderAmount)"
                    : "Use a domain-specific verb+noun (e.g., calculateTotal, reserveInventory)",
                severity,
                1,
                severity.weight()));
      }
    }
    return issues;
  }

  private List<ClarityIssue> findClassNameIssues(List<TraceNode> nodes) {
    var issues = new ArrayList<ClarityIssue>();
    var seen = new HashSet<String>();
    for (var node : nodes) {
      var className = node.signature().className();
      if (seen.add(className)) {
        double score = classNameScorer.score(className);
        var severity = classifySeverity(score);
        if (score < MEDIUM_SEVERITY_THRESHOLD) {
          issues.add(
              new ClarityIssue(
                  "class-name",
                  className,
                  "Use a domain-specific name or a recognized pattern suffix (e.g., OrderService,"
                      + " PaymentGateway)",
                  severity,
                  1,
                  severity.weight()));
        }
      }
    }
    return issues;
  }

  private List<ClarityIssue> findParamNameIssues(List<TraceNode> nodes) {
    var issues = new ArrayList<ClarityIssue>();
    for (var node : nodes) {
      for (var param : node.signature().parameters()) {
        double score = parameterNameScorer.score(param.name());
        var severity = classifySeverity(score);
        if (score < MEDIUM_SEVERITY_THRESHOLD) {
          issues.add(
              new ClarityIssue(
                  "param-name",
                  param.name(),
                  "Use a domain-specific name (e.g., customerId, orderAmount)",
                  severity,
                  1,
                  severity.weight()));
        }
      }
    }
    return issues;
  }

  private List<ClarityIssue> findCollocationIssues(
      List<TraceNode> nodes, Set<String> propertyAccessors) {
    var issues = new ArrayList<ClarityIssue>();
    for (var node : nodes) {
      var sig = node.signature();
      if (propertyAccessors.contains(sig.methodName())) continue;
      var tokens = tokenizer.tokenize(sig.methodName());
      if (tokens.size() < 2) continue;

      var verb = tokens.get(0).toLowerCase();
      var noun = tokens.get(tokens.size() - 1).toLowerCase();
      var preferred = collocationDictionary.preferredVerbs(noun);
      if (preferred.isEmpty() || preferred.contains(verb)) continue;

      var capitalNoun = Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
      var suggestion =
          preferred.stream().sorted().map(v -> v + capitalNoun).collect(Collectors.joining(", "));
      issues.add(
          new ClarityIssue(
              "collocation",
              sig.className() + "." + sig.methodName(),
              "Consider: " + suggestion,
              ClarityIssue.Severity.LOW,
              1,
              ClarityIssue.Severity.LOW.weight()));
    }
    return issues;
  }

  /**
   * Every node under {@code nodes}, pre-order, bounded and cycle-safe via {@link TreeWalk}. A node
   * beyond the walk's bound still contributes its own name to the scores below; only its
   * unreachable descendants are excluded.
   */
  private List<TraceNode> flattenNodes(List<TraceNode> nodes) {
    var result = new ArrayList<TraceNode>();
    for (var root : nodes) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> result.add(n),
          (n, depth, reason) -> result.add(n));
    }
    return result;
  }
}
