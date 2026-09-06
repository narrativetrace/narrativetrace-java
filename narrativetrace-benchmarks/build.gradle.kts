/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    jmh(project(":narrativetrace-core"))
    jmh(project(":narrativetrace-proxy"))
    jmh(project(":narrativetrace-spring"))
    jmh(project(":narrativetrace-agent"))
    jmh("org.springframework:spring-context:6.2.19")
}

val agentJar = project(":narrativetrace-agent").tasks.named<Jar>("shadowJar").map { it.archiveFile.get().asFile.absolutePath }

val jmhProfilers = (findProperty("jmhProfilers") as? String)?.split(",") ?: emptyList()

// The measurement tasks below are JavaExec, which would otherwise run on whatever JVM happens to
// be running Gradle. Pinning them to the project's toolchain makes `-Pnarrativetrace.toolchain=21`
// actually select the JVM the numbers describe — JMH forks with `java.home`, so the child JVMs
// follow. Without this the scheduled JDK-21 job would silently benchmark on the image's default.
val benchmarkLauncher = extensions.getByType<JavaToolchainService>()
    .launcherFor(extensions.getByType<JavaPluginExtension>().toolchain)

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    timeOnIteration.set("1s")
    warmup.set("1s")
    jvmArgs.set(listOf("-Xms256m", "-Xmx256m"))
    jvmArgsAppend.set(agentJar.map { listOf("-javaagent:$it=packages=ai.narrativetrace.benchmarks.agent") })
    if (jmhProfilers.isNotEmpty()) {
        profilers.set(jmhProfilers)
    }
}

// --- Tier 1: allocation invariants as a failing task ---------------------------------------
//
// `allocation-baseline.txt` has documented "these benchmarks must stay at or near their current
// B/op" since February and nothing enforced it, which is how a 7 MiB default pipeline reached
// production. These two tasks close that: `allocationBenchmark` measures, `allocationCheck`
// judges against `allocation-thresholds.properties`.
//
// Deliberately NOT wired into `check`. The run is ~2 minutes of pure CPU and needs both the JMH
// shadow jar and the agent shadow jar built, neither of which `check` otherwise produces —
// against a gate that is already minutes long, on every commit, for a regression that only
// happens when the hot path is touched. Tier 0 (`PipelineFootprintTest`, milliseconds) is the
// per-commit layer; this is the scheduled-job layer, run by private CI.

/** The OFF / noop / direct-call set: the paths whose allocation must not move. */
val allocationBenchmarkPattern = "(context_enterExit_NOOP|directCall|proxy_noopContext|proxy_OFF|agent_OFF)"

val allocationResultFile = layout.buildDirectory.file("reports/jmh/allocation.json")

val allocationBenchmark = tasks.register<JavaExec>("allocationBenchmark") {
    description = "Runs the fast-path benchmarks under the GC profiler, writing JMH JSON"
    group = "verification"
    dependsOn(tasks.named("jmhJar"), ":narrativetrace-agent:shadowJar")
    mainClass.set("org.openjdk.jmh.Main")
    javaLauncher.set(benchmarkLauncher)
    classpath = files(tasks.named("jmhJar").map { it.outputs.files })
    outputs.file(allocationResultFile)
    // A measurement is never up to date: the machine it ran on last time is not this one.
    outputs.upToDateWhen { false }
    doFirst {
        allocationResultFile.get().asFile.parentFile.mkdirs()
    }
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-f", "2", "-wi", "3", "-i", "5", "-r", "1s", "-w", "1s",
                "-prof", "gc",
                "-foe", "true",
                "-jvmArgs", "-Xms256m -Xmx256m",
                "-jvmArgsAppend", "-javaagent:${agentJar.get()}=packages=ai.narrativetrace.benchmarks.agent",
                "-rf", "json",
                "-rff", allocationResultFile.get().asFile.absolutePath,
                allocationBenchmarkPattern
            )
        }
    )
}

