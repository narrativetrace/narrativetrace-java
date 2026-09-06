/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
repositories {
    mavenCentral()
}

// Applied to the ROOT project only (not in `subprojects {}`): the plugin then
// auto-registers a `cyclonedxDirectBom` task on every subproject and
// aggregates them into one root `cyclonedxBom` task/SBOM, which is what
// osvScan (below) points OSV-Scanner at — see documentation/security-tooling.md
// for why a real manifest is needed rather than a directory-wide scan.
apply(plugin = "org.cyclonedx.bom")

val publishedModules = subprojects.filter { sub ->
    !sub.path.startsWith(":narrativetrace-examples") && sub.name !in setOf(
        "narrativetrace-benchmarks",
        "narrativetrace-build-tests",
        "narrativetrace-gradle-plugin",
        "narrativetrace-junit4-example",
        "narrativetrace-agent-example",
        "narrativetrace-jcstress",
        "narrativetrace-security-tests"
    )
}

tasks.register<Javadoc>("aggregateJavadoc") {
    description = "Generates aggregated Javadoc across all published modules"
    group = "documentation"
    title = "NarrativeTrace API"
    dependsOn(publishedModules.map { it.tasks.named("classes") })
    source(publishedModules.map { it.the<SourceSetContainer>()["main"].allJava })
    classpath = files(publishedModules.map { it.the<SourceSetContainer>()["main"].compileClasspath })
    destinationDir = layout.buildDirectory.dir("docs/aggregateJavadoc").get().asFile
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:none", "-quiet")
        links("https://docs.oracle.com/en/java/javase/17/docs/api/")
    }
}

tasks.register<Copy>("generateLlmsDocs") {
    description = "Copies llms-full.md to build/site/llms-full.txt and generates llms.txt"
    group = "documentation"
    from("documentation/llms-full.md")
    into(layout.buildDirectory.dir("site"))
    rename("llms-full.md", "llms-full.txt")
    doLast {
        val llmsTxt = file("documentation/llms.txt")
        if (llmsTxt.exists()) {
            llmsTxt.copyTo(layout.buildDirectory.file("site/llms.txt").get().asFile, overwrite = true)
        }
    }
}

// Licensing is a build property, not a convention: `licensing.properties` says which licence each
// module ships under, and this check refuses a dependency graph the licences cannot support. It
// runs against the *resolved* project dependencies rather than the build files, so a dependency
// added through a convention plugin is caught the same as one written by hand.
tasks.register("licensingCheck") {
    description = "Verifies every module declares a licensing category and respects its direction"
    group = "verification"
    val licensingFile = rootProject.file(ai.narrativetrace.build.LicensingCategorySupport.FILE_NAME)
    inputs.file(licensingFile)
    val moduleGraph = subprojects.map { sub ->
        ai.narrativetrace.build.ModuleLicensing(
            module = moduleKey(sub),
            dependencies = sub.configurations
                .filter { it.name in LICENSED_CONFIGURATIONS }
                .flatMap { it.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { it.path.removePrefix(":") }
        )
    }
    doLast {
        val declared = ai.narrativetrace.build.LicensingCategorySupport.readText(licensingFile)
        val problems = ai.narrativetrace.build.LicensingCategorySupport.check(declared, moduleGraph)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Licensing violations (see licensing.properties):\n" + problems.joinToString("\n")
            )
        }
        println("licensingCheck: ${declared.size} modules declared, dependency direction holds")
    }
}

/**
 * Configurations whose dependencies end up in what a consumer receives. Test-only configurations
 * are deliberately absent: what a module compiles its tests against never reaches a user, so a
 * `free` test dependency in an `open` module is not a licence problem.
 */
val LICENSED_CONFIGURATIONS = setOf("api", "implementation", "compileOnly", "runtimeOnly")

/** `licensing.properties` keys drop the leading colon: `narrativetrace-examples:ecommerce`. */
fun moduleKey(project: Project): String = project.path.removePrefix(":")

