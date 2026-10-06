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
 * Which agent product and model drafted the report, as the agent itself reports them.
 *
 * <p>INTENT: triage needs to know whether a wording problem is one model's reading or everybody's.
 * Neither field is verified and neither is required — an agent that will not name itself still gets
 * to file — so "unknown" is {@code ""} on both, never null and never a guess.
 */
public record AgentIdentity(String product, String model) {

  public AgentIdentity {
    if (product == null || model == null) {
      throw new IllegalArgumentException(
          "an unknown agent product or model is \"\", never null — see AgentIdentity.unknown()");
    }
  }

  /** An agent that did not name itself. */
  public static AgentIdentity unknown() {
    return new AgentIdentity("", "");
  }

  /** The one line the report prints, or {@code ""} when nothing is known. */
  public String describe() {
    if (product.isEmpty() && model.isEmpty()) {
      return "";
    }
    return model.isEmpty() ? product : product + " / " + model;
  }
}
