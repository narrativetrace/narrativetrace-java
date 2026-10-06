/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A case directory may declare which fixture to scaffold via {@code case.json}; a case without one
 * defaults to the skill's own canonical fixture, {@code sixty-seconds} — this runtime's analogue of
 * the TypeScript reference's default, {@code examples/sixty-seconds}.
 */
public final class CaseFixture {

  private static final String DEFAULT_FIXTURE = "sixty-seconds";
  private static final String FIELD = "fixture";

  private CaseFixture() {}

  /**
   * Repo-root-relative path to the fixture {@code caseDir} names, or the default when it names
   * none.
   *
   * @llmNote The two "no value" cases are deliberately NOT the same: no manifest at all is the
   *     default fixture, while a manifest that carries no {@code fixture} field is a case file
   *     somebody meant to fill in, and that is an error.
   */
  public static String fixtureFor(Path caseDir) {
    Path manifest = CaseManifest.manifestPath(caseDir);
    if (!Files.isRegularFile(manifest)) {
      return DEFAULT_FIXTURE;
    }
    return CaseManifest.stringField(caseDir, FIELD)
        .orElseThrow(() -> new IllegalArgumentException(manifest + " has no \"fixture\" field"));
  }
}