/**
 * The version the release workflow asks for, straight from the build that will produce the
 * artifacts.
 *
 * INTENT: `release.yml` previously `sed`-ed a `version = "..."` literal out of `build.gradle.kts`
 * and out of the plugin's build file. Neither has ever held one — the version comes from
 * `gradle.properties` via `providers.gradleProperty("narrativetraceVersion")` — so both greps
 * matched nothing, and a release gate that always answers the empty string is not a gate. Found by
 * an adversarial review. A regex over build source can drift from the
 * build; asking the build cannot.
 *
 * One task serves both checks because there is one version: the libraries and the Gradle plugin
 * share it deliberately (see gradle.properties), since the plugin resolves the library version
 * from its own.
 */
tasks.register("printVersion") {
    description = "Prints the single version every published module shares (release gate input)"
    group = "help"
    val declared = providers.gradleProperty("narrativetraceVersion")
    doLast {
        println(declared.get())
    }
}

/**
 * `scripts/verify-publication.sh`'s only source of what to check post-release.
 *
 * INTENT: the module list a post-publish verification script polls must come from the build,
 * not be copy-pasted into the script — an 18th module added here and forgotten there is a gap the
 * script would never report. One line per artifact, `KIND group artifactId`: `LIBRARY` for every
 * `publishedModules` entry (the same set `aggregateJavadoc` already trusts), plus one `PLUGIN` line
 * for the Gradle Plugin Portal marker coordinate — group equals the plugin id, artifactId is
 * `<pluginId>.gradle.plugin`, the portal's own convention for the marker that lets `plugins { id(...) }`
 * resolve without a group. The plugin id is read from the plugin project's own `gradlePlugin { }`
 * block (not restated here) so a rename there cannot silently desync the two.
 *
 * No version on either line: `printVersion` above is the one place that answers "which version",
 * and a released version does not have to be the `narrativetraceVersion` this checkout currently
 * declares (the working tree moves on to the next `-SNAPSHOT` right after a release).
 */
tasks.register("printPublishedCoordinates") {
    description = "Prints group + artifactId for every published library and the Gradle Plugin " +
        "Portal marker, one per line (post-publish verification input)"
    group = "help"
    val gradlePluginProject = project(":narrativetrace-gradle-plugin")
    doLast {
        publishedModules.sortedBy { it.name }.forEach { module ->
            println("LIBRARY ${module.group} ${module.name}")
        }
        val pluginId =
            gradlePluginProject.extensions
                .getByType<org.gradle.plugin.devel.GradlePluginDevelopmentExtension>()
                .plugins
                .getByName("narrativeTrace")
                .id
        println("PLUGIN $pluginId $pluginId.gradle.plugin")
    }
}

// Staleness (blob-hash headers) always runs. When documentation/i18n/manifest.json is present, three
// more checks run against it — completeness, structure parity, index/menu integrity — plus a
// warn-only review-field summary; absent, the task degrades to the original staleness-only check
// with one warning saying so. See TranslationPlatformSupport and documentation/i18n/manifest.json.
tasks.register("translationCheck") {
    description = "Verifies translated docs are in sync, complete, structurally sound, and correctly indexed"
    group = "verification"
    doLast {
        val result = ai.narrativetrace.build.TranslationPlatformSupport.runAll(rootDir)
        result.warnings.forEach { println(it) }
        if (result.failures.isNotEmpty()) {
            throw GradleException(
                "Translation platform check failed (see the i18n terminology conventions):\n" +
                    result.failures.joinToString("\n")
            )
        }
        println("translationCheck: all translated documents are in sync, complete, and correctly indexed")
    }
}

// Human dashboard, not a gate: the full coverage/review matrix across every declared language.
// Deliberately not wired into `check` — publish-gating on review status is a later owner decision.
tasks.register("translationStatus") {
    description = "Prints the full translation coverage and review-field matrix (not part of check)"
    group = "help"
    doLast {
        val manifest = ai.narrativetrace.build.I18nManifestSupport.loadOrNull(rootDir)
        println(ai.narrativetrace.build.TranslationReviewSupport.statusReport(rootDir, manifest))
    }
}

