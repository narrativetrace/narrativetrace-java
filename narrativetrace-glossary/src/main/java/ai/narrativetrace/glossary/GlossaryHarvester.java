/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Harvests glossary candidates from captured trace trees.
 *
 * <p>INTENT: Trace mode of the plan's harvest — v1 sources are method names (verb phrase + object
 * noun phrase), parameter names, class names (role suffix stripped), and exception type names
 * ({@code Exception}/{@code Error} stripped). Observations are aggregated and deterministically
 * ordered; the harvester makes no merge decisions.
 *
 * <p>Trace nodes carry only simple class names, so the package used for context resolution comes
 * from an injected resolver function (the suite hook supplies a real one; tests supply a map).
 * Non-identifier names on synthetic nodes are skipped — harvesting is best-effort by design.
 */
public final class GlossaryHarvester {

  private final TermNormalizer normalizer = new TermNormalizer();
  private final ContextResolver contextResolver;
  private final UnaryOperator<String> packageOf;

  /**
   * @param contextResolver resolver from package name to bounded context; must not be {@code null}
   * @param packageOf maps a simple class name to its package name ({@code ""} or {@code null} when
   *     unknown, which resolves to {@code _unassigned}); must not be {@code null}
   */
  public GlossaryHarvester(ContextResolver contextResolver, UnaryOperator<String> packageOf) {
    if (contextResolver == null) {
      throw new IllegalArgumentException("contextResolver must not be null");
    }
    if (packageOf == null) {
      throw new IllegalArgumentException("packageOf must not be null");
    }
    this.contextResolver = contextResolver;
    this.packageOf = packageOf;
  }

  /**
   * Harvests all candidate observations from the given trees.
   *
   * @param trees trace trees of one run; must not be {@code null}
   * @return aggregated observations sorted by {@code (context, phrase, kind, site)}
   */
  public HarvestResult harvest(List<TraceTree> trees) {
    return collect(trees, false);
  }

  /**
   * Harvests from trees built by scanning compiled classes, adding {@link TermKind#TEMPLATE}
   * candidates for {@code @Narrated} / {@code @OnError} text.
   *
   * <p>Templates are harvested <strong>only</strong> here. In a real trace, {@link
   * ai.narrativetrace.api.event.MethodSignature#narration()} holds the template with parameter
   * values already interpolated, so harvesting it would write runtime data into the committed
   * glossary. A statically scanned signature carries the raw annotation text, which is what a
   * per-locale template variant must key on.
   *
   * @param trees synthetic trees whose narration fields hold raw annotation text; must not be
   *     {@code null}
   * @return aggregated observations sorted by {@code (context, phrase, kind, site)}
   */
  public HarvestResult harvestStatic(List<TraceTree> trees) {
    return collect(trees, true);
  }

  private HarvestResult collect(List<TraceTree> trees, boolean includeTemplates) {
    if (trees == null) {
      throw new IllegalArgumentException("trees must not be null");
    }
    var occurrences = new HashMap<HarvestCandidate, Integer>();
    for (var tree : trees) {
      for (var root : tree.roots()) {
        TreeWalk.walk(
            root,
            TraceNode::children,
            (node, depth) -> harvestNode(node, occurrences, includeTemplates),
            (node, depth, reason) -> harvestNode(node, occurrences, includeTemplates));
      }
    }
    var candidates =
        occurrences.entrySet().stream()
            .map(e -> withOccurrences(e.getKey(), e.getValue()))
            .sorted(
                Comparator.comparing(HarvestCandidate::context)
                    .thenComparing(HarvestCandidate::phrase)
                    .thenComparing(HarvestCandidate::kind)
                    .thenComparing(HarvestCandidate::site))
            .toList();
    return new HarvestResult(candidates);
  }

  /**
   * Harvests one node's own candidates — class, method, parameters, exception, and (statically)
   * templates. Never descends into {@code node.children()} itself: {@link #collect} drives the
   * descent through {@link TreeWalk}, bounded and cycle-safe, so a hand-built or replayed tree with
   * a genuinely deep or cyclic call structure cannot crash the harvester the way ordinary recursion
   * once could.
   *
   * <p><b>@edgeCase</b> Called for a node beyond {@link TreeWalk#MAX_DEPTH} or already on the
   * current path too, same as any other node — it still contributes its own candidates, only its
   * unreachable descendants are excluded, matching {@code ClarityAnalyzer.flattenNodes}'s choice
   * for the same walker.
   */
  private void harvestNode(
      TraceNode node, Map<HarvestCandidate, Integer> occurrences, boolean includeTemplates) {
    var signature = node.signature();
    var className = signature.className();
    var context = contextResolver.resolve(packageOrEmpty(className));
    harvestClass(context, className, occurrences);
    harvestMethod(context, className, signature.methodName(), occurrences);
    harvestParameters(node, context, occurrences);
    harvestException(node, context, occurrences);
    if (includeTemplates) {
      harvestTemplates(node, context, occurrences);
    }
  }

