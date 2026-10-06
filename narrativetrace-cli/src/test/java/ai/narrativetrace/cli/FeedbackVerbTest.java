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

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.feedback.FeedbackGatherer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every flag and every exit of {@code narrativetrace feedback}, against a real temporary project.
 *
 * <p>The verb writes files, so the project is a {@code @TempDir} rather than a stub: what it wrote
 * is the thing worth asserting, and a stubbed filesystem would assert only that the stub was
 * called. Nothing here starts a process — the {@code gh} probe is injected.
 */
class FeedbackVerbTest {

  private static final String DOCTOR_JSON =
      "{\n  \"findings\": [\n    {\n      \"id\": \"trap.redaction-proof\",\n"
          + "      \"status\": \"fail\",\n      \"message\": \"no test proves redaction\",\n"
          + "      \"fix\": \"assert the marker in a test\",\n"
          + "      \"docUrl\": \"https://narrativetrace.ai/docs/privacy-and-redaction\",\n"
          + "      \"skill\": \"narrativetrace-doctor\"\n    }\n  ],\n  \"exitCode\": 1\n}\n";

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private Cli.Deps deps(Path project, boolean ghAuthenticated) {
    return new Cli.Deps(
        project,
        p -> DoctorSnapshot.healthy(),
        from -> {
          throw new IllegalStateException("feedback must never open a carrier");
        },
        () -> ghAuthenticated,
        out,
        err);
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private static String[] command(String channel, String... extra) {
    List<String> arguments = new ArrayList<>(List.of("feedback", channel));
    arguments.addAll(
        List.of(
            "--category", "doctor",
            "--step", "trap.redaction-proof",
            "--did", "ran the doctor and applied the fix it printed",
            "--happened", "the same check failed again, with the same message",
            "--expected", "the check to pass"));
    arguments.addAll(List.of(extra));
    return arguments.toArray(String[]::new);
  }

  private static void withDoctorReport(Path project) {
    write(project.resolve(FeedbackGatherer.DOCTOR_REPORT_PATH), DOCTOR_JSON);
  }

  private static void write(Path path, String content) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // --- usage and argument errors -----------------------------------------------------------------

  @Test
  void helpExits0AndNamesEveryChannel(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback", "--help"}, deps(project, false))).isZero();
    assertThat(stdout()).contains("draft").contains("url").contains("gh");
    outBytes.reset();
    assertThat(Cli.run(new String[] {"feedback", "-h"}, deps(project, false))).isZero();
    assertThat(stdout()).contains("narrativetrace feedback <draft|url|gh>");
  }

  @Test
  void theTopLevelUsageListsTheVerb(@TempDir Path project) {
    Cli.run(new String[] {"--help"}, deps(project, false));

    assertThat(stdout()).contains("feedback   Drafts a problem report");
  }

  @Test
  void noChannelExits2AndSaysWhichOnesThereAre(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback"}, deps(project, false))).isEqualTo(2);
    assertThat(stderr()).contains("feedback needs a channel: draft, url, gh");
  }

  @Test
  void anUnknownChannelExits2(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback", "send"}, deps(project, false))).isEqualTo(2);
    assertThat(stderr()).contains("unknown feedback channel: \"send\"");
  }

  @Test
  void anUnknownOptionExits2(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback", "draft", "--nope"}, deps(project, false)))
        .isEqualTo(2);
    assertThat(stderr()).contains("unknown option: \"--nope\"");
  }

  @Test
  void aValueFlagWithoutAValueExits2(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback", "draft", "--step"}, deps(project, false)))
        .isEqualTo(2);
    assertThat(stderr()).contains("--step needs a value");
  }

  @Test
  void eachMissingMandatoryFieldIsNamedByItsOwnFlag(@TempDir Path project) {
    assertThat(
            Cli.run(
                new String[] {"feedback", "draft", "--category", "library", "--step", "s"},
                deps(project, false)))
        .isEqualTo(2);
    assertThat(stderr()).contains("--did is required");
  }

  @Test
  void aMissingOrUnknownCategoryExits2(@TempDir Path project) {
    assertThat(Cli.run(new String[] {"feedback", "draft", "--step", "s"}, deps(project, false)))
        .isEqualTo(2);
    assertThat(stderr()).contains("--category needs a value: prompt, skill, doctor or library");
    errBytes.reset();
    assertThat(
            Cli.run(
                new String[] {"feedback", "draft", "--category", "everything"},
                deps(project, false)))
        .isEqualTo(2);
    assertThat(stderr()).contains("unknown category");
  }

  @Test
  void aFlagWrittenWithAnEqualsSignIsReadTheSameWay(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(
            Cli.run(
                command("draft", "--language=pt-BR", "--agent-product=example-cli"),
                deps(project, false)))
        .isZero();
    assertThat(stdout()).contains("- language: pt-BR").contains("- agent: example-cli");
  }

  // --- draft
  // ---------------------------------------------------------------------------------------

  @Test
  void draftWritesBothFilesAndPrintsTheWholeDraft(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("draft"), deps(project, false))).isZero();

