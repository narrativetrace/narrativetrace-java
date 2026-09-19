/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

import java.util.List;

/** The near miss: an ordinary class whose own {@code values()} is vocabulary, not plumbing. */
public class ValuesFixture {

  public List<String> values() {
    return List.of();
  }

  public ValuesFixture valueOf(String reading) {
    return this;
  }
}