// Warn-only, and deliberately so: a stale baseline is not a broken build, it is a public claim
// whose evidence expired. It went six months unnoticed once — through a buffer refactor and a
// level rename — because nothing ever said how old it was. Now every build says.
// Both committed baselines, not just the timing one: `allocationCheck` gates against
// `allocation-baseline.txt`'s thresholds, so its age is a claim in exactly the same way.
tasks.register("baselineFreshnessCheck") {
    description = "Warns when a committed JMH baseline is older than 90 days; never fails"
    group = "verification"
    val baselineFiles = listOf(
        rootProject.file("narrativetrace-benchmarks/baseline.txt"),
        rootProject.file("narrativetrace-benchmarks/allocation-baseline.txt"),
    )
    inputs.files(baselineFiles)
    doLast {
        val today = java.time.LocalDate.now()
        baselineFiles.forEach { println(ai.narrativetrace.build.BaselineFreshnessSupport.report(it, today)) }
    }
}

tasks.register("demoWiringCheck") {
    description = "Verifies every example scenario has a wiring note in the demo launcher"
    group = "verification"
    doLast {
        val problems = ai.narrativetrace.build.DemoWiringSupport.check(rootDir)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "demo.sh cannot explain every scenario (see narrativetrace-examples/demo/wiring.awk):\n" +
                    problems.joinToString("\n")
            )
        }
        println("demoWiringCheck: every example scenario has a wiring note")
    }
}

tasks.register("pitest") {
    description = "Runs mutation testing (PIT) across api, core, proxy, clarity, and glossary modules"
    group = "verification"
    dependsOn(
        ":narrativetrace-api:pitest",
        ":narrativetrace-core:pitest",
        ":narrativetrace-proxy:pitest",
        ":narrativetrace-clarity:pitest",
        ":narrativetrace-glossary:pitest"
    )
    doLast {
        val entries = ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects)
        ai.narrativetrace.build.MutationReportSupport.printReport(entries)
    }
}

// Isolated from the "pitest" aggregate above on purpose — see the pitest-extension comment on
// the narrativetrace-agent subproject block for why this module gets its own task and its own
// GitLab job (`mutation-agent`) rather than a sixth dependsOn entry above.
tasks.register("pitestAgent") {
    description = "Runs mutation testing (PIT) on the agent module, isolated in its own time box"
    group = "verification"
    dependsOn(":narrativetrace-agent:pitest")
    doLast {
        val entries =
            ai.narrativetrace.build.MutationReportSupport.collectEntries(
                listOf(
                    ai.narrativetrace.build.MutationReportInput(
                        "narrativetrace-agent",
                        file("narrativetrace-agent/build/reports/pitest/mutations.xml")
                    )
                )
            )
        ai.narrativetrace.build.MutationReportSupport.printReport(entries)
    }
}

tasks.register("mutationReport") {
    description = "Shows mutation testing results from latest pitest run"
    group = "verification"
    doLast {
        val entries = ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects)
        ai.narrativetrace.build.MutationReportSupport.printReport(entries)
    }
}

tasks.register("coverageReport") {
    description = "Shows per-class line coverage across all modules, sorted by missed lines"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("jacocoTestReport") })
    doLast {
        val entries = ai.narrativetrace.build.CoverageReportSupport.collectEntriesFromProjects(subprojects)
        ai.narrativetrace.build.CoverageReportSupport.printReport(entries)
    }
}

tasks.register("metricsReport") {
    description = "Aggregates NCSS metrics from all modules, sorted by size (descending)"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("pmdMetrics") })
    doLast {
        val entries = ai.narrativetrace.build.MetricsReportSupport.collectEntriesFromProjects(subprojects)
        ai.narrativetrace.build.MetricsReportSupport.printReport(entries)
    }
}

fun machineHash(): String {
    val input = listOf(
        System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "unknown",
        Runtime.getRuntime().availableProcessors().toString(),
        System.getProperty("os.arch"),
        System.getProperty("os.name")
    ).joinToString("|")
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.take(4).joinToString("") { "%02x".format(it) }
}

