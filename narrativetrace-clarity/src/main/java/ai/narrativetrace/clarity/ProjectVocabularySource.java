/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.nio.file.Path;

/**
 * Resolves the vocabulary a project has committed, given the directory that holds it.
 *
 * <p>INTENT: The seam that lets {@link ClarityScannerMain} score with a project's glossary without
 * the clarity module depending on the glossary module — that dependency runs the other way, and a
 * cycle is not allowed. The glossary module supplies an implementation and its own thin entry
 * point; anyone running the scanner without a glossary gets {@link #none()}.
 */
@FunctionalInterface
public interface ProjectVocabularySource {

  /**
   * Resolves the project's declared vocabulary.
   *
   * @param glossaryDir directory holding the committed glossary, or {@code null} when the caller
   *     named none
   * @return the vocabulary, or {@link DomainVocabulary#empty()} when the project has committed none
   */
  DomainVocabulary resolve(Path glossaryDir);

  /** The source of a run that has no glossary to consult — always the empty vocabulary. */
  static ProjectVocabularySource none() {
    return glossaryDir -> DomainVocabulary.empty();
  }
}
