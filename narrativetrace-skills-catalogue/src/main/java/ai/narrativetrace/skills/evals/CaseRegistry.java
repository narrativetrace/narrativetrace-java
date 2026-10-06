/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * INTENT: which registry, if any, delivered a case's skill pages — read from the case's own {@code
 * case.json} ({@code "registry": "npx-skills"}), beside the fixture it names.
 *
 * <p>Most cases declare none, and that is the ordinary arrangement: the harness copies this
 * repository's rendered pages into the scratch project itself and the case is about the prompt. A
 * case that DOES declare one is handed its pages by the registry tool instead, because what such a
 * case measures is the state a registry leaves behind.
 *
 * @llmNote An id outside {@link RegistryPreStep}'s vocabulary is an error, never an absent
 *     pre-step: a registry case that quietly ran no pre-step would pass as a plain prompt replay.
 */
public final class CaseRegistry {

  private static final String FIELD = "registry";

  private CaseRegistry() {}

  /** The delivery {@code caseDir} declares, or empty when it declares none. */
  public static Optional<RegistryPreStep> registryFor(Path caseDir) {
    Optional<String> declared = CaseManifest.stringField(caseDir, FIELD);
    if (declared.isEmpty()) {
      return Optional.empty();
    }
    String id = declared.get();
    return Optional.of(
        RegistryPreStep.parse(id)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        CaseManifest.manifestPath(caseDir)
                            + " declares \"registry\": \""
                            + id
                            + "\", which is not one of "
                            + vocabulary())));
  }

  private static String vocabulary() {
    return Arrays.stream(RegistryPreStep.values())
        .map(RegistryPreStep::id)
        .collect(Collectors.joining(", "));
  }
}
