/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

/**
 * One {@code contract.yaml} entry, mirroring buildSrc's {@code
 * ai.narrativetrace.build.ContractEntry} (the two cannot share code: this project consumes only
 * registry artifacts and never depends on the root build's {@code buildSrc}). {@code expect} is the
 * single observed string the named probe must produce for the claim to hold — the YAML spells it
 * {@code documented_default} or {@code expected_effect} depending on {@code kind}; both land here
 * as one field, same as the Kotlin twin.
 *
 * @llmNote There is no version field. An entry describes the code it was committed with, and {@code
 *     scripts/contract-check.sh} hands this runner the contract as of the tag whose artifact it
 *     installed, so a claim and the artifact it is checked against already come from one commit. A
 *     file from an older release may still carry a {@code since:} key — {@link ContractYaml} reads
 *     only the keys this record names and ignores the rest.
 */
public record ContractEntry(
    String id,
    String kind,
    String page,
    String claim,
    String expect,
    String probe,
    String coordinate,
    String registry) {}