tasks.register("allocationCheck") {
    description = "Fails when a fast-path benchmark allocates more than allocation-thresholds.properties allows"
    group = "verification"
    dependsOn(allocationBenchmark)
    val thresholdsFile = file("allocation-thresholds.properties")
    inputs.file(thresholdsFile)
    val resultFile = allocationResultFile
    doLast {
        val thresholds = ai.narrativetrace.build.AllocationCheckSupport.readThresholds(thresholdsFile)
        val measured = ai.narrativetrace.build.BenchmarkResultSupport.read(resultFile.get().asFile)
        val verdicts = ai.narrativetrace.build.AllocationCheckSupport.verdicts(thresholds, measured)
        println(ai.narrativetrace.build.AllocationCheckSupport.table(verdicts))
        val problems = ai.narrativetrace.build.AllocationCheckSupport.problems(verdicts)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Allocation regressions (see narrativetrace-benchmarks/allocation-baseline.txt):\n" +
                    problems.joinToString("\n")
            )
        }
        println("allocationCheck: ${thresholds.size} fast-path benchmarks inside their thresholds")
    }
}

// --- Tier 2: the throughput suite, compared against the committed baseline ------------------
//
// `benchmarkSuite` runs everything with the settings `baseline.txt`'s header records;
// `benchmarkCompare` reads the JSON against that baseline with the bands in
// `benchmark-tolerances.properties` and prints the table either way. Faster never fails; a
// benchmark the baseline names and the run did not produce always does.
//
// Also not wired into `check` — the suite is minutes, and it is the scheduled job's work.

val benchmarkResultFile = layout.buildDirectory.file("reports/jmh/benchmark.json")

val benchmarkSuite = tasks.register<JavaExec>("benchmarkSuite") {
    description = "Runs the whole JMH suite with the baseline's harness settings, writing JMH JSON"
    group = "verification"
    dependsOn(tasks.named("jmhJar"), ":narrativetrace-agent:shadowJar")
    mainClass.set("org.openjdk.jmh.Main")
    javaLauncher.set(benchmarkLauncher)
    classpath = files(tasks.named("jmhJar").map { it.outputs.files })
    outputs.file(benchmarkResultFile)
    outputs.upToDateWhen { false }
    doFirst {
        benchmarkResultFile.get().asFile.parentFile.mkdirs()
    }
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-f", "1", "-wi", "3", "-i", "5", "-r", "1s", "-w", "1s",
                "-prof", "gc",
                "-foe", "true",
                "-jvmArgs", "-Xms256m -Xmx256m",
                "-jvmArgsAppend", "-javaagent:${agentJar.get()}=packages=ai.narrativetrace.benchmarks.agent",
                "-rf", "json",
                "-rff", benchmarkResultFile.get().asFile.absolutePath
            )
        }
    )
}

tasks.register("benchmarkCompare") {
    description = "Compares a JMH run against baseline.txt within the bands in benchmark-tolerances.properties"
    group = "verification"
    dependsOn(benchmarkSuite)
    val baselineFile = file("baseline.txt")
    val tolerancesFile = file("benchmark-tolerances.properties")
    inputs.file(baselineFile)
    inputs.file(tolerancesFile)
    val resultFile = benchmarkResultFile
    doLast {
        val support = ai.narrativetrace.build.BenchmarkCompareSupport
        val baseline = support.readBaseline(baselineFile)
        val tolerances = support.readTolerances(tolerancesFile)
        val current = ai.narrativetrace.build.BenchmarkResultSupport.read(resultFile.get().asFile)
        val comparisons = support.compare(baseline, current, tolerances)
        val missing = support.missing(baseline, current)
        println(support.table(comparisons, missing))
        val problems = support.problems(comparisons, missing)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Benchmark regressions against narrativetrace-benchmarks/baseline.txt:\n" +
                    problems.joinToString("\n")
            )
        }
        println("benchmarkCompare: ${comparisons.size} benchmarks inside their tolerance bands")
    }
}
