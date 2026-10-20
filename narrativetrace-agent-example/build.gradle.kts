/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Proves the third attachment path (see README.md): a plain class, zero NarrativeTrace
// imports anywhere in src/main, traced entirely by class-load-time bytecode weaving. The
// -javaagent flag on the `agentTest` task below is this module's whole contribution —
// everything else that task needs (AgentRuntime, NarrativeContext, TraceTree) is
// testImplementation only, never main.
//
// Wires the agent jar the same way narrativetrace-benchmarks wires it for JMH: reference
// :narrativetrace-agent's own shadowJar task output directly (this module is not published,
// so there is no Maven coordinate to depend on yet — see NarrativeTracePlugin.configureAgentJvmArg
// for the published-coordinate equivalent a real consumer's build uses instead).
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(project(":narrativetrace-agent"))
    testImplementation(project(":narrativetrace-core"))
    // The §176 proof: both test integrations route the agent's shared context into their own
    // lifecycle-owned one, so GreetingServiceAgentFixture/GreetingServiceAgentJunit4Fixture need
    // only the ordinary extension/rule wiring, same as a proxy-traced test.
    testImplementation(project(":narrativetrace-junit5"))
    testImplementation(project(":narrativetrace-junit4"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.4")
    // AgentRuntime builds its pipeline through the same PipelineBootstrap as every other
    // integration, so Slf4jTraceEventListener attaches reflectively once this is on the
    // classpath too (documentation/configuration-guide.md §7) — even in agent mode.
    // src/test/resources/logback-test.xml decides where the narration actually goes.
    testRuntimeOnly(project(":narrativetrace-slf4j"))
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

val agentShadowJar = project(":narrativetrace-agent").tasks.named<Jar>("shadowJar")

// GreetingServiceAgentTest/GreetingServiceAgentJunit4Test are tagged "agent" and live in this
// same test source set, but must never run inside the default `test` task: JaCoCo's own coverage
// agent and the narrativetrace agent both instrument GreetingService's bytecode in that JVM, and
// JaCoCo then cannot match its execution data back to the class file — it zeroes that class's
// reported coverage instead of failing loudly, which would sink the 98% gate for a class
// GreetingServiceTest covers fully on its own. The two fixture classes they each drive through a
// nested Launcher call (GreetingServiceAgentFixture, GreetingServiceAgentJunit4Fixture) are excluded
// by name rather than by tag: JUnit 4 carries no `@Tag`, and in both cases GreetingService must
// still be unloaded in the task's own JVM when the agent weaves it, so Gradle's own test discovery
// must never touch them directly either. `test` runs everything else; `agentTest`/`agentTestJunit4`
// run only the agent-woven proofs.
tasks.test {
    useJUnitPlatform {
        excludeTags("agent")
    }
    exclude("**/GreetingServiceAgentFixture.class")
    exclude("**/GreetingServiceAgentJunit4Fixture.class")
    exclude("**/ParallelGreetingAgentFixture.class")
}

val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]

fun registerAgentProofTask(name: String, description: String, vararg testClassNames: String) =
    tasks.register<Test>(name) {
        this.description = description
        group = "verification"
        useJUnitPlatform()
        filter {
            testClassNames.forEach { includeTestsMatching(it) }
        }
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        dependsOn(agentShadowJar)
        // Resolved at execution time, after shadowJar has actually run — mirrors the Gradle
        // plugin's own agent-mode wiring (NarrativeTracePlugin.configureAgentJvmArg).
        doFirst {
            val agentJarPath = agentShadowJar.get().archiveFile.get().asFile.absolutePath
            jvmArgs("-javaagent:$agentJarPath=packages=ai.narrativetrace.examples.agent")
        }
    }

val agentTestTask = registerAgentProofTask(
    "agentTest",
    "Runs the agent-mode proofs (JUnit 5: GreetingServiceAgentTest, ParallelAgentIsolationTest) with -javaagent attached",
    "ai.narrativetrace.examples.agent.GreetingServiceAgentTest",
    "ai.narrativetrace.examples.agent.ParallelAgentIsolationTest"
)
val agentTestJunit4Task = registerAgentProofTask(
    "agentTestJunit4",
    "Runs the agent-mode proof (GreetingServiceAgentJunit4Test, JUnit 4) with -javaagent attached",
    "ai.narrativetrace.examples.agent.GreetingServiceAgentJunit4Test"
)

// Not coverage-gated (see the comment on `tasks.test` above), but still runs on every `check` —
// the point being demonstrated is proven on every gate, the same as any other test.
tasks.named("check") {
    dependsOn(agentTestTask, agentTestJunit4Task)
}
