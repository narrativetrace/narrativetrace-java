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
 * §7 ruling 9); ends by naming {@code narrativetrace-doctor}, which owns diagnosis from there.
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

  private AddNarrativeTracingSkill() {}

  static Skill build() {
    return new Skill(
        "add-narrative-tracing",
        SkillClass.GUIDED,
        "Installs NarrativeTrace into a Java project and gets it to a first trace. Use when"
            + " NarrativeTrace is not yet installed, a project needs its very first traced call,"
            + " or traces need to reach a real logger instead of standard output. Adds"
            + " narrativetrace-core and narrativetrace-proxy with Gradle, wraps a class with"
            + " NarrativeTraceProxy.trace, renders and runs the first trace, then wires an"
            + " SLF4J/Logback consumer so traces reach your logger. Ends by running"
            + " narrativetrace-doctor to confirm the install is correctly wired —"
            + " narrativetrace-doctor owns diagnosis from there. Say 'add narrative tracing to my"
            + " service', 'install narrativetrace', 'get a trace in sixty seconds', 'wrap this"
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
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.2.2")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.2")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // without it, traces show arg0, arg1
}

application {
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
                null),
            new SkillStep(
                "First trace: wrap, call, render, run",
                new StepBody.SnippetStep(
                    "java", "sixty-seconds/src/main/java/com/example/orders/Main.java"),
                DoctorCommands.RUN_PROJECT,
                List.of(),
                null),
            new SkillStep(
                "Send it to your logger",
                new StepBody.CodeStep(
                    "kotlin",
                    """
                    dependencies {
                        runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.2.2")
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
                DoctorCommands.VERIFY_ELEVEN_FINDINGS,
                List.of(DOCTOR_TASK_NOT_FOUND),
                null)),
        List.of(
            new ReasonedRule(
                "Never assume a step worked without its verify",
                "reproduces-from-clean is the gate; a step that looks right and was never checked"
                    + " is exactly the failure mode the studies found")),
        List.of(
            new ReasonedRule(
                "Never skip the final narrativetrace-doctor call",
                "it is the seam to diagnosis — every other skill's failure path names it, and"
                    + " skipping it here is the one place that convention would go uncompleted")),
        CommandVocabulary.JAVA);
  }
}
