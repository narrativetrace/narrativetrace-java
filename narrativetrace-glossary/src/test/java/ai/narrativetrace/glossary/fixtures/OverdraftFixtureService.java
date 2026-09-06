/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.OnError;

/**
 * Annotated fixture for the static scanner, deliberately in its own package.
 *
 * <p>The scanner reads annotations reflectively, and same-package reflection hides access problems
 * that only appear across package boundaries.
 */
public class OverdraftFixtureService {

  @Narrated("Opening overdraft account for {customerId}")
  @OnError("Overdraft refused for {customerId}")
  public String openOverdraftAccount(String customerId) {
    return customerId;
  }

  public void closeOverdraftAccount(String overdraftAccountId) {
    // no annotations: harvested for names only
  }
}
