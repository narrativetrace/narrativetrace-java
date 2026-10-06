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

/**
 * Implemented by test classes that verify a class invariant via Contract-Augmented TDD.
 *
 * <p>The test class exposes its subject; {@link InvariantCheckExtension} calls {@link
 * #checkInvariant()} before and after each test method.
 *
 * <p>The production class knows nothing about this interface — it declares a package-private {@code
 * boolean invariant()} and the test class bridges the two.
 *
 * @param <T> the type of the subject under test
 */
public interface ContractVerifiable<T> {

  /** The subject under test. May be {@code null} before setUp has run. */
  T subject();

  /** True when the subject is in a valid state, or when there is no subject yet. */
  boolean checkInvariant();
}
