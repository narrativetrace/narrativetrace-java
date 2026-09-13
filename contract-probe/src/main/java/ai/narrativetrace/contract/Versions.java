/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

/**
 * Version compare, deliberately duplicated (not shared) from buildSrc's {@code
 * ContractDecisionSupport.isApplicable}: this project consumes only registry artifacts and can
 * never depend on the root build's {@code buildSrc}, which is not published. Both sides are unit
 * tested against the same cases (docs-vs-published-gate §5.1 ruling 1).
 */
final class Versions {

  private Versions() {}

  /** True while {@code since} is NOT strictly later than {@code installedVersion}. */
  static boolean isApplicable(String since, String installedVersion) {
    int[] a = parts(since);
    int[] b = parts(installedVersion);
    int len = Math.max(a.length, b.length);
    for (int i = 0; i < len; i++) {
      int x = i < a.length ? a[i] : 0;
      int y = i < b.length ? b[i] : 0;
      if (x != y) {
        return x < y;
      }
    }
    return true;
  }

  private static int[] parts(String version) {
    String[] split = version.split("\\.");
    int[] out = new int[split.length];
    for (int i = 0; i < split.length; i++) {
      out[i] = Integer.parseInt(split[i]);
    }
    return out;
  }
}
