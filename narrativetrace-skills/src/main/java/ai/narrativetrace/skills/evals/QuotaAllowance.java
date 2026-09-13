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
package ai.narrativetrace.skills.evals;

/** One row of {@code ledger/quota.md}'s Allowance table: a platform's plan tier and weekly cap. */
public record QuotaAllowance(Platform platform, String planTier, int weeklyAllowance) {

  public QuotaAllowance {
    if (platform == null) {
      throw new IllegalArgumentException("a QuotaAllowance's platform must not be null");
    }
    if (planTier == null || planTier.isBlank()) {
      throw new IllegalArgumentException("a QuotaAllowance's planTier must not be blank");
    }
  }
}
