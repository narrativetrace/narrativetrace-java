/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context.catdd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that checks the class invariant before and after each test.
 *
 * <p>The test class must implement {@link ContractVerifiable}. The extension calls {@link
 * ContractVerifiable#checkInvariant()} at both entry and exit of every test method.
 */
public class InvariantCheckExtension implements BeforeEachCallback, AfterEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    check(context, "before");
  }

  @Override
  public void afterEach(ExtensionContext context) {
    check(context, "after");
  }

  private void check(ExtensionContext context, String phase) {
    context
        .getTestInstance()
        .ifPresent(
            instance -> {
              if (instance instanceof ContractVerifiable<?> cv) {
                assertTrue(
                    cv.checkInvariant(),
                    "Invariant violated " + phase + " " + context.getDisplayName());
              }
            });
  }
}
