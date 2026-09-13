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
 * One doctor check's outcome. Every finding carries a stable, dotted {@code id} — appended to,
 * never renamed, across releases, since the id is what a self-advertising library error or a
 * skill's {@code verify} step names — plus a human message, a fix (mandatory on a failure, empty on
 * a pass), and an absolute doc URL a person or an agent can follow.
 *
 * <p>Mirrors the TypeScript reference's {@code Finding} type ({@code
 * packages/cli/src/doctor/types.ts} in the sibling TypeScript runtime) in shape and intent, adapted
 * to Java's two-state (no network, no "unknown") model.
 */
public record Finding(String id, Status status, String message, String fix, String docUrl) {

  /** Whether a check passed or failed. There is no third state: the doctor never guesses. */
  public enum Status {
    PASS,
    FAIL
  }

  public Finding {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("a finding's id must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("a finding's status must not be null");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("a finding's message must not be blank");
    }
    if (docUrl == null || !docUrl.startsWith("https://")) {
      throw new IllegalArgumentException("a finding's docUrl must be an absolute https:// URL");
    }
    if (status == Status.FAIL && (fix == null || fix.isBlank())) {
      throw new IllegalArgumentException("a failing finding must carry a non-blank fix");
    }
    if (status == Status.PASS && fix == null) {
      fix = "";
    }
  }

  /** True when {@link #status()} is {@link Status#FAIL}. */
  public boolean isFailing() {
    return status == Status.FAIL;
  }

  public static Finding pass(String id, String message, String docUrl) {
    return new Finding(id, Status.PASS, message, "", docUrl);
  }

  public static Finding fail(String id, String message, String fix, String docUrl) {
    return new Finding(id, Status.FAIL, message, fix, docUrl);
  }
}
