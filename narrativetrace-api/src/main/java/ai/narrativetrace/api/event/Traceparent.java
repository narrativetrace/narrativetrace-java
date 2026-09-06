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
 * W3C Trace Context {@code traceparent} header value — the cross-process half of ADR-014's adopt
 * rung.
 *
 * <p>INTENT: This is the wire format a trace crosses a process boundary in. Inbound, a filter
 * parses the header and the trace continues instead of starting over; outbound, a client formats
 * one so the next service can do the same.
 *
 * <p><b>@llmNote</b> {@link #parse(String)} returns {@code null} for anything malformed rather than
 * throwing — the caller is on a request path, and a bad header from a stranger must degrade to
 * "start a fresh trace", never to a failed request. The record constructor is the opposite regime
 * (fail fast and loud), so construct one directly only from ids you already trust.
 *
 * <p><b>@edgeCase</b> Parsing is strict, matching the W3C ABNF: lowercase hex only, no surrounding
 * whitespace, no all-zero trace id or parent id, and the forbidden {@code ff} version is rejected.
 * A version above {@code 00} may carry trailing fields, which are ignored; version {@code 00} may
 * not.
 *
 * @param traceId Trace the caller is part of, adopted verbatim by the receiver.
 * @param parentSpanId The caller's own span id — the receiver's parent span.
 * @param traceFlags Raw W3C trace-flags byte; bit 0 is the sampled flag.
 * @see <a href="https://www.w3.org/TR/trace-context/#traceparent-header">W3C traceparent</a>
 */
public record Traceparent(TraceId traceId, SpanId parentSpanId, int traceFlags) {

  /** Canonical lowercase header name, as sent and as matched case-insensitively on receipt. */
  public static final String HEADER_NAME = "traceparent";

  /** The only version this implementation formats; higher versions are parsed, not written. */
  private static final String VERSION = "00";

  private static final String FORBIDDEN_VERSION = "ff";
  private static final String DELIMITER = "-";
  private static final int VERSION_LENGTH = 2;
  private static final int FLAGS_LENGTH = 2;
  private static final int FIELD_COUNT = 4;
  private static final int TRACE_ID_LENGTH = 32;
  private static final int SPAN_ID_LENGTH = 16;
  private static final int HEX_RADIX = 16;
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  /** Rejects null ids and flags outside one unsigned byte. */
  public Traceparent {
    if (traceId == null) {
      throw new IllegalArgumentException("traceId must not be null");
    }
    if (parentSpanId == null) {
      throw new IllegalArgumentException("parentSpanId must not be null");
    }
    if (traceFlags < 0 || traceFlags > 0xff) {
      throw new IllegalArgumentException("traceFlags must be a single unsigned byte");
    }
  }

  /**
   * Parses a {@code traceparent} header value.
   *
   * @param headerValue Raw header value, possibly {@code null} or malformed.
   * @return The parsed value, or {@code null} when the header is absent or does not conform.
   */
  public static Traceparent parse(String headerValue) {
    if (headerValue == null) {
      return null;
    }
    var fields = headerValue.split(DELIMITER, -1);
    if (fields.length < FIELD_COUNT || !isValidVersion(fields[0], fields.length)) {
      return null;
    }
    if (!isId(fields[1], TRACE_ID_LENGTH) || !isId(fields[2], SPAN_ID_LENGTH)) {
      return null;
    }
    if (!isHex(fields[3], FLAGS_LENGTH) || !hasNoEmptyTrailingField(fields)) {
      return null;
    }
    return new Traceparent(
        TraceId.of(fields[1]), SpanId.of(fields[2]), Integer.parseInt(fields[3], HEX_RADIX));
  }

  /**
   * Renders this value as a version-{@code 00} header.
   *
   * @return The header value, always 55 characters.
   */
  public String format() {
    return VERSION
        + DELIMITER
        + traceId.value()
        + DELIMITER
        + parentSpanId.value()
        + DELIMITER
        + hexByte(traceFlags);
  }

  /** Returns whether the W3C sampled flag (bit 0 of {@link #traceFlags()}) is set. */
  public boolean sampled() {
    return (traceFlags & 1) != 0;
  }

  /** Returns the header value, making the type transparent in string contexts. */
  @Override
  public String toString() {
    return format();
  }

  /** Version {@code 00} is exactly four fields; a higher one may append more, {@code ff} is not. */
  private static boolean isValidVersion(String version, int fieldCount) {
    if (!isHex(version, VERSION_LENGTH) || FORBIDDEN_VERSION.equals(version)) {
      return false;
    }
    return !VERSION.equals(version) || fieldCount == FIELD_COUNT;
  }

  /** A trailing empty field means a stray delimiter, not an extension the spec allows. */
  private static boolean hasNoEmptyTrailingField(String[] fields) {
    return !fields[fields.length - 1].isEmpty();
  }

  private static boolean isId(String value, int length) {
    return isHex(value, length) && !isAllZero(value);
  }

  private static boolean isAllZero(String value) {
    return value.chars().allMatch(c -> c == '0');
  }

  private static boolean isHex(String value, int length) {
    if (value.length() != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        return false;
      }
    }
    return true;
  }

  private static String hexByte(int value) {
    return new String(new char[] {HEX_DIGITS[value >>> 4], HEX_DIGITS[value & 0x0f]});
  }
}
