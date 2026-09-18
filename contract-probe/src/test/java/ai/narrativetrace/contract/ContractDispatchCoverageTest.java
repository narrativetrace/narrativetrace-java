/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The guard that should have caught {@code probed-run-name-console-footer} and {@code
 * probed-run-name-manifest-field}: both were added to {@code documentation/contract.yaml} with the
 * "run has a name" feature, both had their probe class written, and neither was ever wired into
 * {@link ContractRunner}'s dispatch. The nightly contract gate did not report them as failing — it
 * crashed on an {@code IllegalStateException} partway through the run, which is a far worse signal
 * than a FAILS line, and one no amount of rerunning would explain.
 *
 * <p>Derived from the contract file itself rather than from a list of ids kept here (release rule
 * 5): the ids come from the same YAML {@code runContract} reads, resolved through the same {@code
 * contractYaml} property, so an entry added tomorrow is covered by this test the moment it lands.
 * {@code runContract} depends on {@code test} (contract-probe/build.gradle.kts), so the gate cannot
 * run without this having run first.
 */
class ContractDispatchCoverageTest {

  /** The same file {@code runContract} is pointed at — the build passes the property through. */
  private static File contractFile() {
    return new File(System.getProperty("contractYaml", "../documentation/contract.yaml"));
  }

  @Test
  void everyContractEntryHasAProbeDispatch() throws IOException {
    List<ContractEntry> entries = ContractYaml.read(contractFile());
    assertFalse(entries.isEmpty(), "read no entries from " + contractFile());

    List<String> undispatched =
        entries.stream()
            .map(ContractEntry::id)
            .filter(id -> !ContractRunner.dispatchedEntryIds().contains(id))
            .toList();

    assertEquals(
        List.of(),
        undispatched,
        "contract.yaml entries with no probe dispatch — add each in ContractRunner.PROBES, "
            + "or the nightly gate crashes on it instead of reporting a verdict");
  }

  /**
   * The other direction: a dispatch for an id the contract no longer carries is dead code that
   * silently never runs, and the pair of assertions is what makes the two lists one list.
   */
  @Test
  void everyProbeDispatchAnswersAContractEntry() throws IOException {
    List<String> ids = ContractYaml.read(contractFile()).stream().map(ContractEntry::id).toList();

    List<String> orphaned =
        ContractRunner.dispatchedEntryIds().stream()
            .filter(id -> !ids.contains(id))
            .sorted()
            .toList();

    assertEquals(List.of(), orphaned, "probe dispatches for ids no longer in contract.yaml");
  }
}
