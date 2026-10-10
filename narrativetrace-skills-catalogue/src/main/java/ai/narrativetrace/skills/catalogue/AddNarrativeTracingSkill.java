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

/**
 * {@code add-narrative-tracing} — setup. Install, first trace, and the logger bridge: steps 1, 2
 * and 6 of the original six-step evidenced pool (frozen ruling, {@code agent-skills-2026-09-12.md}
 * §7 ruling 9); names {@code narrativetrace-doctor}, which owns diagnosis from there, and closes by
 * PREVIEWING the installer so the next session finds the skills without being told about them.
 *
 * <p>Right after the install, ONE step hands framework wiring to the doctor (Phase 6, D2 as
 * amended): run it, apply every {@code config.<framework>-*} fix in order. The page names no
 * framework — the installed doctor's own framework table is the oracle, so a release that adds a
 * row reaches existing projects through the doctor, never through a stale skill page.
 *
 * <p>Every step is written for what an ADOPTER runs in their own project — the install block is the
 * same Maven Central coordinates the onboarding doc's own "Install and first trace" block states,
 * and every command stays in the closed vocabulary ({@link CommandVocabulary#JAVA}); {@code
 * SkillReplayer} (Tier A2) is what maps each of these onto the {@code sixty-seconds} fixture for
 * replay, never the other way around.
 *
 * <p>The first-trace step's body is a {@link StepBody.SnippetStep} naming {@code
 * sixty-seconds/src/main/java/com/example/orders/Main.java}, not a literal copy of it: {@code
 * ClaudeSkillRenderer} reads the fixture's own file at render time, the same file {@code
 * SkillReplayer} already runs above, so the rendered page can never quietly drift from what
 * actually executes (rule 8, docs as tests) — a literal string here could.
 */
public final class AddNarrativeTracingSkill {

  /** Failure text shared by every step that runs {@link DoctorCommands#RUN_DOCTOR_GRADLE}. */
  static final FailureNote DOCTOR_TASK_NOT_FOUND =
      new FailureNote(
          "the task fails with \"Task 'narrativetraceDoctor' not found\"",
          "the ai.narrativetrace Gradle plugin isn't applied to this project",
          "add id(\"ai.narrativetrace\") to the plugins block, or run the standalone"
              + " narrativetrace-cli launcher instead");

  /** The same missing-plugin symptom the doctor step has, with the installer's own fallback. */
  static final FailureNote INSTALLER_TASK_NOT_FOUND =
      new FailureNote(
          "the task fails with \"Task 'narrativetraceInit' not found\"",
          "the ai.narrativetrace Gradle plugin isn't applied to this project",
          "add id(\"ai.narrativetrace\") to the plugins block, or preview the same install with the"
              + " standalone launcher: narrativetrace init --dry-run");

  /**
   * Step 1's condition (Phase 6, D2). Names no framework: whether the project already has an entry
   * point is read from its build and the doctor's report, never from a list on this page. The
   * {@code application} plugin's {@code mainClass} becomes the packaged application's start class,
   * so adding it to a project that already starts itself replaces the project's own deliverable.
   */
  static final String ENTRY_POINT_ADDS_NO_APPLICATION_PLUGIN =
      "if the project already has an application entry point — a main class, a web or application"
          + " framework the doctor reports — add only the dependencies and the -parameters flag"
          + " below and leave out the application plugin and the application { mainClass } block;"
          + " otherwise use the block as written";

  /**
   * Step 3's condition (Phase 6, D2): in a project that already starts itself, the first trace is
   * one real boundary's, read from the application the way it already runs — never a demo main.
   */
  static final String ENTRY_POINT_RUNS_ITSELF =
      "if the project already has an application entry point — a main class, a web or application"
          + " framework the doctor reports — do not add a demo main: run the application the way it"
          + " already runs, exercise one real boundary, and read that request's trace; the"
          + " verify below is for the console app, and in an existing application the step is done"
          + " when that request's trace is in the output; otherwise create the smallest console app"
          + " as follows";

  /**
   * The install's hand-off to the next session (Phase 7, D7): once the skills are installed, the
   * session after this one reads what its changes did before reporting them.
   */
  static final String NEXT_SESSION_VERIFIES =
      "; the next session verifies with narrativetrace-verify — once a change's tests are green, it"
          + " reads the trace before it reports";

  private AddNarrativeTracingSkill() {}