  /** Records raw template text verbatim — normalizing it would destroy its placeholders. */
  private void harvestTemplates(
      TraceNode node, String context, Map<HarvestCandidate, Integer> occurrences) {
    var signature = node.signature();
    var site = signature.className() + "." + signature.methodName();
    observeTemplate(occurrences, context, signature.narration(), site);
    observeTemplate(occurrences, context, signature.errorContext(), site);
  }

  private static void observeTemplate(
      Map<HarvestCandidate, Integer> occurrences, String context, String template, String site) {
    if (template == null || template.isBlank()) {
      return;
    }
    observe(
        occurrences,
        context,
        new TermNormalizer.Candidate(template, TermKind.TEMPLATE),
        site,
        template);
  }

  private String packageOrEmpty(String className) {
    var packageName = packageOf.apply(className);
    return packageName == null ? "" : packageName;
  }

  private void harvestClass(
      String context, String className, Map<HarvestCandidate, Integer> occurrences) {
    if (!isIdentifier(className)) {
      return;
    }
    normalizer
        .classCandidate(className)
        .ifPresent(c -> observe(occurrences, context, c, className, className));
  }

  private void harvestMethod(
      String context,
      String className,
      String methodName,
      Map<HarvestCandidate, Integer> occurrences) {
    if (!isIdentifier(methodName)) {
      return;
    }
    var site = className + "." + methodName;
    for (var candidate : normalizer.methodCandidates(methodName)) {
      observe(occurrences, context, candidate, site, methodName);
    }
  }

  private void harvestParameters(
      TraceNode node, String context, Map<HarvestCandidate, Integer> occurrences) {
    var signature = node.signature();
    var site = signature.className() + "." + signature.methodName();
    for (var parameter : signature.parameters()) {
      if (isIdentifier(parameter.name())) {
        normalizer
            .parameterCandidate(parameter.name())
            .ifPresent(c -> observe(occurrences, context, c, site, parameter.name()));
      }
    }
  }

  private void harvestException(
      TraceNode node, String context, Map<HarvestCandidate, Integer> occurrences) {
    exceptionTypeName(node)
        .filter(GlossaryHarvester::isIdentifier)
        .flatMap(normalizer::exceptionCandidate)
        .ifPresent(
            c ->
                observe(
                    occurrences,
                    context,
                    c,
                    node.signature().className() + "." + node.signature().methodName(),
                    exceptionTypeName(node).orElseThrow()));
  }

  private static Optional<String> exceptionTypeName(TraceNode node) {
    if (node.outcome() instanceof TraceOutcome.Threw threw) {
      return Optional.of(threw.exception().getClass().getSimpleName());
    }
    return Optional.empty();
  }

  private static void observe(
      Map<HarvestCandidate, Integer> occurrences,
      String context,
      TermNormalizer.Candidate candidate,
      String site,
      String identifier) {
    var key =
        new HarvestCandidate(context, candidate.phrase(), candidate.kind(), site, identifier, 1);
    occurrences.merge(key, 1, Integer::sum);
  }

  private static HarvestCandidate withOccurrences(HarvestCandidate key, int occurrences) {
    return new HarvestCandidate(
        key.context(), key.phrase(), key.kind(), key.site(), key.identifier(), occurrences);
  }

  /** Best-effort filter: synthetic node names like {@code <launcher>} are not harvestable. */
  /**
   * Whether a name is a Java identifier the normalizer can read.
   *
   * <p><b>@edgeCase</b> "Is a Java identifier" is not enough on its own: {@code __} and {@code $$}
   * are legal identifiers with no word in them, and bytecode is full of them (a Kotlin
   * unused-parameter placeholder, an obfuscated jar, a synthetic accessor). Requiring one letter or
   * digit is what keeps a name-less name out of a normalizer whose contract promises a phrase.
   */
  private static boolean isIdentifier(String name) {
    if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    return name.chars().allMatch(Character::isJavaIdentifierPart)
        && name.chars().anyMatch(Character::isLetterOrDigit);
  }
}