tasks.register("calibratePerf") {
    group = "verification"
    description = "Measures host machine performance and writes a calibration factor"
    val hash = machineHash()
    val outFile = file("perf-calibration/$hash.properties")
    outputs.upToDateWhen { outFile.exists() }
    doLast {
        if (outFile.exists()) {
            println("[calibration] Machine $hash already calibrated, skipping.")
            return@doLast
        }
        println("[calibration] Running calibration probes for machine $hash ...")
        val referenceScore = 62_000_000L
        fun singleProbeRun(): Long {
            var sum = 0.0
            val start = System.nanoTime()
            for (i in 1..1_000_000) {
                sum += Math.sqrt(i.toDouble()) + Math.log(i.toDouble())
            }
            val elapsedNs = System.nanoTime() - start
            return (1_000_000_000.0 / elapsedNs * 1_000_000).toLong()
        }
        val runs = (1..5).map { singleProbeRun() }.sorted()
        val score = runs[runs.size / 2]
        val factor = score.toDouble() / referenceScore
        outFile.parentFile.mkdirs()
        val hostname = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "unknown"
        outFile.writeText("""
            machine.hash=$hash
            machine.hostname=$hostname
            calibration.factor=${"%.4f".format(factor)}
            calibration.timestamp=${java.time.Instant.now()}
            calibration.jvm.version=${Runtime.version()}
            calibration.cpu.cores=${Runtime.getRuntime().availableProcessors()}
        """.trimIndent())
        println("[calibration] Done. Score=$score, factor=${"%.4f".format(factor)}")
    }
}

tasks.register("pmdReport") {
    description = "Shows PMD violations across all modules from latest check run"
    group = "verification"
    doLast {
        val violations = ai.narrativetrace.build.PmdViolationSupport.collectViolationsFromProjects(subprojects)
        ai.narrativetrace.build.PmdViolationSupport.printReport(violations)
    }
}

// gitleaks: a named entry point so CI encodes only this task's name, never the
// binary's path, flags or version (THIN-CI rule). The local pre-commit hook
// covers the staged diff on every commit; this covers the whole git history —
// for a periodic sweep, and for a machine that never ran the hook. Degrades
// gracefully: warns and passes when the `gitleaks` binary is absent locally,
// the same convention the pre-commit hook uses.
// Deliberately not wired into `check` or a CI job here — see
// documentation/security-tooling.md for the cadence this repo runs it at.
fun findExecutableOnPath(name: String): String? {
    val path = System.getenv("PATH") ?: return null
    return path.split(File.pathSeparatorChar)
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }
        ?.absolutePath
}

