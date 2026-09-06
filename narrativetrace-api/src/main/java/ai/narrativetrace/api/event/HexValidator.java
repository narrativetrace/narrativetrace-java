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

/**
 * Shared hex-string validation for W3C trace and span identifiers.
 *
 * <p>Package-private — used by {@link SpanId}, {@link TraceId}, and {@link SpanContext}.
 */
final class HexValidator {

  private HexValidator() {}

  /**
   * Validates that {@code value} is exactly {@code length} lowercase hex characters.
   *
   * @throws IllegalArgumentException if the value is null, wrong length, or contains non-hex chars
   */
  static void requireValid(String value, int length, String field) {
    if (value == null || value.length() != length || !isLowercaseHex(value)) {
      throw new IllegalArgumentException(
          field + " must be " + length + " lowercase hex characters");
    }
  }

  private static boolean isLowercaseHex(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        return false;
      }
    }
    return true;
  }
}
