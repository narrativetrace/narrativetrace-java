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
