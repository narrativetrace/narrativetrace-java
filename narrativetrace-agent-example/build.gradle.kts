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
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(project(":narrativetrace-agent"))
    testImplementation(project(":narrativetrace-core"))
    // AgentRuntime builds its pipeline through the same PipelineBootstrap as every other
    // integration, so Slf4jTraceEventListener attaches reflectively once this is on the
    // classpath too (documentation/configuration-guide.md §7) — even in agent mode.
    // src/test/resources/logback-test.xml decides where the narration actually goes.
    testRuntimeOnly(project(":narrativetrace-slf4j"))
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

val agentShadowJar = project(":narrativetrace-agent").tasks.named<Jar>("shadowJar")

// GreetingServiceAgentTest is tagged "agent" and lives in this same test source set, but must
// never run inside the default `test` task: JaCoCo's own coverage agent and the narrativetrace
// agent both instrument GreetingService's bytecode in that JVM, and JaCoCo then cannot match
// its execution data back to the class file — it zeroes that class's reported coverage instead
// of failing loudly, which would sink the 98% gate for a class GreetingServiceTest covers fully
// on its own. `test` runs everything else; `agentTest` runs only the agent-woven proof.
tasks.test {
    useJUnitPlatform {
        excludeTags("agent")
    }
}

val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
val agentTestTask = tasks.register<Test>("agentTest") {
    description = "Runs the agent-mode proof (GreetingServiceAgentTest) with -javaagent attached"
    group = "verification"
    useJUnitPlatform {
        includeTags("agent")
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

// Not coverage-gated (see the comment on `tasks.test` above), but still runs on every `check` —
// the point being demonstrated is proven on every gate, the same as any other test.
tasks.named("check") {
    dependsOn(agentTestTask)
}
