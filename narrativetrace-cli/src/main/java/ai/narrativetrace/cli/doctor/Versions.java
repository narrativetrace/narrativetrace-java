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
package ai.narrativetrace.cli.doctor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal dotted-numeric version parsing and comparison — no pre-release/build metadata rules,
 * because every version this doctor compares (JDK feature version, JUnit Jupiter release) is a
 * plain {@code major.minor.patch} or {@code major} release train.
 */
public final class Versions {

  private static final Pattern LEADING_NUMBER = Pattern.compile("\\d+");

  private Versions() {}

  /** The first run of digits in a version-like string, or 0 if none is found. */
  public static int leadingInt(String s) {
    if (s == null) {
      return 0;
    }
    Matcher m = LEADING_NUMBER.matcher(s);
    return m.find() ? Integer.parseInt(m.group()) : 0;
  }

  /** Compares two {@code x.y.z}-shaped versions component-wise, missing components as 0. */
  public static int compare(String a, String b) {
    int[] va = components(a);
    int[] vb = components(b);
    for (int i = 0; i < Math.max(va.length, vb.length); i++) {
      int ca = i < va.length ? va[i] : 0;
      int cb = i < vb.length ? vb[i] : 0;
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
    }
    return 0;
  }

  /** True when {@code min <= v < maxExclusive}. */
  public static boolean inRange(String v, String min, String maxExclusive) {
    return compare(v, min) >= 0 && compare(v, maxExclusive) < 0;
  }

  private static int[] components(String s) {
    String cleaned = s == null ? "" : s.split("[-+]", 2)[0];
    String[] parts = cleaned.split("\\.");
    int[] out = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = leadingInt(parts[i]);
    }
    return out;
  }
}
