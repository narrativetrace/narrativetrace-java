/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;

/** Guided setup for a first Clarity report and an explicit, opt-in quality gate. */
public final class AddNarrativeTraceClaritySkill {

  private AddNarrativeTraceClaritySkill() {}

  static Skill build() {
    return new Skill(
        "add-narrativetrace-clarity",
        SkillClass.GUIDED,
        "Adds or verifies NarrativeTrace Clarity in a Java Gradle project. Use when you want a"
            + " first static naming report, per-element explanations, or an explicit clarity"
            + " quality gate. Runs the real clarityScan task, checks fresh nonempty JSON and"
            + " Markdown, explains the JUnit trace path, and preserves existing thresholds. Repairs"
            + " a missing or empty clarity report by registering the JUnit 5 extension or linking"
            + " the JUnit 4 class rule; route tracing itself being broken to"
            + " narrativetrace-doctor.",
        "A project has NarrativeTrace or needs a first clarity report, score explanation, or"
            + " optional build enforcement.",
        "narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer",
        List.of(
            new SkillStep(
                "Inspect the existing Gradle setup",
                new StepBody.CommandStep(List.of()),
                """
Read the target module's plugins, repositories, test framework (JUnit 4 or 5), NarrativeTrace settings, outputDir, and thresholds; preserve them. Run commands in that consumer module and adapt report paths to its outputDir.

Choose the path that matches the request:
- First static naming report: scan, inspect artifacts, and explain their contents.
- Missing test reports or an existing quality gate to repair: follow the matching JUnit path below. Validate the repair with clean clarityCheck and fresh test reports; a static scan cannot verify this repair.
- Optional future CI advice: explain the matching JUnit path and gate without changing policy until requested.
"""
                    .strip(),
                List.of(),
                null),
            new SkillStep(
                "Apply the plugin only when the project needs it",
                new StepBody.CodeStep(
                    "kotlin",
                    """
                    plugins {
                        java
                        id("ai.narrativetrace") version "0.3.0"
                    }

                    repositories {
                        mavenCentral()
                    }
                    """),
                "the project has one NarrativeTrace plugin configuration, and its existing settings"
                    + " and thresholds are preserved. When applying the plugin to a JUnit 4"
                    + " project, also set narrativeTrace { testFramework.set(\"junit4\") } before"
                    + " running tasks, including a static scan; do not silently switch its tests to"
                    + " the default JUnit 5 mode"),
            new SkillStep(
                "Run a clean static scan for a first naming report",
                new StepBody.CommandStep(List.of(ClarityCommands.CLEAN_STATIC_SCAN)),
                "clarityScan completed in the consumer project and produced a fresh report from"
                    + " compiled production classes",
                List.of(
                    new FailureNote(
                        "the task is not found",
                        "the ai.narrativetrace plugin is not applied in this consumer project",
                        "apply id(\"ai.narrativetrace\") alongside java, then rerun the scan")),
                null),
            new SkillStep(
                "Check both report artifacts and read their contents",
                new StepBody.CommandStep(
                    List.of(
                        ClarityCommands.FIND_JSON_REPORT, ClarityCommands.FIND_MARKDOWN_REPORT)),
                "both "
                    + ClarityCommands.JSON_REPORT_PATH
                    + " and "
                    + ClarityCommands.MARKDOWN_REPORT_PATH
                    + " are fresh, nonempty, and were read; the scan JSON has nonempty scenarios"
                    + " with elements arrays and the Markdown explains ranked issues when any exist"
                    + " plus per-element notes. These scan artifacts remain distinct from the JUnit"
                    + " test artifacts "
                    + ClarityCommands.JSON_REPORT_PATH.replace(
                        "clarity-scan-results.json", "clarity-results.json")
                    + " and "
                    + ClarityCommands.MARKDOWN_REPORT_PATH.replace(
                        "clarity-scan-report.md", "clarity-report.md"),
                List.of(),
                null),
            new SkillStep(
                "Explain scores, notes, and coverage",
                new StepBody.CommandStep(List.of()),
                """
Base the explanation on the report you read:

- Present a short table of element, observed score, and the report's exact note. Label your own interpretation or optional rename advice separately as suggestions.
- Also write that table to build/narrativetrace/clarity-explanation.md (adapt the path to the project's own outputDir): one row for EVERY element of EVERY scenario in the JSON, using each element's full name exactly as printed (an interface and its implementation are separate scenarios with their own rows, never merged), with observed score, the report's exact note verbatim, then any suggestion in its own column.
- overallScore (0–1) weights method names 30%, parameter names 25%, class names 20%, structural quality 15%, and cohesion 10%.
- structuralScore reflects parameter count and call depth. Static scanning uses flat nodes: it assesses parameter count but cannot establish runtime call depth or test coverage.
- Cohesion measures vocabulary consistency within classes. An empty issues array means no scoring rules flagged an issue; it does not prove every name is unambiguous.
"""
                    .strip(),
                List.of(),
                null),
            new SkillStep(
                "For JUnit 5, register the extension and capture calls",
                new StepBody.SnippetStep(
                    "java",
                    "narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer/src/test/java/com/example/orders/OrderServiceTest.java"),
                "adapt this example to an existing service interface and its implementation."
                    + " Register the extension and execute a proxied call. The plugin supplies"
                    + " -parameters and JUnit runtime dependencies, but does not register the"
                    + " extension. Output defaults to true; remove an explicit"
                    + " narrativetrace.output=false when enabling reports. Alternative to"
                    + " @ExtendWith on every class: set"
                    + " junit.jupiter.extensions.autodetection.enabled=true in"
                    + " junit-platform.properties when the project prefers not to annotate each"
                    + " test class; this registers every ServiceLoader-published extension on the"
                    + " test classpath, not only NarrativeTrace's, and still requires a proxied"
                    + " call to actually capture a trace"),
            new SkillStep(
                "For JUnit 4, retain its framework configuration",
                new StepBody.CodeStep(
                    "kotlin",
                    """
                    narrativeTrace {
                        testFramework.set("junit4")
                    }
                    """),
                "use this branch only for an existing JUnit 4 suite; skip the JUnit 5 example. The"
                    + " plugin adds narrativetrace-junit4 and leaves the JUnit 4 runner in use. A"
                    + " plain JUnit 4 suite does not need Jupiter or useJUnitPlatform(); preserve"
                    + " an intentional Vintage setup if the project already uses one"),
            new SkillStep(
                "For JUnit 4, link the class rule and per-test rule",
                new StepBody.SnippetStep(
                    "java",
                    "narrativetrace-junit4-example/src/test/java/ai/narrativetrace/examples/junit4/GreetingServiceTest.java"),
                "adapt the compiled example to the project's service. Use org.junit.Test, a public"
                    + " test class and public test methods, a public static @ClassRule, and a"
                    + " public @Rule created by classRule.testRule(). Capture calls through"
                    + " narrativeTrace.context(). A standalone new NarrativeTraceRule() writes"
                    + " per-test traces but does not feed the class rule's aggregate Clarity"
                    + " report. NarrativeTestCase is an alternative only when the test can use that"
                    + " superclass. Output defaults to true; JUnit 4 reads narrativetrace.output"
                    + " and outputDir as test JVM system properties, not junit-platform.properties."
                    + " The linked class rule writes clarity-results.json and clarity-report.md"
                    + " after each class, aggregating completed classes in that JVM"),
            new SkillStep(
                "Add enforcement only when requested",
                new StepBody.CodeStep(
                    "kotlin",
                    """
                    narrativeTrace {
                        clarity {
                            // Example policy: use the requested values; retain existing limits.
                            minScore.set(0.80)
                            maxHighIssues.set(0)
                        }
                    }
                    """),
                ClarityCommands.CLEAN_CLARITY_CHECK,
                List.of(),
                null),
            new SkillStep(
                "Verify the test report before calling the gate successful",
                new StepBody.CommandStep(List.of()),
                """
After clean clarityCheck, read fresh clarity-results.json and clarity-report.md in the configured outputDir; require nonempty scenarios with scores and elements.

- Gate input: test-generated clarity-results.json. Static scanning produces a separate report; the default plugin has no gate wired to static scores. Choose test thresholds from observed test reports.
- Score condition: each scenario's overallScore must meet minScore. Overall 0.82 passes minimum 0.80 even with methodNameScore 0.55, provided issue limits pass. Component and element scores are not individually gated.
- Issue conditions: maxHighIssues limits HIGH issues per scenario; maxSuiteIssues limits suite-level issues.
- Missing JSON skips the gate; warnOnly=true is advisory. Neither proves enforcement.
- When enforcement is requested, temporarily tighten a threshold above an observed test score, confirm the specific Clarity check failed diagnostic, restore the original policy, and rerun.
"""
                    .strip(),
                List.of(),
                null),
            new SkillStep(
                "Hand missing tracing or output setup to the doctor",
                new StepBody.CommandStep(List.of()),
                "if trace reports remain missing, run narrativetrace-doctor for diagnosis. For a"
                    + " JUnit 4 suite its config.junit4-rule check reports a suite with no"
                    + " NarrativeTrace rule linked; it does not tell a standalone"
                    + " NarrativeTraceRule from one created by classRule.testRule(), so confirm"
                    + " that linkage for the aggregate report yourself. Its extension,"
                    + " Jupiter-version, and launcher checks target JUnit 5 and are not a reason to"
                    + " migrate a JUnit 4 suite. When the user requested setup or repair, apply the"
                    + " identified configuration fix and repeat verification; do not stop after"
                    + " merely naming the doctor",
                List.of(),
                null)),
        List.of(
            new ReasonedRule(
                "Start from clean output and inspect both artifacts",
                "a successful Gradle task can still leave a stale or empty report"),
            new ReasonedRule(
                "Treat the static and JUnit paths as different coverage",
                "compiled classes and executed traces answer different questions")),
        List.of(
            new ReasonedRule(
                "Do not describe static scanning as default-gate enforcement or copy scan JSON over"
                    + " test JSON",
                "the CI command ./gradlew clean clarityCheck gates traced-test reports. Running a"
                    + " static scan alongside it does not connect static scores to that gate"),
            new ReasonedRule(
                "Report observed scores and notes without promising a score for an untested rename",
                "a rename's score must be measured by another run; scan scores alone do not prove"
                    + " the test quality gate passed"),
            new ReasonedRule(
                "Do not lower or replace existing thresholds to hide failures",
                "enforcement is an explicit project choice and a failure is useful evidence"),
            new ReasonedRule(
                "Do not harvest a glossary merely to read existing vocabulary",
                "clarity reads a committed glossary; harvesting is a separate opt-in write"),
            new ReasonedRule(
                "Do not call a missing report successful",
                "clarityCheck skips when JSON is absent, so artifact freshness must be checked")),
        CommandVocabulary.JAVA);
  }
}
