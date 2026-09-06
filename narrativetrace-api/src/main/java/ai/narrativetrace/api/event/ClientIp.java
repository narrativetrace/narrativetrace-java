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
 * Client network address captured at span creation time.
 *
 * <p>Pro extensions may add IP anonymization or erasure for GDPR compliance. {@link #toString()}
 * returns the raw value for transparent use in JSON export, MDC, and OTel attributes.
 *
 * @param value the IP address string, never null
 */
public record ClientIp(String value) {

  public ClientIp {
    Objects.requireNonNull(value, "clientIp value");
  }

  public static ClientIp of(String value) {
    return new ClientIp(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
