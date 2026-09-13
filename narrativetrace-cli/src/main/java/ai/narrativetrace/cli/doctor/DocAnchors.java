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

/**
 * The public doc anchor each check points at. Unlike the TypeScript reference, whose guides are not
 * yet published on narrativetrace.ai and so fall back to a GitHub blob URL, the Java runtime's
 * {@code documentation/} tree is already published there — every anchor below names a real page and
 * section under that site, never a private planning note, a repo path, or a SHA.
 */
public final class DocAnchors {

  private static final String BASE = "https://narrativetrace.ai/docs/";

  public static final String INSTALLATION_PREREQUISITES = BASE + "installation-guide#prerequisites";
  public static final String INSTALLATION_DEPENDENCIES =
      BASE + "installation-guide#2-add-dependencies";
  public static final String CONFIGURATION_OUTPUT = BASE + "configuration-guide#2-output";
  public static final String CONFIGURATION_EXTENSION =
      BASE + "configuration-guide#1-junit-5-extension";
  public static final String TROUBLESHOOTING_NO_OUTPUT =
      BASE + "troubleshooting#no-trace-files-are-written";
  public static final String TROUBLESHOOTING_ARG0 =
      BASE + "troubleshooting#parameters-show-as-arg0-arg1";
  public static final String PRIVACY_REDACTION =
      BASE + "privacy-and-redaction#redaction-surface-by-surface";
  public static final String STRUCTURAL_TRACE_APPROVAL =
      BASE + "structural-trace-format#approval-traces-end-to-end";
  public static final String SIXTY_SECONDS_BEFORE_YOU_START =
      BASE + "sixty-seconds#before-you-start";

  private DocAnchors() {}
}
