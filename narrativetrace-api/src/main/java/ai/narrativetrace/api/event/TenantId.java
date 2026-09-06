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
package ai.narrativetrace.api.event;

import java.util.Objects;

/**
 * Tenant or account scope captured at span creation time.
 *
 * <p>Pro extensions may add tenant isolation enforcement or audit scoping. {@link #toString()}
 * returns the raw value for transparent use in JSON export, MDC, and OTel attributes.
 *
 * @param value the tenant identifier string, never null
 */
public record TenantId(String value) {

  public TenantId {
    Objects.requireNonNull(value, "tenantId value");
  }

  public static TenantId of(String value) {
    return new TenantId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
