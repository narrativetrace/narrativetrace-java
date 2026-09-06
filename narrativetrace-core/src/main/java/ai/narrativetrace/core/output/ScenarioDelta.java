/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

/**
 * One scenario's structural status against its last green {@code .nt} artifact.
 *
 * <p>INTENT: The unit the trace writer reports upward after each test — the suite footer aggregates
 * these into the post-run delta line, and the failure surface prints the diff of the failing
 * scenario. Produced beside the artifact write so baseline reading happens exactly once.
 *
 * @param scenario the humanized scenario name (the {@code scenario:} header value)
 * @param kind NEW (no baseline yet), UNCHANGED (byte-identical), or CHANGED
 * @param summary compact change summary ({@code +4 calls X.y}); empty unless CHANGED
 * @param diff readable line diff against the baseline; empty unless CHANGED
 */
public record ScenarioDelta(String scenario, Kind kind, String summary, String diff) {

  /** How the scenario's structure relates to its last green artifact. */
  public enum Kind {
    NEW,
    UNCHANGED,
    CHANGED
  }

  /**
   * Classifies the current artifact against the baseline; a null baseline means no last green
   * artifact exists yet — the scenario is NEW.
   */
  public static ScenarioDelta of(String scenario, String baseline, String current) {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    if (current == null) {
      throw new IllegalArgumentException("current must not be null");
    }
    if (baseline == null) {
      return new ScenarioDelta(scenario, Kind.NEW, "", "");
    }
    var structural = StructuralDelta.between(baseline, current);
    if (structural.unchanged()) {
      return new ScenarioDelta(scenario, Kind.UNCHANGED, "", "");
    }
    return new ScenarioDelta(scenario, Kind.CHANGED, structural.summary(), structural.diff());
  }
}
