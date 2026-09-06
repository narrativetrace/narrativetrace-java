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
 * Value type wrapping a W3C trace id: exactly 32 lowercase hexadecimal characters.
 *
 * <p>Validation happens at construction time — any {@code TraceId} instance is guaranteed valid.
 * {@link #toString()} returns the raw hex value, so the type is transparent in string contexts such
 * as MDC, JSON export, and OTel attributes.
 *
 * @param value 32-character lowercase hex string
 */
public record TraceId(String value) {

  private static final int LENGTH = 32;

  /** Validates the hex value on construction. */
  public TraceId {
    HexValidator.requireValid(value, LENGTH, "traceId");
  }

  /** Creates a {@code TraceId} from a 32-char lowercase hex string. */
  public static TraceId of(String hex) {
    return new TraceId(hex);
  }

  /** Generates a random {@code TraceId} using {@link SpanIdGenerator}. */
  public static TraceId generate() {
    return SpanIdGenerator.traceId();
  }

  /** Returns the raw hex value, making this type transparent in string contexts. */
  @Override
  public String toString() {
    return value;
  }
}
