/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.init.catdd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that checks a class invariant before and after every test.
 *
 * <p>The test class implements {@link ContractVerifiable}; a test class that does not is ignored,
 * so the extension is safe to register anywhere.
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
              if (instance instanceof ContractVerifiable<?> verifiable) {
                assertTrue(
                    verifiable.checkInvariant(),
                    "Invariant violated " + phase + " " + context.getDisplayName());
              }
            });
  }
}
