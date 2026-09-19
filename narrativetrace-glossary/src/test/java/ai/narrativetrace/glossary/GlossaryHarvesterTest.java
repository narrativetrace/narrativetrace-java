/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class GlossaryHarvesterTest {

  private static final UnaryOperator<String> PACKAGES =
      className -> className.startsWith("Overdraft") ? "com.acme.billing" : "com.other";

  private final GlossaryHarvester harvester =
      new GlossaryHarvester(
          new ContextResolver(
              new Glossary(
                  1,
                  Map.of(
                      "billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
                  List.of())),
          PACKAGES);

  private static TraceTree tree(TraceNode... roots) {
    return new DefaultTraceTree(List.of(roots));
  }

  private static TraceNode node(String className, String methodName, String... parameterNames) {
    var parameters =
        java.util.Arrays.stream(parameterNames)
            .map(name -> new ParameterCapture(name, "\"v\"", false))
            .toList();
    return new TraceNode(new MethodSignature(className, methodName, parameters), List.of(), null);
  }

  private static TraceNode narratedNode(String className, String methodName, String narration) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of(), narration, null), List.of(), null);
  }

  @Test
  void staticHarvestKeepsTemplatesVerbatimSoPlaceholdersSurvive() {
    var result =
        harvester.harvestStatic(
            List.of(
                tree(
                    narratedNode(
                        "OverdraftService", "openAccount", "Opening overdraft for {customerId}"))));

    assertThat(result.candidates())
        .contains(
            new HarvestCandidate(
                "billing",
                "Opening overdraft for {customerId}",
                TermKind.TEMPLATE,
                "OverdraftService.openAccount",
                "Opening overdraft for {customerId}",
                1));
  }

  @Test
  void staticHarvestTakesOnErrorTemplatesBesideNarrationTemplates() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OverdraftService",
                "openAccount",
                List.of(),
                "Opening overdraft for {customerId}",
                "Overdraft refused for {customerId}"),
            List.of(),
            null);

    var result = harvester.harvestStatic(List.of(tree(node)));

    assertThat(result.candidates())
        .filteredOn(candidate -> candidate.kind() == TermKind.TEMPLATE)
        .extracting(HarvestCandidate::phrase)
        .containsExactlyInAnyOrder(
            "Opening overdraft for {customerId}", "Overdraft refused for {customerId}");
  }

  @Test
  void traceHarvestNeverTakesTemplatesBecauseTheyHoldResolvedValues() {
    var result =
        harvester.harvest(
            List.of(
                tree(
                    narratedNode(
                        "OverdraftService", "openAccount", "Opening overdraft for C-123"))));

    assertThat(result.candidates())
        .extracting(HarvestCandidate::kind)
        .doesNotContain(TermKind.TEMPLATE);
  }

  @Test
  void harvestsMethodParameterAndClassCandidatesWithContextAndSite() {
    var result =
        harvester.harvest(
            List.of(
                tree(node("OverdraftService", "openAccountWithOverdraft", "overdraftAccountId"))));

    assertThat(result.candidates())
        .containsExactly(
            new HarvestCandidate(
                "billing",
                "account with overdraft",
                TermKind.NOUN_PHRASE,
                "OverdraftService.openAccountWithOverdraft",
                "openAccountWithOverdraft",
                1),
            new HarvestCandidate(
                "billing",
                "open account with overdraft",
                TermKind.VERB_PHRASE,
                "OverdraftService.openAccountWithOverdraft",
                "openAccountWithOverdraft",
                1),
            new HarvestCandidate(
                "billing", "overdraft", TermKind.WORD, "OverdraftService", "OverdraftService", 1),
            new HarvestCandidate(
                "billing",
                "overdraft account",
                TermKind.NOUN_PHRASE,
                "OverdraftService.openAccountWithOverdraft",
                "overdraftAccountId",
                1));
  }

  @Test
  void harvestsExceptionTypeFromFailedNode() {
    var failed =
        new TraceNode(
            new MethodSignature("OverdraftService", "charge", List.of()),
            List.of(),
            new ai.narrativetrace.api.event.TraceOutcome.Threw(new IllegalStateException("boom")));

    var result = harvester.harvest(List.of(tree(failed)));

    assertThat(result.candidates())
        .contains(
            new HarvestCandidate(
                "billing",
                "illegal state",
                TermKind.NOUN_PHRASE,
                "OverdraftService.charge",
                "IllegalStateException",
                1));
  }

  @Test
  void walksNestedChildrenAndAggregatesRepeatedObservations() {
    var leaf = node("OverdraftService", "charge");
    var parent =
        new TraceNode(
            new MethodSignature("OverdraftService", "charge", List.of()), List.of(leaf), null);

    var result = harvester.harvest(List.of(tree(parent)));

    assertThat(result.candidates())
        .contains(
            new HarvestCandidate(
                "billing", "charge", TermKind.VERB_PHRASE, "OverdraftService.charge", "charge", 2));
  }

  @Test
  void skipsSyntheticNonIdentifierNames() {
    var synthetic = node("<launcher>", "<fork>");

    var result = harvester.harvest(List.of(tree(synthetic)));

    assertThat(result.candidates()).isEmpty();
  }

  @Test
  void skipsEmptyAndInteriorPunctuatedNames() {
    assertThat(harvester.harvest(List.of(tree(node("", "")))).candidates()).isEmpty();
    assertThat(harvester.harvest(List.of(tree(node("Foo-Bar", "bad-name")))).candidates())
        .isEmpty();
  }

  @Test
  void treatsNullPackageLookupAsUnassigned() {
    var nullPackages =
        new GlossaryHarvester(
            new ContextResolver(new Glossary(1, Map.of(), List.of())), className -> null);

    var result = nullPackages.harvest(List.of(tree(node("TicketDesk", "escalate"))));

    assertThat(result.candidates()).isNotEmpty();
    assertThat(result.candidates())
        .allSatisfy(candidate -> assertThat(candidate.context()).isEqualTo("_unassigned"));
  }

  @Test
  void rejectsNullArguments() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> harvester.harvest(null))
        .withMessageContaining("trees");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> new GlossaryHarvester(null, name -> ""))
        .withMessageContaining("contextResolver");
    var resolver = new ContextResolver(new Glossary(1, Map.of(), List.of()));
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> new GlossaryHarvester(resolver, null))
        .withMessageContaining("packageOf");
  }

  @Test
  void resolvesUnknownPackagesToUnassigned() {
    var result = harvester.harvest(List.of(tree(node("TicketDesk", "ticket"))));

    assertThat(result.candidates())
        .allSatisfy(candidate -> assertThat(candidate.context()).isEqualTo("_unassigned"));
    assertThat(result.candidates()).isNotEmpty();
  }

  /**
   * Regression: {@code __} and {@code $$} are legal Java identifiers with no word in them, so the
   * harvester's identifier filter let them through to a normalizer whose contract promises a
   * phrase. Bytecode produces them (a Kotlin unused-parameter placeholder, an obfuscated jar, a
   * synthetic accessor), so a real trace could carry one. Found by the security suite.
   */
  @Test
  void aClassAndMethodWithNoReadableWordAreSkippedRatherThanCrashing() {
    var node =
        new TraceNode(
            new MethodSignature("__", "$$", List.of(new ParameterCapture("__", "\"v\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1L);

    assertThat(harvester.harvest(List.of(tree(node))).candidates()).isEmpty();
  }

  // --- Cross-runtime residual recursion: harvesting walked TraceNode.children() by ordinary
  // call-stack recursion, the same crash risk every core/clarity/diagrams renderer already closed
  // via ai.narrativetrace.core.tree.TreeWalk. ---

  @Test
  void harvestSurvivesAVeryDeepLegitimateChainWithoutStackOverflow() {
    var current = node("Leaf", "leafMethod");
    var depth = 5_000;
    for (var i = 0; i < depth; i++) {
      current =
          new TraceNode(
              new MethodSignature("Service" + i, "callMethod", List.of()), List.of(current), null);
    }
    var tree = tree(current);

    assertThatCode(() -> harvester.harvest(List.of(tree))).doesNotThrowAnyException();
  }

  @Test
  void harvestTruncatesAChainDeeperThanMaxDepthInsteadOfOverflowing() {
    var current = node("Leaf", "leafMethod");
    var depth = ai.narrativetrace.core.tree.TreeWalk.MAX_DEPTH + 50;
    for (var i = 0; i < depth; i++) {
      current =
          new TraceNode(
              new MethodSignature("Service" + i, "callMethod", List.of()), List.of(current), null);
    }
    var tree = tree(current);

    assertThatCode(() -> harvester.harvest(List.of(tree))).doesNotThrowAnyException();
  }

  @Test
  void harvestTerminatesOnASelfHoldingCyclicNodeInsteadOfHanging() {
    var ownChildren = new java.util.ArrayList<TraceNode>();
    var cyclic = new TraceNode(new MethodSignature("Loopy", "recur", List.of()), ownChildren, null);
    ownChildren.add(cyclic); // self-holding cycle
    var tree = tree(cyclic);

    assertThatCode(() -> harvester.harvest(List.of(tree))).doesNotThrowAnyException();
  }

  // --- Harvest and translation must resolve a node's context by the same rule. A term filed under
  // one context and looked up under another produces a gap no curation can close. ---

  private static final Glossary SHOP_AND_BILLING =
      new Glossary(
          1,
          Map.of(
              "shop",
              new BoundedContext("shop", List.of("com.acme.shop"), null),
              "billing",
              new BoundedContext("billing", List.of("com.acme.billing"), null),
              "_unassigned",
              new BoundedContext("_unassigned", List.of(), null)),
          List.of());

  private static TraceNode orderNode(String capturedPackage) {
    return new TraceNode(
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, capturedPackage),
        List.of(),
        null);
  }

  private static ai.narrativetrace.core.export.CanonicalEntry orderEntry(String capturedPackage) {
    return ai.narrativetrace.core.export.CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.000Z")
        .level("trace")
        .message("enter")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId("0123456789abcdef")
        .codeNamespace("OrderService")
        .codeFunction("placeOrder")
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.2")
        .ntPackage(capturedPackage)
        .build();
  }

  private static Glossary withPlaceOrderCuratedIn(String context) {
    return new Glossary(
        SHOP_AND_BILLING.schemaVersion(),
        SHOP_AND_BILLING.contexts(),
        List.of(
            new GlossaryTerm(
                "place order",
                context,
                TermKind.VERB_PHRASE,
                TermStatus.CURATED,
                null,
                Map.of("es", "realizar pedido"),
                List.of(),
                List.of(),
                java.time.LocalDate.of(2026, 8, 11))));
  }

  /**
   * Harvests one node, curates its verb phrase in exactly the context the harvest filed it under,
   * then renders the same call: the translation must land. It only can when both halves resolved
   * the context the same way.
   */
  private static void assertHarvestAndTranslationAgree(
      UnaryOperator<String> packageOf, String capturedPackage) {
    var harvested =
        new GlossaryHarvester(new ContextResolver(SHOP_AND_BILLING), packageOf)
            .harvest(List.of(tree(orderNode(capturedPackage))));
    var context =
        harvested.candidates().stream()
            .filter(candidate -> "place order".equals(candidate.phrase()))
            .map(HarvestCandidate::context)
            .findFirst()
            .orElseThrow();

    var view = new TraceTranslationView(withPlaceOrderCuratedIn(context), packageOf);

    assertThat(view.render(List.of(orderEntry(capturedPackage)), "es"))
        .as("harvested under '%s'", context)
        .contains("realizar pedido (placeOrder)");
  }

  @Test
  void harvestAndTranslationAgreeWhenTheIndexResolvesTheSimpleName() {
    assertHarvestAndTranslationAgree(className -> "com.acme.shop", "com.acme.shop");
  }

  @Test
  void harvestAndTranslationAgreeWhenTheClassShipsInAJarTheIndexNeverScanned() {
    assertHarvestAndTranslationAgree(className -> null, "com.acme.shop");
  }

  @Test
  void harvestAndTranslationAgreeWhenTheSimpleNameIsAmbiguousInTheIndex() {
    // Two classes share the simple name, so the index answers for the wrong one (or, as the real
    // index does, not at all). The package captured at the site outranks it either way.
    assertHarvestAndTranslationAgree(className -> "com.acme.billing", "com.acme.shop");
  }

  @Test
  void aPreSchemaOneTwoSignatureWithNoCapturedPackageStillFallsBackToTheIndex() {
    assertHarvestAndTranslationAgree(className -> "com.acme.shop", null);
  }
}