tasks.register("gitleaksScan") {
    description = "Scans the full git history for secrets with gitleaks (warns and passes if the binary is absent)"
    group = "verification"
    doLast {
        val gitleaks = findExecutableOnPath("gitleaks")
        if (gitleaks == null) {
            println(
                "gitleaksScan: 'gitleaks' not found on PATH — skipped. " +
                    "Install: https://github.com/gitleaks/gitleaks#installing (see documentation/security-tooling.md)."
            )
            return@doLast
        }
        val reportFile = layout.buildDirectory.file("reports/gitleaks/report.json").get().asFile
        reportFile.parentFile.mkdirs()
        val process = ProcessBuilder(
            gitleaks, "detect", "--redact", "--no-banner", "-f", "json", "-r", reportFile.absolutePath
        ).directory(rootDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        println(output)
        if (exitCode != 0) {
            throw GradleException(
                "gitleaksScan: gitleaks reported a probable secret — see $reportFile and the output above"
            )
        }
        println("gitleaksScan: clean — full git history scanned, report at $reportFile")
    }
}

// Semgrep: a named entry point over the community `p/java` registry ruleset
// (OSS/community-maintained; no custom rules — see documentation/security-tooling.md
// for why custom rules are out of scope here). Fetching the ruleset needs
// network, which is why this is not in `check` or a per-push job: wired into
// private CI on merge-request and scheduled pipelines only. Degrades
// gracefully when the `semgrep` binary is absent locally.
tasks.register("semgrepScan") {
    description = "Runs Semgrep's community p/java security ruleset over the source tree (needs network)"
    group = "verification"
    doLast {
        val semgrep = findExecutableOnPath("semgrep")
        if (semgrep == null) {
            println(
                "semgrepScan: 'semgrep' not found on PATH — skipped. " +
                    "Install: https://semgrep.dev/docs/getting-started/ (see documentation/security-tooling.md)."
            )
            return@doLast
        }
        val reportFile = layout.buildDirectory.file("reports/semgrep/results.json").get().asFile
        reportFile.parentFile.mkdirs()
        val process = ProcessBuilder(
            semgrep, "--config=p/java", "--metrics=off", "--json", "--output=${reportFile.absolutePath}"
        ).directory(rootDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        println(output)
        if (exitCode != 0) {
            throw GradleException(
                "semgrepScan: Semgrep reported finding(s) — see $reportFile and the output above"
            )
        }
        println("semgrepScan: clean — report at $reportFile")
    }
}

// OSV-Scanner: points at the aggregated CycloneDX SBOM (`cyclonedxBom`,
// org.cyclonedx.bom applied above), never at a directory scan — an
// unscoped `osv-scanner scan source -r .` also matches POMs cached under
// build/ by nested test harnesses (e.g. the Gradle plugin's functionalTest
// GradleTestKit cache), which describe *their* dependencies, not this
// project's resolved graph. Querying the OSV database needs network, so this
// is not in `check`: private CI runs it on the schedule and on a manual
// web trigger only. Degrades gracefully when the `osv-scanner` binary is absent.
tasks.register("osvScan") {
    description = "Scans the aggregated dependency SBOM against the OSV database (needs network)"
    group = "verification"
    dependsOn("cyclonedxBom")
    doLast {
        val osvScanner = findExecutableOnPath("osv-scanner")
        if (osvScanner == null) {
            println(
                "osvScan: 'osv-scanner' not found on PATH — skipped. " +
                    "Install: https://google.github.io/osv-scanner/installation/ (see documentation/security-tooling.md)."
            )
            return@doLast
        }
        val sbom = layout.buildDirectory.file("reports/cyclonedx/bom.json").get().asFile
        if (!sbom.exists()) {
            throw GradleException("osvScan: expected SBOM missing at $sbom — did cyclonedxBom run?")
        }
        val reportFile = layout.buildDirectory.file("reports/osv-scanner/results.json").get().asFile
        reportFile.parentFile.mkdirs()
        val process = ProcessBuilder(
            osvScanner, "scan", "source", "--format=json", "--output-file=${reportFile.absolutePath}", sbom.absolutePath
        ).directory(rootDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        println(output)
        // osv-scanner exit codes: 0 = clean, 1 = vulnerabilities found, >1 = scan error.
        if (exitCode > 1) {
            throw GradleException("osvScan: scan itself failed (exit $exitCode) — see the output above")
        }
        if (exitCode == 1) {
            throw GradleException(
                "osvScan: known vulnerabilities found in the dependency graph — see $reportFile and the output above"
            )
        }
        println("osvScan: clean — report at $reportFile")
    }
}

tasks.register("dependencyReport") {
    description = "Generates module dependency tree for all subprojects"
    group = "verification"
    val outputFile = layout.buildDirectory.file("reports/dependency-graph/module-dependencies.txt")
    outputs.file(outputFile)
    doLast {
        val sb = StringBuilder()
        sb.appendLine("# Module Dependency Report")
        sb.appendLine("# Generated: ${java.time.Instant.now()}")
        sb.appendLine()
        subprojects.sortedBy { it.name }.forEach { sub ->
            sb.appendLine("## ${sub.name}")
            val compileClasspath = sub.configurations.findByName("compileClasspath")
            if (compileClasspath != null) {
                val projectDeps = compileClasspath.allDependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    // `dependencyProject` is deprecated for removal in Gradle 9; `path` is the
                    // supported accessor and the name is its last segment.
                    .map { it.path.substringAfterLast(':') }
                    .sorted()
                val externalDeps = compileClasspath.allDependencies
                    .filter { it !is org.gradle.api.artifacts.ProjectDependency && it.group != null }
                    .map { "${it.group}:${it.name}:${it.version ?: ""}" }
                    .sorted()
                if (projectDeps.isEmpty() && externalDeps.isEmpty()) {
                    sb.appendLine("  (no dependencies)")
                } else {
                    projectDeps.forEach { sb.appendLine("  -> $it") }
                    externalDeps.forEach { sb.appendLine("  -> $it") }
                }
            } else {
                sb.appendLine("  (no compileClasspath)")
            }
            sb.appendLine()
        }
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(sb.toString())
        println(sb.toString())
    }
}

// Modules JDepend cannot measure. The first four have no `main` source set worth
// analysing at all: `narrativetrace-security-tests` and `narrativetrace-build-tests`
// are test-only (no `build/classes/java/main` exists), and a module with no
// production package has no afferent/efferent coupling to gate. They are excluded
// from `jdependCrossModule` for the same reason — including them would add an empty
// input, not a measurement.
val jdependExcludedModules = setOf(
    "narrativetrace-benchmarks",
    "narrativetrace-build-tests",
    "narrativetrace-security-tests",
    "narrativetrace-jcstress",
    "narrativetrace-micronaut",
    "narrativetrace-micronaut-http"
)

val jdependCrossModuleExcludedModules = jdependExcludedModules + setOf(
    "narrativetrace-junit4-example",
    "narrativetrace-gradle-plugin"
)

// Per-module JDepend measures nothing on narrativetrace-api. Every one of its packages is a
// contract: all-abstract, and with zero afferent coupling *inside* the module, because nothing in
// the API depends on anything else in it — that is the point of the module. Distance is then 1.0
// by construction rather than by design, for `annotation`, `spi`, `render` and `export` alike.
// It stays in the CROSS-module analysis, where its afferent coupling from core, proxy and the
// integrations is real and the D <= 0.8 threshold means something.
val jdependPerModuleExcludedModules = jdependExcludedModules + setOf("narrativetrace-api")

tasks.register("jdependReport") {
    description = "Per-module JDepend package metrics (isolated analysis)"
    group = "verification"
    val jdependModules = subprojects.filter {
        !it.path.startsWith(":narrativetrace-examples") &&
            it.name !in jdependPerModuleExcludedModules
    }
    dependsOn(jdependModules.map { ":${it.name}:jdepend" })
    doLast {
        val results = jdependModules.mapNotNull { sub ->
            val jsonFile = sub.file("build/reports/jdepend/jdepend.json")
            if (jsonFile.exists()) ai.narrativetrace.build.JDependReportSupport.readJson(jsonFile) else null
        }
        ai.narrativetrace.build.JDependReportSupport.printReport(results)
    }
}

tasks.register("jdependCrossModule") {
    description = "Cross-module JDepend analysis with accurate inter-module coupling"
    group = "verification"
    val crossModules = subprojects.filter {
        !it.path.startsWith(":narrativetrace-examples") &&
            it.name !in jdependCrossModuleExcludedModules
    }
    dependsOn(crossModules.map { ":${it.name}:classes" })
    doLast {
        val inputs = crossModules.map { sub ->
            ai.narrativetrace.build.JDependInput(sub.name, sub.file("build/classes/java/main"))
        }
        val result = ai.narrativetrace.build.JDependReportSupport.analyzeCrossModule(inputs)
        val jsonFile = file("build/reports/jdepend/jdepend-cross-module.json")
        ai.narrativetrace.build.JDependReportSupport.writeJson(result, jsonFile)
        ai.narrativetrace.build.JDependReportSupport.printReport(listOf(result))
        ai.narrativetrace.build.JDependReportSupport.enforceThresholds(
            listOf(result),
            maxDistance = 0.8,
            failOnCycles = true,
            minPackagesForEnforcement = 2
        )
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "pmd")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "com.diffplug.spotless")

    // License headers are stamped onto released sources at packaging time
    // (licensing.properties is the category map), so the working tree stays
    // free of the boilerplate.

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.25.2")
            targetExclude("build/**")
        }
        if (
            project.path.startsWith(":narrativetrace-examples") ||
            project.name in setOf("narrativetrace-micronaut", "narrativetrace-micronaut-http")
        ) {
            kotlin {
                ktlint("1.5.0")
                // target, not targetExclude: `targetExclude` filters a file tree that
                // Spotless has already walked, and the walk reaches into `build/`, where
                // the Kotlin compiler is concurrently creating and deleting its
                // incremental-state files. That race fails the task outright with
                // "Could not read path .../compileTestKotlin/cacheable/dirty-sources.txt".
                // Naming the sources up front means `build/` is never visited at all.
                target("src/**/*.kt")
            }
        }
    }

    group = "ai.narrativetrace"
    // Every module, the Gradle plugin included, ships the same version — see
    // gradle.properties for why the plugin may not drift from the libraries.
    version = providers.gradleProperty("narrativetraceVersion").get()

    repositories {
        mavenCentral()
    }

    // Toolchain pin: compile and test on JDK 17 regardless of the host JVM, so the
    // gate is reproducible across machines and the Java/Kotlin targets never diverge.
    // -Pnarrativetrace.toolchain=21 overrides the pin for the scheduled CI job that
    // exercises the JDK-21-gated test bodies (virtual threads) the 17 pin skips.
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion =
                JavaLanguageVersion.of((findProperty("narrativetrace.toolchain") as String? ?: "17").toInt())
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    // Micronaut 4.10.26 (the newest patch on the 4.x line) still
    // resolves the Netty 4.2 family to 4.2.16.Final, which carries GHSA-8c42-7qj2-3j46
    // (netty-codec-http CORS cache-poisoning/info-disclosure). Netty releases its 4.2.x
    // artifacts in lockstep, so forcing the whole family to the next patch is safe within
    // that guarantee — found by osvScan (documentation/security-tooling.md).
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty" && requested.version?.startsWith("4.2.") == true) {
                useVersion("4.2.17.Final")
                because("GHSA-8c42-7qj2-3j46 — Micronaut 4.10.26 still resolves 4.2.16.Final")
            }
        }
    }

    // Declared, not auto-loaded. Gradle 9 removes the automatic loading of test framework
    // implementation dependencies; without this every `check` says so once and every module
    // would have to repeat the line in its own build file.
    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        System.getProperty("narrativetrace.output")?.let { systemProperty("narrativetrace.output", it) }
        System.getProperty("narrativetrace.outputDir")?.let { systemProperty("narrativetrace.outputDir", it) }
        System.getProperty("narrativetrace.format")?.let { systemProperty("narrativetrace.format", it) }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("perf")
        }
    }

    val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
    tasks.register<Test>("perfTest") {
        description = "Runs performance tests (tagged @Tag(\"perf\"))"
        group = "verification"
        useJUnitPlatform {
            includeTags("perf")
        }
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        maxHeapSize = "256m"
    }

    tasks.register("printCompilerArgs") {
        doLast {
            tasks.withType<JavaCompile>().forEach {
                println("COMPILER_ARGS: ${it.options.compilerArgs}")
            }
        }
    }

    configure<PmdExtension> {
        toolVersion = "7.8.0"
        isConsoleOutput = true
        isIgnoreFailures = false
        ruleSets = emptyList()
        ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
    }

    // FindSecBugs rides on the SpotBugs Gradle plugin, offline-capable like
    // PMD: it only needs the one-time dependency resolution every other
    // declared dependency already pays for, never a network call at scan
    // time. Wired into `check` automatically by the plugin (spotbugsMain,
    // spotbugsTest), same as PMD's pmdMain/pmdTest. `spotbugsTest` is
    // disabled rather than removed from `check` — the fuzz/hostile-input
    // test fixtures deliberately do things (deserializing untrusted bytes,
    // building SQL from raw strings) that are the point of the test, not a
    // production finding, and a security linter cannot tell the two apart.
    // toolVersion left unpinned: the spotbugs-gradle-plugin already pins a
    // compatible SpotBugs core release, and pinning both independently is a
    // second version to keep in sync for no benefit here.
    dependencies {
        "spotbugsPlugins"("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
    }
    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        ignoreFailures.set(false)
        showStackTraces.set(false)
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    }
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
        reports.create("xml") { required.set(true) }
    }
    tasks.named("spotbugsTest") {
        enabled = false
    }

    val java = the<JavaPluginExtension>()
    tasks.register<Pmd>("pmdMetrics") {
        description = "Reports NCSS (non-commenting source statements) per method and class"
        group = "verification"
        source = java.sourceSets["main"].allJava
        classpath = java.sourceSets["main"].compileClasspath
        ruleSets = emptyList()
        ruleSetFiles = rootProject.files("config/pmd/metrics.xml")
        isConsoleOutput = false
        setIgnoreFailures(true)
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    if (name !in setOf("narrativetrace-benchmarks", "narrativetrace-build-tests", "narrativetrace-security-tests", "narrativetrace-gradle-plugin", "narrativetrace-jcstress")) {
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            dependsOn(tasks.named("test"))
            val threshold = if (project.name == "narrativetrace-agent") "0.97" else "0.98"
            violationRules {
                rule {
                    enabled = true
                    element = "BUNDLE"
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = threshold.toBigDecimal()
                    }
                }
            }
            if (project.path.startsWith(":narrativetrace-examples")) {
                classDirectories.setFrom(classDirectories.files.map { dir ->
                    fileTree(dir) {
                        exclude(
                            "**/*Example.class",
                            "**/*ExampleKt.class",
                            "**/PlantUmlImageRenderer.class"
                        )
                    }
                })
            }
        }

        tasks.named("check") {
            dependsOn(tasks.named("jacocoTestCoverageVerification"))
            dependsOn(":translationCheck")
            dependsOn(":demoWiringCheck")
            dependsOn(":licensingCheck")
            dependsOn(":baselineFreshnessCheck")
        }
    }

    if (name in setOf("narrativetrace-api", "narrativetrace-core", "narrativetrace-proxy", "narrativetrace-clarity", "narrativetrace-glossary")) {
        apply(plugin = "info.solidsoft.pitest")
        configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
            pitestVersion = "1.17.4"
            junit5PluginVersion = "1.2.1"
            threads = 4
            outputFormats = setOf("HTML", "XML")
            timestampedReports = false
            timeoutConstInMillis = 8000
            mutators = setOf("DEFAULTS")
            mutationThreshold = 80
            excludedTestClasses = setOf("*PerfTest")
        }
    }

    // narrativetrace-agent joins mutation testing (owner ruling, 2026-09-03) through its own
    // `pitestAgent` task, deliberately not folded into the shared `pitest` aggregate above: ASM
    // bytecode rewriting plus a double shadow-jar build (see the module's own build script) make
    // its baseline test run heavier than the other five pitest modules, and a blowout in an
    // instrumentation-visitor mutant must not consume the time box they share. Its own,
    // larger `timeoutConstInMillis` is that time box. It runs only where mutation already runs —
    // never per-commit, see `pitestAgent` at the bottom of this file and the GitLab `mutation-agent`
    // job, both isolated from the five-module `pitest`/`mutation` pair the same way.
    if (name == "narrativetrace-agent") {
        apply(plugin = "info.solidsoft.pitest")
        configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
            pitestVersion = "1.17.4"
            junit5PluginVersion = "1.2.1"
            threads = 4
            outputFormats = setOf("HTML", "XML")
            timestampedReports = false
            timeoutConstInMillis = 20000
            mutators = setOf("DEFAULTS")
            mutationThreshold = 80
            excludedTestClasses = setOf("*PerfTest")
        }
    }

    if (!path.startsWith(":narrativetrace-examples") && name !in jdependPerModuleExcludedModules) {
        tasks.register("jdepend") {
            description = "Runs JDepend analysis and writes JSON report"
            group = "verification"
            dependsOn(tasks.named("classes"))
            val classesDir = file("build/classes/java/main")
            val jsonFile = file("build/reports/jdepend/jdepend.json")
            // Captured at configuration time: reaching for `project` inside `doLast` is deprecated
            // and fails outright under the configuration cache.
            val moduleName = name
            outputs.file(jsonFile)
            doLast {
                val input = ai.narrativetrace.build.JDependInput(moduleName, classesDir)
                val result = ai.narrativetrace.build.JDependReportSupport.analyze(listOf(input)).first()
                ai.narrativetrace.build.JDependReportSupport.writeJson(result, jsonFile)
            }
        }

        tasks.register("jdependCheck") {
            description = "Enforces JDepend thresholds (Distance <= 0.7, no cycles)"
            group = "verification"
            dependsOn(tasks.named("jdepend"))
            val jsonFile = file("build/reports/jdepend/jdepend.json")
            doLast {
                if (jsonFile.exists()) {
                    val result = ai.narrativetrace.build.JDependReportSupport.readJson(jsonFile)
                    ai.narrativetrace.build.JDependReportSupport.enforceThresholds(
                        listOf(result),
                        maxDistance = 0.7,
                        failOnCycles = true,
                        minPackagesForEnforcement = 2
                    )
                }
            }
        }

        tasks.named("check") {
            dependsOn(tasks.named("jdependCheck"))
            dependsOn(rootProject.tasks.named("jdependCrossModule"))
        }
    }
}
