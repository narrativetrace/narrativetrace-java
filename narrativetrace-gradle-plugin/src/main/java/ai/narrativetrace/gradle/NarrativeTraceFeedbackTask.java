/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import ai.narrativetrace.tooling.feedback.AgentIdentity;
import ai.narrativetrace.tooling.feedback.Attachments;
import ai.narrativetrace.tooling.feedback.FeedbackCategory;
import ai.narrativetrace.tooling.feedback.FeedbackDraft;
import ai.narrativetrace.tooling.feedback.FeedbackDrafter;
import ai.narrativetrace.tooling.feedback.FeedbackGatherer;
import ai.narrativetrace.tooling.feedback.FeedbackRender;
import ai.narrativetrace.tooling.feedback.FeedbackReport;
import ai.narrativetrace.tooling.feedback.IssueFormUrl;
import ai.narrativetrace.tooling.feedback.ProblemNarrative;
import ai.narrativetrace.tooling.feedback.PublicRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * {@code narrativetraceFeedback}: drafts a problem report about NarrativeTrace from this project,
 * refuses to write one that carries a value from the project's own traces, and prints either the
 * whole draft or the pre-filled issue-form URL.
 *
 * <p>INTENT: the agent-facing spelling of the same verb the {@code narrativetrace} CLI carries, for
 * the same reason {@code narrativetraceDoctor} exists beside {@code narrativetrace doctor} — an
 * agent skill's closed command vocabulary is the repo's own Gradle wrapper, and a skill may never
 * instruct installing a global tool. Both entry points call the same {@code narrativetrace-tooling}
 * classes in-process; neither depends on the other.
 *
 * <p><b>@llmNote</b> Two channels here, not three. The {@code gh} channel is the CLI's, and
 * deliberately not duplicated: deciding whether to offer it means asking an installed {@code gh}
 * whether it is signed in, and a second copy of that probe in this plugin would be a second thing
 * to keep true. {@code gh} is a convenience for people who already live in that tool — and those
 * people have a terminal.
 *
 * <p><b>@llmNote</b> This task sends NOTHING and opens nothing. It writes two files under the
 * extension's own output directory and prints text. Filing is the person's act, in their own
 * browser, under their own account.
 *
 * <p><b>@sideEffects</b> Writes {@code <outputDir>/feedback/feedback-draft.md} and {@code
 * feedback-body.md}, and only when the value-free gate cleared the report.
 */
public abstract class NarrativeTraceFeedbackTask extends DefaultTask {

  /** The project the report is about — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  /** Where the two files land: the extension's output directory, {@code feedback/} inside it. */
  @Internal
  public abstract DirectoryProperty getOutputDir();

  /** The resolved agent-skills carrier, read exactly as {@code narrativetraceDoctor} reads it. */
  @Internal
  public abstract ConfigurableFileCollection getCarrier();

  /**
   * The coordinate the carrier was asked for — what "could not resolve it" has to be able to say.
   */
  @Input
  public abstract Property<String> getCarrierCoordinate();

  @Input
  @Option(option = "channel", description = "draft (write and print the whole draft) or url")
  public abstract Property<String> getChannel();

  @Input
  @Option(option = "category", description = "prompt, skill, doctor or library")
  public abstract Property<String> getCategory();

  @Input
  @Option(option = "step", description = "The doctor check id, skill step, or prompt step number")
  public abstract Property<String> getStep();

  @Input
  @Option(option = "did", description = "What you did")
  public abstract Property<String> getDid();

  @Input
  @Option(option = "happened", description = "What happened instead")
  public abstract Property<String> getHappened();

  @Input
  @Option(option = "expected", description = "What you expected")
  public abstract Property<String> getExpected();

  @Input
  @Option(option = "language", description = "The language the report is written in")
  public abstract Property<String> getLanguage();

  @Input
  @Option(option = "agent-product", description = "The agent product, as it reports itself")
  public abstract Property<String> getAgentProduct();

  @Input
  @Option(option = "agent-model", description = "The agent model, as it reports itself")
  public abstract Property<String> getAgentModel();

  @Input
  @Option(option = "trace", description = "A path suffix naming the structural trace to attach")
  public abstract Property<String> getTrace();

  @TaskAction
  public void draftReport() {
    FeedbackDraft draft = FeedbackDrafter.draft(gather());
    if (draft instanceof FeedbackDraft.Refused refused) {
      throw new GradleException(
          refused.describe()
              + "Nothing was written. Fix the field the rule names and run this again.");
    }
    FeedbackDraft.Drafted drafted = (FeedbackDraft.Drafted) draft;
    write(drafted);
    announce(drafted);
  }

  /** The report this project and these options describe. */
  private FeedbackReport gather() {
    DoctorSnapshot snapshot = snapshot();
    return FeedbackReport.builder()
        .runtime(PublicRepository.RUNTIME)
        .category(FeedbackCategory.ofId(getCategory().get()))
        .install(FeedbackGatherer.installCoordinate(snapshot))
        .step(getStep().get())
        .narrative(new ProblemNarrative(getDid().get(), getHappened().get(), getExpected().get()))
        .language(getLanguage().get())
        .agent(new AgentIdentity(getAgentProduct().get(), getAgentModel().get()))
        .attachments(attachments(snapshot))
        .build();
  }

  private DoctorSnapshot snapshot() {
    SkillsCarrier.Resolution resolution =
        SkillsCarrier.resolve(getCarrier()::getFiles, getCarrierCoordinate().get());
    return SnapshotBuilder.build(targetPath(), resolution.carrier());
  }

  private Attachments attachments(DoctorSnapshot snapshot) {
    return FeedbackGatherer.attachments(snapshot, doctorReport(), getTrace().get());
  }

  /** The doctor's JSON report, or {@code ""} when this project has none to attach. */
  private String doctorReport() {
    Path report = targetPath().resolve(FeedbackGatherer.DOCTOR_REPORT_PATH);
    if (!Files.isRegularFile(report)) {
      return "";
    }
    try {
      return Files.readString(report);
    } catch (IOException e) {
      throw new GradleException("narrativetraceFeedback could not read " + report, e);
    }
  }

  private void announce(FeedbackDraft.Drafted drafted) {
    if ("url".equals(getChannel().get())) {
      getLogger().lifecycle(IssueFormUrl.of(drafted.report()));
      getLogger()
          .lifecycle(
              "\nOpen that in your own browser, then paste the contents of {} into the report's"
                  + " last box and submit it.\n\n{}",
              bodyFile(),
              FeedbackRender.PRIVACY_NOTE);
      return;
    }
    getLogger().lifecycle(drafted.draft());
    getLogger().lifecycle("Written to {} and {}.", draftFile(), bodyFile());
  }

  private void write(FeedbackDraft.Drafted drafted) {
    try {
      Files.createDirectories(feedbackDirectory());
      Files.writeString(draftFile(), drafted.draft());
      Files.writeString(bodyFile(), drafted.body());
    } catch (IOException e) {
      throw new GradleException(
          "narrativetraceFeedback could not write under " + feedbackDirectory(), e);
    }
  }

  private Path targetPath() {
    return getTargetDir().getAsFile().get().toPath();
  }

  private Path feedbackDirectory() {
    return getOutputDir().getAsFile().get().toPath().resolve("feedback");
  }

  private Path draftFile() {
    return feedbackDirectory().resolve("feedback-draft.md");
  }

  private Path bodyFile() {
    return feedbackDirectory().resolve("feedback-body.md");
  }
}
