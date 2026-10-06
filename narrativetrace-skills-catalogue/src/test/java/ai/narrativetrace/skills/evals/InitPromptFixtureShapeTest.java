/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The published prompt's cases ship the fixture shape the published pages show: a one-method
 * interface and a {@code Default…} implementation, the pair {@code documentation/llms.txt} and
 * {@code sixty-seconds.md} list. The reason is mechanical, and it cost a trial to find
 * (2026-09-25): {@code NarrativeTraceProxy} wraps an INTERFACE, so a fixture whose only service is
 * a concrete class forces an agent to extract one — and the name it picks ({@code IOrderService})
 * is not the name {@code run_the_program.sh} is told to look for, whose leading guard exists
 * precisely to stop a near-miss type name from passing. Ship the interface and the faithful path
 * needs no refactor at all, while the guard keeps every bit of its protection.
 *
 * <p>Which cases those are is read out of the GRADERS too, not out of a name prefix: a case grades
 * the published prompt exactly when its verifier calls one of the two shared prompt graders with a
 * service name. So the registry cases — which reach the same prompt through a registry install
 * first — are covered by construction, and a case that stops grading the prompt drops out by
 * construction.
 *
 * @llmNote The service name is READ OUT of each case's {@code graders/verify.sh} rather than
 *     hard-coded here, so the grader's argument and the fixture's types cannot drift apart
 *     silently: renaming one without the other fails this test.
 */
class InitPromptFixtureShapeTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path INIT_PROMPT_CASES =
      REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals/add-narrative-tracing");

  /**
   * The line inside a case's grader that names the service: {@code sh …/grade_the_prompt.sh
   * <Service>} directly, or {@code sh …/grade_the_registry.sh <registry> <Service>} for a case
   * whose pages a registry delivered first.
   */
  private static final Pattern GRADED_SERVICE =
      Pattern.compile(
          "grade_the_prompt\\.sh\"?\\s+([A-Za-z_][A-Za-z0-9_]*)"
              + "|grade_the_registry\\.sh\"?\\s+[a-z][a-z-]*\\s+([A-Za-z_][A-Za-z0-9_]*)");

  /**
   * The test-half lines the install block declares, minus NarrativeTrace's own: a cold fixture may
   * not name us anywhere, but it must already be a project whose {@code test} task can RUN, because
   * the prompt's step 5 adds a test and the grader runs it.
   */
  private static final List<String> TEST_HALF_THE_INSTALL_BLOCK_DECLARES =
      List.of("org.junit.jupiter:junit-jupiter", "org.junit.platform:junit-platform-launcher");

  static List<Path> initPromptCases() {
    try (Stream<Path> entries = Files.list(INIT_PROMPT_CASES)) {
      List<Path> cases =
          entries
              .filter(Files::isDirectory)
              .filter(InitPromptFixtureShapeTest::gradesThePrompt)
              .sorted()
              .toList();
      assertThat(cases)
          .as("the cases that replay the published prompt, found by what their graders call")
          .hasSizeGreaterThan(1);
      return cases;
    } catch (IOException e) {
      throw new UncheckedIOException("could not list " + INIT_PROMPT_CASES, e);
    }
  }

  /** Whether {@code namedCase}'s verifier grades the published prompt against a named service. */
  private static boolean gradesThePrompt(Path namedCase) {
    Path grader = namedCase.resolve("graders").resolve("verify.sh");
    if (!Files.isRegularFile(grader)) {
      return false;
    }
    try {
      return GRADED_SERVICE.matcher(Files.readString(grader)).find();
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + grader, e);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("initPromptCases")
  void theGradedServiceIsAnInterfaceTheFixtureAlreadyShips(Path namedCase) throws IOException {
    String service = gradedServiceName(namedCase);
    List<String> sources = fixtureSources(namedCase);

    assertThat(sources)
        .as(namedCase.getFileName() + "'s fixture must declare `interface " + service + "`")
        .anyMatch(source -> source.contains("public interface " + service + " {"));
    assertThat(sources)
        .as(namedCase.getFileName() + "'s fixture must implement it as `Default" + service + "`")
        .anyMatch(source -> source.contains("class Default" + service + " implements " + service));
  }

  /**
   * No source in the fixture may already name NarrativeTrace: these are cold-install fixtures, and
   * a fixture that ships the wrap grades the grader rather than the agent.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("initPromptCases")
  void theFixtureHasNeverTouchedNarrativeTrace(Path namedCase) throws IOException {
    assertThat(fixtureSources(namedCase))
        .as(namedCase.getFileName() + "'s fixture must start cold")
        .noneMatch(source -> source.contains("ai.narrativetrace"));
  }

  /**
   * Step 5 needs somewhere to put a test. The install block the prompt sends a reader to now
   * carries the test half — {@code narrativetrace-junit5}, Jupiter, and the platform launcher the
   * doctor's {@code toolchain.launcher} check reads — and a fixture that declared none of it would
   * make the faithful path start by rebuilding its own test wiring, which is not what either case
   * measures. The fixture ships the two lines that are not ours; the agent adds the one that is.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("initPromptCases")
  void theFixtureCanAlreadyRunATest(Path namedCase) throws IOException {
    String buildFile =
        Files.readString(
            REPO_ROOT.resolve(CaseFixture.fixtureFor(namedCase)).resolve("build.gradle.kts"));

    assertThat(buildFile)
        .as(namedCase.getFileName() + "'s fixture must be able to run the test step 5 adds")
        .contains(TEST_HALF_THE_INSTALL_BLOCK_DECLARES)
        .contains("useJUnitPlatform()");
    assertThat(buildFile)
        .as(namedCase.getFileName() + "'s fixture must still start cold")
        .doesNotContain("ai.narrativetrace");
  }

  private static String gradedServiceName(Path namedCase) throws IOException {
    String grader = Files.readString(namedCase.resolve("graders").resolve("verify.sh"));
    Matcher matcher = GRADED_SERVICE.matcher(grader);
    assertThat(matcher.find())
        .as(namedCase.getFileName() + "'s grader must name the service run_the_program.sh checks")
        .isTrue();
    return matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
  }

  private static List<String> fixtureSources(Path namedCase) throws IOException {
    Path main = REPO_ROOT.resolve(CaseFixture.fixtureFor(namedCase)).resolve("src/main/java");
    List<String> sources = new ArrayList<>();
    try (Stream<Path> files = Files.walk(main)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        sources.add(Files.readString(file));
      }
    }
    assertThat(sources).as(namedCase.getFileName() + "'s fixture must have sources").isNotEmpty();
    return sources;
  }
}
