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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates W3C-compliant trace and span identifiers from pseudorandom bytes.
 *
 * <p>Returns typed {@link TraceId} and {@link SpanId} values that are guaranteed valid at
 * construction time. IDs are identifiers, not secrets — they are meant to travel in logs, headers
 * and exported traces, and the W3C Trace Context spec only requires a low collision probability,
 * not unpredictability against an adversary — so a fast, non-cryptographic generator is the correct
 * choice on this hot path, not a gap to close with {@code SecureRandom}.
 */
public final class SpanIdGenerator {

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private SpanIdGenerator() {}

  /** Generates a 32-character lowercase hex trace ID (16 random bytes). */
  public static TraceId traceId() {
    return new TraceId(randomHex(16));
  }

  /** Generates a 16-character lowercase hex span ID (8 random bytes). */
  public static SpanId spanId() {
    return new SpanId(randomHex(8));
  }

  // FindSecBugs PREDICTABLE_RANDOM is suppressed for this method in
  // config/spotbugs/exclude.xml, not with @SuppressFBWarnings here: this module
  // publishes with zero dependencies (ArchitectureTest), and even a compileOnly
  // annotation dependency would be a class reference outside ai.narrativetrace.api
  // that the ArchUnit rule would reject. See the class JavaDoc for why
  // ThreadLocalRandom is the correct choice here, not a gap.
  private static String randomHex(int byteCount) {
    var random = ThreadLocalRandom.current();
    var buf = new char[byteCount * 2];
    for (int i = 0; i < byteCount; i++) {
      int b = random.nextInt(256);
      buf[i * 2] = HEX_DIGITS[b >>> 4];
      buf[i * 2 + 1] = HEX_DIGITS[b & 0x0f];
    }
    return new String(buf);
  }
}
