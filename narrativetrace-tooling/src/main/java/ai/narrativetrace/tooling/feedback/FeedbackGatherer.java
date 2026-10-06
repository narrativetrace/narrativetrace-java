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

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the verb can learn about a project without being told: which NarrativeTrace it has
 * installed, and which structural trace is safe to attach.
 *
 * <p>INTENT: the two facts triage needs most are the two a reporter is least likely to get right by
 * hand. The install coordinate decides whether a defect is already fixed; the structural trace
 * shows the shape of the call that misbehaved. Both are read from the doctor's own snapshot, so the
 * report and the doctor cannot disagree about the project they are describing.
 *
 * <p><b>@llmNote</b> A trace is chosen only if it is BOTH a structural trace by grammar and clean
 * by every value-free rule, and when none is, the choice says why rather than attaching nothing
 * silently. A report that quietly lost its attachment is a report whose author thinks they filed
 * more than they did.
 *
 * <p><b>@sideEffects</b> None: a pure function over a snapshot. Reading the doctor's JSON from disk
 * is the entry point's job, and it is passed in here.
 */
public final class FeedbackGatherer {

  /** What the install field says when the build declares none of our coordinates. */
  public static final String INSTALL_UNKNOWN = "not declared in this project's build file";

  /** Where the doctor leaves the report this verb attaches. */
  public static final String DOCTOR_REPORT_PATH = "build/narrativetrace/doctor-report.json";

  private static final String COORDINATE_PREFIX = "ai.narrativetrace:";

  private FeedbackGatherer() {}

  /**
   * Which structural trace was chosen, or why none was.
   *
   * @param content the trace's content, or {@code ""} when none was chosen
   * @param reason why nothing was chosen, or {@code ""} when something was
   */
  public record TraceChoice(String content, String reason) {

    public TraceChoice {
      if (content == null || reason == null) {
        throw new IllegalArgumentException("a trace choice is content or a reason, never null");
      }
      if (content.isBlank() == reason.isBlank()) {
        throw new IllegalArgumentException(
            "a choice attached a trace or says why it did not, never both and never neither");
      }
    }
  }

  /** Every NarrativeTrace coordinate the build declares, or a stated unknown. */
  public static String installCoordinate(DoctorSnapshot snapshot) {
    List<String> ours =
        snapshot.declaredDependencyCoordinates().stream()
            .filter(coordinate -> coordinate.startsWith(COORDINATE_PREFIX))
            .sorted()
            .toList();
    return ours.isEmpty() ? INSTALL_UNKNOWN : String.join(", ", ours);
  }

  /**
   * The structural trace to attach.
   *
   * @param preferredPath a path suffix the user named, or {@code ""} to let the verb choose
   */
  public static TraceChoice chooseTrace(DoctorSnapshot snapshot, String preferredPath) {
    Map<String, String> candidates = structuralCandidates(snapshot);
    if (!preferredPath.isBlank()) {
      return named(candidates, preferredPath);
    }
    for (Map.Entry<String, String> candidate : candidates.entrySet()) {
      if (isAttachable(candidate.getValue())) {
        return new TraceChoice(candidate.getValue(), "");
      }
    }
    return new TraceChoice("", refusalFor(candidates));
  }

  /** The attachment set: the doctor's JSON when there is one, and at most one structural trace. */
  public static Attachments attachments(
      DoctorSnapshot snapshot, String doctorReportJson, String preferredTracePath) {
    String trace = chooseTrace(snapshot, preferredTracePath).content();
    if (doctorReportJson == null || doctorReportJson.isBlank()) {
      return Attachments.withoutDoctorReport(
          "no report at " + DOCTOR_REPORT_PATH + " — run the doctor first", trace);
    }
    return Attachments.of(doctorReportJson, trace);
  }

  /** Every {@code .nt} the snapshot saw, in path order so two runs choose the same one. */
  private static Map<String, String> structuralCandidates(DoctorSnapshot snapshot) {
    Map<String, String> candidates = new TreeMap<>();
    snapshot
        .outputFiles()
        .forEach((path, content) -> putIfStructuralName(candidates, path, content));
    snapshot
        .approvalDirFiles()
        .forEach((path, content) -> putIfStructuralName(candidates, path, content));
    return candidates;
  }

  private static void putIfStructuralName(
      Map<String, String> candidates, String path, String content) {
    if (path.endsWith(".nt")) {
      candidates.put(path, content);
    }
  }

  private static TraceChoice named(Map<String, String> candidates, String preferredPath) {
    for (Map.Entry<String, String> candidate : candidates.entrySet()) {
      if (candidate.getKey().endsWith(preferredPath)) {
        return isAttachable(candidate.getValue())
            ? new TraceChoice(candidate.getValue(), "")
            : new TraceChoice("", preferredPath + " " + whyNot(candidate.getValue()));
      }
    }
    return new TraceChoice("", preferredPath + " was not found under this project's output");
  }

  private static boolean isAttachable(String content) {
    return StructuralTrace.looksStructural(content)
        && ValueFreeCheck.rulesRefusing(content).isEmpty();
  }

  private static String refusalFor(Map<String, String> candidates) {
    if (candidates.isEmpty()) {
      return "no structural trace was found under this project's output";
    }
    Map.Entry<String, String> first = candidates.entrySet().iterator().next();
    return "no attachable structural trace: " + first.getKey() + " " + whyNot(first.getValue());
  }

  /** Why one candidate was not attachable, in the words the verb prints. */
  private static String whyNot(String content) {
    if (!StructuralTrace.looksStructural(content)) {
      return "is not a structural trace";
    }
    return "breaks "
        + ValueFreeCheck.rulesRefusing(content).stream()
            .map(ValueFreeRule::id)
            .reduce((a, b) -> a + ", " + b)
            .orElse("no rule");
  }
}
