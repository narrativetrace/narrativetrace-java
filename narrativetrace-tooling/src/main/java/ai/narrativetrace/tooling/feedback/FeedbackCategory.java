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
package ai.narrativetrace.tooling.feedback;

/**
 * Which part of NarrativeTrace a problem report is about — the one field that decides where the
 * report is triaged and what it must carry.
 *
 * <p>INTENT: a closed set, not free text. The issue form renders it as a dropdown, the label {@code
 * category:<id>} comes from it, and {@link #requiresDoctorReport()} is the one rule that depends on
 * it: a complaint about the doctor or about a skill is unarguable only with the doctor's own JSON
 * beside it, while a complaint about the published prompt or about the library may be filed from a
 * project whose build cannot run the doctor at all.
 */
public enum FeedbackCategory {

  /** The published install prompt, or any text an adopter was told to follow. */
  PROMPT("prompt", false),

  /** A catalogue skill: a step that does not work, a verify that cannot be met, wrong wording. */
  SKILL("skill", true),

  /** A doctor check: wrong finding, wrong fix, a fix that does not work. */
  DOCTOR("doctor", true),

  /** The library itself: capture, rendering, redaction, an integration. */
  LIBRARY("library", false);

  private final String id;
  private final boolean requiresDoctorReport;

  FeedbackCategory(String id, boolean requiresDoctorReport) {
    this.id = id;
    this.requiresDoctorReport = requiresDoctorReport;
  }

  /** The lower-case id the issue form's dropdown and the {@code category:} label carry. */
  public String id() {
    return id;
  }

  /** Whether a report in this category is only meaningful with the doctor's JSON attached. */
  public boolean requiresDoctorReport() {
    return requiresDoctorReport;
  }

  /**
   * The category with this id.
   *
   * @throws IllegalArgumentException on an unknown id — a category is a closed set, and a silent
   *     fallback would file a report into the wrong triage queue
   */
  public static FeedbackCategory ofId(String id) {
    for (FeedbackCategory category : values()) {
      if (category.id.equals(id)) {
        return category;
      }
    }
    throw new IllegalArgumentException(
        "unknown category \"" + id + "\" — one of prompt, skill, doctor, library");
  }
}
