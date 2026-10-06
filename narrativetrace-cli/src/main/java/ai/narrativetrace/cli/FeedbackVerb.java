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
package ai.narrativetrace.cli;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.feedback.FeedbackDraft;
import ai.narrativetrace.tooling.feedback.FeedbackDrafter;
import ai.narrativetrace.tooling.feedback.FeedbackGatherer;
import ai.narrativetrace.tooling.feedback.FeedbackJson;
import ai.narrativetrace.tooling.feedback.FeedbackRender;
import ai.narrativetrace.tooling.feedback.FeedbackReport;
import ai.narrativetrace.tooling.feedback.GhCommandLine;
import ai.narrativetrace.tooling.feedback.IssueFormUrl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code narrativetrace feedback} — drafts a problem report about NarrativeTrace itself, checks it
 * carries no values, and shows the person how to file it.
 *
 * <p>INTENT: the whole decision is the user's, and this verb is built so that it cannot be anybody
 * else's. It writes two files and prints text. It opens no browser, runs no {@code gh}, and makes
 * no request of its own — the only outward-facing thing it does is ask an already-installed {@code
 * gh} whether it is signed in, and only on the channel that would need it.
 *
 * <p><b>@llmNote</b> Every channel re-drafts from the flags it was given rather than reading back a
 * draft from disk. That is deliberate: there is no hidden state between invocations, so a report
 * can never be filed under a draft that was edited after it was shown. "Never edit the draft after
 * showing it — draft it again and show it again" is enforced by there being nothing else to do.
 *
 * <p><b>@sideEffects</b> Writes {@code build/narrativetrace/feedback/feedback-draft.md} and {@code
 * feedback-body.md} under the project, and only when the gate cleared the report.
 */
final class FeedbackVerb {

  /** Where the two files land, relative to the project. */
  static final String OUTPUT_DIRECTORY = "build/narrativetrace/feedback";

  static final String DRAFT_FILE = OUTPUT_DIRECTORY + "/feedback-draft.md";
  static final String BODY_FILE = OUTPUT_DIRECTORY + "/feedback-body.md";

  /** What to do instead when {@code gh} cannot be used. */
  private static final String NO_GH =
      "gh is not installed or not signed in. Use `narrativetrace feedback url` instead: it needs"
          + " no tool and no credential beyond the browser you are already signed in to.";

  private FeedbackVerb() {}

  static int run(List<String> arguments, Cli.Deps deps) {
    FeedbackArguments parsed = FeedbackArguments.parse(arguments);
    if (parsed.help()) {
      deps.out().println(Cli.FEEDBACK_USAGE);
      return 0;
    }
    if (parsed.error() != null) {
      deps.err().println(parsed.error() + "\n\n" + Cli.FEEDBACK_USAGE);
      return 2;
    }
    try {
      return draftThen(parsed, deps);
    } catch (IllegalArgumentException e) {
      deps.err().println(e.getMessage());
      return 2;
    } catch (UncheckedIOException e) {
      deps.err().println("could not write the draft: " + e.getMessage());
      return 1;
    }
  }

  private static int draftThen(FeedbackArguments parsed, Cli.Deps deps) {
    DoctorSnapshot snapshot = deps.buildSnapshot().apply(deps.cwd());
    FeedbackReport report = parsed.report(snapshot, readDoctorReport(deps.cwd()));
    FeedbackDraft draft = FeedbackDrafter.draft(report);
    if (draft instanceof FeedbackDraft.Refused refused) {
      deps.err()
          .print(
              parsed.json() ? FeedbackJson.refused(parsed.channel(), refused) : refused.describe());
      return 2;
    }
    FeedbackDraft.Drafted drafted = (FeedbackDraft.Drafted) draft;
    write(deps.cwd(), drafted);
    return print(parsed, drafted, deps, traceNote(snapshot, parsed.trace()));
  }

  private static int print(
      FeedbackArguments parsed, FeedbackDraft.Drafted drafted, Cli.Deps deps, String traceNote) {
    return switch (parsed.channel()) {
      case "url" -> printUrl(parsed, drafted, deps);
      case "gh" -> printGh(parsed, drafted, deps);
      default -> printDraft(parsed, drafted, deps, traceNote);
    };
  }

  private static int printDraft(
      FeedbackArguments parsed, FeedbackDraft.Drafted drafted, Cli.Deps deps, String traceNote) {
    if (parsed.json()) {
      deps.out().print(FeedbackJson.drafted(drafted.report(), DRAFT_FILE, BODY_FILE, traceNote));
      return 0;
    }
    deps.out().println(drafted.draft());
    deps.out().println("Written to " + DRAFT_FILE + " and " + BODY_FILE + ".");
    if (!traceNote.isEmpty()) {
      deps.out().println("No structural trace attached: " + traceNote);
    }
    return 0;
  }

  private static int printUrl(
      FeedbackArguments parsed, FeedbackDraft.Drafted drafted, Cli.Deps deps) {
    String url = IssueFormUrl.of(drafted.report());
    if (parsed.json()) {
      deps.out().print(FeedbackJson.url(url, BODY_FILE));
      return 0;
    }
    deps.out().println(url);
    deps.out()
        .println(
            "\nOpen that in your own browser, where you are already signed in, then paste the"
                + " contents of "
                + BODY_FILE
                + " into the report's last box and submit it.\n\n"
                + FeedbackRender.PRIVACY_NOTE);
    return 0;
  }

  private static int printGh(
      FeedbackArguments parsed, FeedbackDraft.Drafted drafted, Cli.Deps deps) {
    if (!deps.ghAuthenticated().getAsBoolean()) {
      deps.err().print(parsed.json() ? FeedbackJson.ghUnavailable(NO_GH) : NO_GH + "\n");
      return 1;
    }
    String command = GhCommandLine.of(drafted.report(), BODY_FILE);
    if (parsed.json()) {
      deps.out().print(FeedbackJson.gh(command));
      return 0;
    }
    deps.out().println(command);
    deps.out()
        .println(
            "\nThat line is printed, not run. Running it files the report under your own GitHub"
                + " account.\n\n"
                + FeedbackRender.PRIVACY_NOTE);
    return 0;
  }

  /** Why no structural trace was attached, or {@code ""} when one was. */
  private static String traceNote(DoctorSnapshot snapshot, String preferredPath) {
    return FeedbackGatherer.chooseTrace(snapshot, preferredPath).reason();
  }

  /** The doctor's JSON report, or {@code ""} when this project has none. */
  private static String readDoctorReport(Path project) {
    Path report = project.resolve(FeedbackGatherer.DOCTOR_REPORT_PATH);
    if (!Files.isRegularFile(report)) {
      return "";
    }
    try {
      return Files.readString(report);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + report, e);
    }
  }

  private static void write(Path project, FeedbackDraft.Drafted drafted) {
    try {
      Files.createDirectories(project.resolve(OUTPUT_DIRECTORY));
      Files.writeString(project.resolve(DRAFT_FILE), drafted.draft());
      Files.writeString(project.resolve(BODY_FILE), drafted.body());
    } catch (IOException e) {
      throw new UncheckedIOException("could not write under " + OUTPUT_DIRECTORY, e);
    }
  }
}
