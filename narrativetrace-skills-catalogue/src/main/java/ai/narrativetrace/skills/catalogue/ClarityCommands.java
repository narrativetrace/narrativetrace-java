/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/** Adopter-facing commands and report paths used by {@code add-narrativetrace-clarity}. */
public final class ClarityCommands {

  public static final String CLEAN_STATIC_SCAN = "./gradlew clean clarityScan";
  public static final String CLEAN_CLARITY_CHECK = "./gradlew clean clarityCheck";
  public static final String FIND_JSON_REPORT =
      "find build/narrativetrace -name \"clarity-scan-results.json\"";
  public static final String FIND_MARKDOWN_REPORT =
      "find build/narrativetrace -name \"clarity-scan-report.md\"";
  public static final String JSON_REPORT_PATH = "build/narrativetrace/clarity-scan-results.json";
  public static final String MARKDOWN_REPORT_PATH = "build/narrativetrace/clarity-scan-report.md";

  private ClarityCommands() {}
}
