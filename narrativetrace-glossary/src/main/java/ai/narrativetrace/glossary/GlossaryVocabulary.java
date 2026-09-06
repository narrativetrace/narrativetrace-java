/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.clarity.DomainVocabulary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

/**
 * Reads a repository's committed glossary as the vocabulary clarity scores with.
 *
 * <p>INTENT: One file, one review workflow. The glossary a team already curates (ADR-012) is the
 * only place a project declares domain vocabulary — there is no second clarity dictionary file to
 * keep in sync. This class is the whole bridge: {@link Glossary} in, {@link DomainVocabulary} out,
 * plus the committed-file lookup the test-time and scan-time entry points share.
 *
 * <p>Four rules make the mapping trustworthy:
 *
 * <ul>
 *   <li><b>Only the committed file counts.</b> Nothing harvested during the run itself is
 *       consulted; the commit is the human approval, and a self-expanding vocabulary would make
 *       scores non-deterministic and self-certifying.
 *   <li><b>Accepted shorthand comes from the {@code abbreviations} section and nowhere else.</b> A
 *       token that merely appears inside a committed phrase is not accepted shorthand — nobody read
 *       it, so "the commit is the human approval" does not hold for it.
 *   <li><b>Deprecated synonyms are not vocabulary.</b> An alias exists to be flagged, so promoting
 *       it to domain vocabulary would silence the very issue the glossary declares it for.
 *   <li><b>{@link TermStatus#STALE} terms are not vocabulary.</b> Marking a term stale is an
 *       explicit human statement that the word left the domain.
 * </ul>
 *
 * <p>Bounded contexts are flattened: clarity scores identifiers, which carry no package, so every
 * context's vocabulary applies everywhere. {@link TermKind#TEMPLATE} entries are skipped — their
 * text is raw narration, not a word.
 */
public final class GlossaryVocabulary {

  /** Name of the committed glossary file, as {@link GlossarySuiteHarvest} writes it. */
  static final String GLOSSARY_FILE = "glossary.json";

  private GlossaryVocabulary() {}

  /**
   * Maps a glossary onto the vocabulary the clarity scorers consult.
   *
   * <p>A verb phrase contributes its leading verb as a domain verb and the rest as domain nouns
   * ({@code settle trade} → verb {@code settle}, noun {@code trade}); words and noun phrases
   * contribute every token as a domain noun. Multi-word terms therefore still teach, one token at a
   * time, which is the granularity identifiers are scored at. The glossary's {@code abbreviations}
   * section maps across unchanged — it is already token-to-expansion.
   *
   * @param glossary the committed glossary; must not be {@code null}
   * @return the project's declared vocabulary
   */
  public static DomainVocabulary of(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    var verbs = new HashSet<String>();
    var nouns = new HashSet<String>();
    for (var term : glossary.terms()) {
      collect(term, verbs, nouns);
    }
    return DomainVocabulary.of(verbs, nouns, glossary.abbreviations());
  }

  private static void collect(GlossaryTerm term, HashSet<String> verbs, HashSet<String> nouns) {
    if (term.status() == TermStatus.STALE || term.kind() == TermKind.TEMPLATE) {
      return;
    }
    var tokens = List.of(term.term().split(" "));
    if (term.kind() == TermKind.VERB_PHRASE) {
      verbs.add(tokens.get(0));
      nouns.addAll(tokens.subList(1, tokens.size()));
      return;
    }
    nouns.addAll(tokens);
  }

  /**
   * Reads the committed glossary from the directory holding it.
   *
   * <p><b>@edgeCase</b> A missing directory, a {@code null} directory, and a directory with no
   * {@code glossary.json} all mean the same thing — the project has declared no vocabulary — and
   * yield {@link DomainVocabulary#empty()}. A glossary that exists but cannot be parsed is a
   * different matter and throws, mirroring {@link GlossaryJsonReader}'s fail-loudly contract: a
   * committed file with a typo is a defect, not an absence.
   *
   * @param glossaryDir directory holding the committed {@code glossary.json}, or {@code null}
   * @return the project's declared vocabulary, empty when no glossary is committed
   * @throws IllegalArgumentException when the committed glossary is malformed
   * @throws UncheckedIOException when the committed glossary cannot be read
   */
  public static DomainVocabulary from(Path glossaryDir) {
    if (glossaryDir == null) {
      return DomainVocabulary.empty();
    }
    var file = glossaryDir.resolve(GLOSSARY_FILE);
    if (!Files.isRegularFile(file)) {
      return DomainVocabulary.empty();
    }
    try {
      return of(new GlossaryJsonReader().read(Files.readString(file)));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read glossary at " + file, e);
    }
  }
}