    Path draftFile = project.resolve(FeedbackVerb.DRAFT_FILE);
    Path bodyFile = project.resolve(FeedbackVerb.BODY_FILE);
    assertThat(draftFile).exists();
    assertThat(bodyFile).exists();
    assertThat(stdout()).contains(read(draftFile).strip());
    assertThat(read(draftFile)).contains(read(bodyFile));
    assertThat(stdout()).contains("Written to " + FeedbackVerb.DRAFT_FILE);
  }

  @Test
  void draftCarriesTheInstallCoordinateTheBuildDeclares(@TempDir Path project) {
    withDoctorReport(project);

    Cli.run(command("draft"), deps(project, false));

    assertThat(stdout()).contains("ai.narrativetrace:narrativetrace-junit5:0.2.2");
  }

  @Test
  void draftSaysWhyNoStructuralTraceWasAttached(@TempDir Path project) {
    withDoctorReport(project);

    Cli.run(command("draft"), deps(project, false));

    assertThat(stdout()).contains("No structural trace attached: no structural trace was found");
  }

  @Test
  void draftJsonPrintsTheEnvelopeAndNotTheDraft(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("draft", "--json"), deps(project, false))).isZero();
    assertThat(stdout())
        .contains("\"verb\": \"draft\"")
        .contains("\"status\": \"drafted\"")
        .contains("\"exitCode\": 0")
        .doesNotContain("## What I did");
  }

  @Test
  void aDoctorReportIsRequiredForTheDoctorCategoryAndItsAbsenceExits2(@TempDir Path project) {
    assertThat(Cli.run(command("draft"), deps(project, false))).isEqualTo(2);
    assertThat(stderr())
        .contains("needs the doctor's JSON report")
        .contains("file this under prompt or library instead");
    assertThat(project.resolve(FeedbackVerb.BODY_FILE)).doesNotExist();
  }

  @Test
  void aLibraryReportIsFilableWithNoDoctorReportAndSaysWhyThereIsNone(@TempDir Path project) {
    String[] arguments = command("draft");
    arguments[3] = "library";

    assertThat(Cli.run(arguments, deps(project, false))).isZero();
    assertThat(stdout())
        .contains("No doctor report: no report at " + FeedbackGatherer.DOCTOR_REPORT_PATH);
  }

  // --- the gate
  // ------------------------------------------------------------------------------------

  @Test
  void aReportCarryingAValueExits2NamesTheRuleAndWritesNothing(@TempDir Path project) {
    withDoctorReport(project);
    String[] arguments = command("draft");
    arguments[9] = "it rendered OrderService.placeOrder(customerId: \"C-1234\")";

    assertThat(Cli.run(arguments, deps(project, false))).isEqualTo(2);
    assertThat(stderr()).contains("happened: vf.rendered-call").contains("structural trace");
    assertThat(project.resolve(FeedbackVerb.BODY_FILE)).doesNotExist();
    assertThat(project.resolve(FeedbackVerb.DRAFT_FILE)).doesNotExist();
  }

  @Test
  void theRefusalIsAlsoAvailableAsTheJsonEnvelope(@TempDir Path project) {
    withDoctorReport(project);
    String[] arguments = command("url", "--json");
    arguments[9] = "ada@example.com hit it";

    assertThat(Cli.run(arguments, deps(project, false))).isEqualTo(2);
    assertThat(stderr())
        .contains("\"verb\": \"url\"")
        .contains("\"status\": \"refused\"")
        .contains("\"rule\": \"vf.email\"")
        .contains("\"exitCode\": 2");
  }

  @Test
  void aSecretInTheProjectsOwnDoctorReportRefusesTheReport(@TempDir Path project) {
    write(
        project.resolve(FeedbackGatherer.DOCTOR_REPORT_PATH),
        DOCTOR_JSON.replace(
            "no test proves redaction", "the config had \"password\": \"hunter2\""));

    assertThat(Cli.run(command("draft"), deps(project, false))).isEqualTo(2);
    assertThat(stderr()).contains("doctor report: vf.named-secret");
  }

  @Test
  void aHomeDirectoryIsRewrittenRatherThanRefused(@TempDir Path project) {
    withDoctorReport(project);
    String[] arguments = command("draft");
    arguments[7] = "ran it in /Users/ada/work/orders";

    assertThat(Cli.run(arguments, deps(project, false))).isZero();
    assertThat(stdout()).contains("~/work/orders").doesNotContain("/Users/ada");
  }

  // --- url
  // -----------------------------------------------------------------------------------------

  @Test
  void urlPrintsThePreFilledFormAndTellsTheUserWhatToPaste(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("url"), deps(project, false))).isZero();
    assertThat(stdout())
        .contains("https://github.com/narrativetrace/narrativetrace-java/issues/new?")
        .contains("template=narrativetrace-report.yml")
        .contains("paste the contents of " + FeedbackVerb.BODY_FILE)
        .contains("Filing on GitHub is public");
    assertThat(project.resolve(FeedbackVerb.BODY_FILE)).exists();
  }

  @Test
  void urlJsonCarriesTheUrlAndTheFileToPaste(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("url", "--json"), deps(project, false))).isZero();
    assertThat(stdout())
        .contains("\"verb\": \"url\"")
        .contains("\"bodyFile\": \"" + FeedbackVerb.BODY_FILE + "\"");
  }

  // --- gh
  // ------------------------------------------------------------------------------------------

  @Test
  void ghPrintsTheExactLineOnlyWhenItIsAuthenticated(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("gh"), deps(project, true))).isZero();
    assertThat(stdout())
        .contains("gh issue create --repo narrativetrace/narrativetrace-java")
        .contains("--body-file " + FeedbackVerb.BODY_FILE)
        .contains("--label from-agent")
        .contains("That line is printed, not run");
  }

  @Test
  void ghExits1WithSomethingToDoInsteadWhenItIsNotAuthenticated(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("gh"), deps(project, false))).isEqualTo(1);
    assertThat(stderr()).contains("narrativetrace feedback url");
    assertThat(stdout()).doesNotContain("gh issue create");
  }

  @Test
  void ghJsonSaysReadyOrUnavailableWithItsOwnExitCode(@TempDir Path project) {
    withDoctorReport(project);

    assertThat(Cli.run(command("gh", "--json"), deps(project, true))).isZero();
    assertThat(stdout()).contains("\"status\": \"ready\"").contains("\"exitCode\": 0");
    outBytes.reset();

    assertThat(Cli.run(command("gh", "--json"), deps(project, false))).isEqualTo(1);
    assertThat(stderr()).contains("\"status\": \"unavailable\"").contains("\"exitCode\": 1");
  }

  // --- the trace attachment
  // ------------------------------------------------------------------------

  @Test
  void aStructuralTraceUnderTheProjectIsAttached(@TempDir Path project) {
    Cli.Deps deps =
        new Cli.Deps(
            project,
            p ->
                DoctorSnapshot.healthy().toBuilder()
                    .putOutputFile(
                        "build/narrativetrace/structural/OrderServiceTest/order_is_placed.nt",
                        "scenario: Order is placed\n\n- OrderService.placeOrder(customerId)\n")
                    .build(),
            from -> {
              throw new IllegalStateException("no carrier");
            },
            () -> false,
            out,
            err);
    withDoctorReport(project);

    assertThat(Cli.run(command("draft"), deps)).isZero();
    assertThat(stdout())
        .contains("- OrderService.placeOrder(customerId)")
        .doesNotContain("No structural trace attached");
  }

  @Test
  void theVerbNeverStartsAProcessOfItsOwnOnTheTwoChannelsThatDoNotNeedOne(@TempDir Path project) {
    withDoctorReport(project);
    Cli.Deps refusing =
        new Cli.Deps(
            project,
            p -> DoctorSnapshot.healthy(),
            from -> {
              throw new IllegalStateException("no carrier");
            },
            () -> {
              throw new AssertionError("the gh probe must only run on the gh channel");
            },
            out,
            err);

    assertThat(Cli.run(command("draft"), refusing)).isZero();
    assertThat(Cli.run(command("url"), refusing)).isZero();
  }

  // --- the two ways the filesystem can refuse
  // -----------------------------------------------------

  @Test
  void anUnwritableOutputDirectoryExits1AndSaysSoRatherThanThrowing(@TempDir Path project) {
    withDoctorReport(project);
    write(project.resolve(FeedbackVerb.OUTPUT_DIRECTORY), "a file where the directory goes");

    assertThat(Cli.run(command("draft"), deps(project, false))).isEqualTo(1);
    assertThat(stderr())
        .contains("could not write the draft")
        .contains(FeedbackVerb.OUTPUT_DIRECTORY);
  }

  @Test
  void aDoctorReportThatIsNotTextExits1RatherThanFilingHalfOfIt(@TempDir Path project)
      throws IOException {
    Path report = project.resolve(FeedbackGatherer.DOCTOR_REPORT_PATH);
    Files.createDirectories(report.getParent());
    Files.write(report, new byte[] {(byte) 0xc3, (byte) 0x28});

    assertThat(Cli.run(command("draft"), deps(project, false))).isEqualTo(1);
    assertThat(stderr()).contains("could not read").contains("doctor-report.json");
  }

  @Test
  void aDoctorReportDirectoryWhereTheFileShouldBeReadsAsNoReportAtAll(@TempDir Path project)
      throws IOException {
    Files.createDirectories(project.resolve(FeedbackGatherer.DOCTOR_REPORT_PATH));
    String[] arguments = command("draft");
    arguments[3] = "prompt";

    assertThat(Cli.run(arguments, deps(project, false))).isZero();
    assertThat(stdout()).contains("No doctor report: no report at");
  }
}
