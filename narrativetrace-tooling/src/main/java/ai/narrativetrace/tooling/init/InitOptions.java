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
package ai.narrativetrace.tooling.init;

/**
 * What a person asked the installer to do.
 *
 * <p>INTENT: every flag that changes a plan, in one value the planner takes as input — so a plan is
 * reproducible from a snapshot, a carrier and this, and from nothing else.
 *
 * <p><b>@llmNote</b> The defaults write nothing they were not asked to: an existing file is left
 * alone without {@link #writeExisting()}, and a skill directory somebody else owns is left alone
 * without {@link #force()}.
 *
 * @param dryRun compute and show the plan, write nothing, and exit 0 even on a refusal
 * @param writeExisting permission to touch a context file that exists and carries no markers
 * @param force permission to overwrite a skill directory that is not ours
 * @param scope which half of the install to plan
 * @param vendorClaude whether the vendor flavour is installed as well
 */
public record InitOptions(
    boolean dryRun, boolean writeExisting, boolean force, Scope scope, Vendor vendorClaude) {

  /** Which half of the install a run covers. */
  public enum Scope {
    /** The skill directories only. */
    SKILLS,
    /** The managed section and the import line only. */
    AGENTS_MD,
    /** Both — the default. */
    BOTH;

    /** Whether skill directories are part of this scope. */
    public boolean includesSkills() {
      return this != AGENTS_MD;
    }

    /** Whether the managed section is part of this scope. */
    public boolean includesAgentsMd() {
      return this != SKILLS;
    }
  }

  /** Whether the vendor flavour is installed beside the open-standard one. */
  public enum Vendor {
    /** Install it where the project looks like that vendor's. */
    AUTO,
    /** Install it, detected or not. */
    ON,
    /** Never install it. */
    OFF
  }

  public InitOptions {
    if (scope == null) {
      throw new IllegalArgumentException("an install needs a scope");
    }
    if (vendorClaude == null) {
      throw new IllegalArgumentException("an install needs a vendor rule");
    }
  }

  /** Write both halves, touch nothing that exists, detect the vendor. */
  public static InitOptions defaults() {
    return new InitOptions(false, false, false, Scope.BOTH, Vendor.AUTO);
  }

  public InitOptions withDryRun(boolean value) {
    return new InitOptions(value, writeExisting, force, scope, vendorClaude);
  }

  public InitOptions withWriteExisting(boolean value) {
    return new InitOptions(dryRun, value, force, scope, vendorClaude);
  }

  public InitOptions withForce(boolean value) {
    return new InitOptions(dryRun, writeExisting, value, scope, vendorClaude);
  }

  public InitOptions withScope(Scope value) {
    return new InitOptions(dryRun, writeExisting, force, value, vendorClaude);
  }

  public InitOptions withVendorClaude(Vendor value) {
    return new InitOptions(dryRun, writeExisting, force, scope, value);
  }
}
