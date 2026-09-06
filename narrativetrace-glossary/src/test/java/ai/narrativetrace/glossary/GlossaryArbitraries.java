/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * Shared jqwik generators for structurally valid glossaries and harvests.
 *
 * <p>Generation is constructive — every sample satisfies the {@link Glossary} invariants by design:
 * term texts are globally unique, and alias texts always end in {@code " alias"} while canonical
 * texts never do, so alias/term disjointness holds without filtering.
 */
final class GlossaryArbitraries {

  private GlossaryArbitraries() {}

  static Arbitrary<Glossary> glossaries() {
    var contextNames =
        Arbitraries.of("billing", "support", "shipping", "_unassigned")
            .set()
            .ofMinSize(1)
            .ofMaxSize(3);
    return contextNames.flatMap(
        names -> {
          var contexts =
              names.stream()
                  .collect(Collectors.toMap(name -> name, GlossaryArbitraries::boundedContext));
          return Combinators.combine(
                  termIn(List.copyOf(names)).list().uniqueElements(GlossaryTerm::term).ofMaxSize(6),
                  abbreviations())
              .as((termList, abbreviations) -> new Glossary(1, contexts, abbreviations, termList));
        });
  }

  /** May be empty, so schema-1 files stay in the sample space alongside schema-2 ones. */
  private static Arbitrary<Map<String, String>> abbreviations() {
    return Arbitraries.maps(
            Arbitraries.of("fx", "calc", "acc", "cfg"),
            Arbitraries.of("foreign exchange", "calculate", "account", "config \"file\""))
        .ofMaxSize(3);
  }

  /** Candidate phrases overlap glossary term and alias texts to exercise every merge branch. */
  static Arbitrary<HarvestResult> harvests() {
    var candidates =
        Combinators.combine(
                Arbitraries.of("billing", "support", "shipping", "_unassigned", "warehouse"),
                Arbitraries.of(
                    "alpha",
                    "beta",
                    "overdraft account",
                    "payment plan",
                    "old phrasing alias",
                    "legacy wording alias",
                    "fresh term"),
                Arbitraries.of(TermKind.class),
                Arbitraries.of("A.a", "B.b", "C.c"),
                Arbitraries.of("someIdentifier", "otherIdentifier"))
            .as(
                (context, phrase, kind, site, identifier) ->
                    new HarvestCandidate(context, phrase, kind, site, identifier, 1));
    return candidates
        .list()
        .uniqueElements(c -> c.context() + "|" + c.phrase() + "|" + c.kind() + "|" + c.site())
        .ofMaxSize(8)
        .map(HarvestResult::new);
  }

  private static BoundedContext boundedContext(String name) {
    var packages = name.startsWith("_") ? List.<String>of() : List.of("com.acme." + name);
    return new BoundedContext(name, packages, "Context \"" + name + "\"");
  }

  private static Arbitrary<GlossaryTerm> termIn(List<String> contextNames) {
    var identity =
        Combinators.combine(
                Arbitraries.of(
                    "alpha",
                    "beta",
                    "overdraft account",
                    "payment plan",
                    "café crédit",
                    "say \"hi\"\tterm"),
                Arbitraries.of(contextNames),
                Arbitraries.of(TermKind.class),
                Arbitraries.of(TermStatus.class))
            .as(Tuple::of);
    var curation =
        Combinators.combine(
                Arbitraries.of("Means something.", "Multi\nline é", "x").injectNull(0.3),
                Arbitraries.maps(
                        Arbitraries.of("es", "de", "fr"), Arbitraries.of("uno", "dós", "z\"z"))
                    .ofMaxSize(2),
                synonyms(),
                Arbitraries.of("a.B.c", "d.E.f").list().ofMaxSize(2),
                Arbitraries.of(LocalDate.of(2026, 8, 11), LocalDate.of(2025, 1, 31)))
            .as(Tuple::of);
    return Combinators.combine(identity, curation)
        .as(
            (id, cur) ->
                new GlossaryTerm(
                    id.get1(),
                    id.get2(),
                    id.get3(),
                    id.get4(),
                    cur.get1(),
                    cur.get2(),
                    cur.get3(),
                    cur.get4(),
                    cur.get5()));
  }

  private static Arbitrary<List<SynonymAlias>> synonyms() {
    return Combinators.combine(
            Arbitraries.of("old phrasing", "legacy wording", "v1 term"),
            Arbitraries.of("historic", "renamed 2025").injectNull(0.5))
        .as((alias, note) -> new SynonymAlias(alias + " alias", note))
        .list()
        .uniqueElements(SynonymAlias::alias)
        .ofMaxSize(2);
  }
}
