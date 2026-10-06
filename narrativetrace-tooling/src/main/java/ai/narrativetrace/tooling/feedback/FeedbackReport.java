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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One problem report, gathered: which runtime and install, which part of the product, which step,
 * the three sentences, the language it is written in, who drafted it, and at most two attachments.
 *
 * <p>INTENT: the single object the gate, the draft, the issue-form URL and the {@code gh} line all
 * read, so none of them can disagree about what is being filed. Every runtime in the family gathers
 * the same fields under the same names — the issue form is shared, so the field set is a
 * cross-runtime contract, not a Java shape.
 *
 * <p><b>@llmNote</b> Built through {@link Builder} rather than positionally: eight components of
 * which six are strings is a call nobody can read at the point of use, and two of them swapped is a
 * report filed with the wrong step.
 *
 * <p><b>@llmNote</b> {@link #fields()} is what the gate reads, and it is deliberately EVERY field
 * that reaches a URL or a body file — including the install coordinate and the agent line, which
 * look harmless and are the two fields a wrapper script is most likely to interpolate something
 * into.
 */
public record FeedbackReport(
    String runtime,
    FeedbackCategory category,
    String install,
    String step,
    ProblemNarrative narrative,
    String language,
    AgentIdentity agent,
    Attachments attachments) {

  public FeedbackReport {
    requireText(runtime, "runtime");
    requireText(install, "install");
    requireText(step, "step");
    requireText(language, "language");
    requireParts(category, narrative, agent, attachments);
    if (category.requiresDoctorReport() && !attachments.hasDoctorReport()) {
      throw new IllegalArgumentException(
          "a \""
              + category.id()
              + "\" report needs the doctor's JSON report — run the doctor, or file this under"
              + " prompt or library instead");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a report's \"" + field + "\" must not be blank");
    }
  }

  private static void requireParts(
      FeedbackCategory category,
      ProblemNarrative narrative,
      AgentIdentity agent,
      Attachments attachments) {
    if (category == null || narrative == null || agent == null || attachments == null) {
      throw new IllegalArgumentException(
          "a report needs a category, a narrative, an agent identity and an attachment set");
    }
  }

  /**
   * Every field the gate inspects, in the order a refusal lists them.
   *
   * <p>A {@link LinkedHashMap}, so the same report always refuses in the same words — which is what
   * lets a test and a corpus row assert on them.
   */
  public Map<String, String> fields() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("install", install);
    fields.put("step", step);
    fields.put("did", narrative.did());
    fields.put("happened", narrative.happened());
    fields.put("expected", narrative.expected());
    fields.put("agent", agent.describe());
    fields.put("doctor report", attachments.doctorReport());
    fields.put("trace", attachments.structuralTrace());
    return fields;
  }

  /**
   * True when this report is in a state the rest of the verb may rely on.
   *
   * <p>Nine categories, the ones that apply here: no blank mandatory text; the category is one of
   * the four; the attachment set's own exclusivity holds; the category's doctor-report rule holds;
   * the runtime is canonical (lower case, no spaces) because it becomes a {@code runtime:} label;
   * and no field is null.
   */
  boolean invariant() {
    return !runtime.isBlank()
        && runtime.equals(runtime.toLowerCase(java.util.Locale.ROOT))
        && !runtime.contains(" ")
        && category != null
        && !install.isBlank()
        && !step.isBlank()
        && !language.isBlank()
        && narrative != null
        && agent != null
        && attachments != null
        && attachments.hasDoctorReport() == attachments.doctorUnavailable().isBlank()
        && (!category.requiresDoctorReport() || attachments.hasDoctorReport());
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Starts a copy of this report, for a single-field override. */
  public Builder toBuilder() {
    return new Builder()
        .runtime(runtime)
        .category(category)
        .install(install)
        .step(step)
        .narrative(narrative)
        .language(language)
        .agent(agent)
        .attachments(attachments);
  }

  /** Builds a {@link FeedbackReport} one named field at a time. */
  public static final class Builder {
    private String runtime = "java";
    private FeedbackCategory category;
    private String install;
    private String step;
    private ProblemNarrative narrative;
    private String language = "en";
    private AgentIdentity agent = AgentIdentity.unknown();
    private Attachments attachments;

    private Builder() {}

    public Builder runtime(String value) {
      this.runtime = value;
      return this;
    }

    public Builder category(FeedbackCategory value) {
      this.category = value;
      return this;
    }

    public Builder install(String value) {
      this.install = value;
      return this;
    }

    public Builder step(String value) {
      this.step = value;
      return this;
    }

    public Builder narrative(ProblemNarrative value) {
      this.narrative = value;
      return this;
    }

    public Builder language(String value) {
      this.language = value;
      return this;
    }

    public Builder agent(AgentIdentity value) {
      this.agent = value;
      return this;
    }

    public Builder attachments(Attachments value) {
      this.attachments = value;
      return this;
    }

    public FeedbackReport build() {
      return new FeedbackReport(
          runtime, category, install, step, narrative, language, agent, attachments);
    }
  }
}
