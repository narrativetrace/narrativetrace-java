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
 * Request route or path template associated with a span.
 *
 * <p>Pro extensions may add route pattern masking or normalization. {@link #toString()} returns the
 * raw value for transparent use in JSON export, MDC, and OTel attributes.
 *
 * @param value the route string, never null
 */
public record HttpRoute(String value) {

  public HttpRoute {
    Objects.requireNonNull(value, "httpRoute value");
  }

  public static HttpRoute of(String value) {
    return new HttpRoute(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