  static Skill build() {
    return new Skill(
        "add-narrative-tracing",
        SkillClass.GUIDED,
        "Installs NarrativeTrace into a Java project and gets it to a first trace. Use when"
            + " NarrativeTrace is not yet installed, a project needs its very first traced call, or"
            + " traces need to reach a real logger instead of standard output. Adds"
            + " narrativetrace-core and narrativetrace-proxy with Gradle, wraps a class with"
            + " NarrativeTraceProxy.trace, renders and runs the first trace, then wires an"
            + " SLF4J/Logback consumer so traces reach your logger. Applies the doctor's"
            + " framework-wiring fixes for the frameworks the project already uses, and runs"
            + " narrativetrace-doctor to confirm the install is correctly wired —"
            + " narrativetrace-doctor owns diagnosis from there — and ends by previewing the"
            + " agent-skills install so the next session finds them. Say 'add narrative tracing to"
            + " my service', 'install narrativetrace', 'get a trace in sixty seconds', 'wrap this"
            + " class so I can see a trace', or 'send my traces to my logger' to invoke it.",
        null,
        "sixty-seconds",
        List.of(
            new SkillStep(
                "Install with the real toolchain",
                new StepBody.CodeStep(
                    "kotlin",
                    """
// build.gradle.kts
plugins {
    java
    application   // only when the project has no application entry point of its own
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.3.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.3.0")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // without it, traces show arg0, arg1
}

application {   // only when the project has no application entry point of its own
    mainClass.set("com.example.orders.Main")
}
"""),
                DoctorCommands.BUILD_PROJECT,
                List.of(
                    new FailureNote(
                        "dependency resolution fails offline",
                        "a version pin drifted from what is actually published to Maven Central",
                        "check the exact coordinate against"
                            + " documentation/installation-guide.md's dependency block")),
                null,
                ENTRY_POINT_ADDS_NO_APPLICATION_PLUGIN),
            new SkillStep(
                "Wire the frameworks this project already uses",
                new StepBody.CommandStep(List.of(DoctorCommands.RUN_DOCTOR_GRADLE)),
                DoctorCommands.VERIFY_FRAMEWORK_FIXES_APPLIED,
                List.of(DOCTOR_TASK_NOT_FOUND),
                null),
            new SkillStep(
                "First trace: wrap, call, render, run",
                new StepBody.SnippetStep(
                    "java", "sixty-seconds/src/main/java/com/example/orders/Main.java"),
                DoctorCommands.RUN_PROJECT,
                List.of(),
                null,
                ENTRY_POINT_RUNS_ITSELF),
            new SkillStep(
                "Send it to your logger",
                new StepBody.CodeStep(
                    "kotlin",
                    """
                    dependencies {
                        runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.3.0")
                        runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
                    }
                    """),
                "traces reach the configured logger appender instead of standard output",
                List.of(
                    new FailureNote(
                        "traces still print to standard output, not the logger",
                        "narrativetrace-slf4j attaches to the pipeline reflectively once it is on"
                            + " the runtime classpath — a compile-only or test-only dependency"
                            + " scope that never reaches the running classpath leaves it"
                            + " unattached",
                        "add narrativetrace-slf4j on runtimeOnly (or testRuntimeOnly for a"
                            + " test-only logger), not compileOnly")),
                null),
            new SkillStep(
                "Run narrativetrace-doctor and resolve every finding",
                new StepBody.CommandStep(List.of(DoctorCommands.RUN_DOCTOR_GRADLE)),
                DoctorCommands.VERIFY_EVERY_FINDING,
                List.of(DOCTOR_TASK_NOT_FOUND),
                null),
            new SkillStep(
                "Install the skills for next time",
                new StepBody.CommandStep(List.of(InstallerCommands.PREVIEW_INSTALL)),
                InstallerCommands.VERIFY_PREVIEW_WROTE_NOTHING + NEXT_SESSION_VERIFIES,
                List.of(INSTALLER_TASK_NOT_FOUND),
                null)),
        List.of(
            new ReasonedRule(
                "Never assume a step worked without its verify",
                "reproduces-from-clean is the gate; a step that looks right and was never checked"
                    + " is exactly the failure mode the studies found")),
        List.of(
            new ReasonedRule(
                "Never skip the narrativetrace-doctor call",
                "it is the seam to diagnosis — every other skill's failure path names it, and"
                    + " skipping it here is the one place that convention would go uncompleted"),
            new ReasonedRule(
                "Never apply the installer without showing its diff first",
                "it writes into AGENTS.md and the project's skill directories, and the approval for"
                    + " that is a person reading the diff — run it with --diff, show the output,"
                    + " and let them run it again without the flag")),
        CommandVocabulary.JAVA);
  }
}
