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
        "narrativetrace-security-tests",
        "sixty-seconds"
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

// Rule 8 (docs as tests), layer 1: a quickstart's code and output are embedded from a project the
// build compiles, tests and runs — never typed into the page. `sixty-seconds` IS
// documentation/sixty-seconds.md; its one test writes the byte-stable artifact the page's
// output block embeds. Depending on that test (rather than only reading whatever happens to be on
// disk) is what makes `snippetCheck` a docs-as-tests gate instead of a docs-as-whatever-was-left-
// in-build check.
// Docs-vs-published-gate design note, part (a): the git-ignored cache PublishedVersionSupport
// reads/writes, shared by snippetCheck (read-only) and snippetSync (the only writer).
val publishedVersionCacheFile =
    layout.buildDirectory.file("narrativetrace-publish-cache/published-version.txt")

tasks.register("snippetCheck") {
    description = "Verifies embedded doc code/output blocks match their source files (docs as tests, rule 8)"
    group = "verification"
    dependsOn(":sixty-seconds:test")
    val repoVersion = providers.gradleProperty("narrativetraceVersion")
    val cacheFileProvider = publishedVersionCacheFile
    doLast {
        val problems = ai.narrativetrace.build.SnippetSupport.check(rootDir).toMutableList()
        // The published-version half is read-only here — never a fetch — so this task never
        // fails merely because the current run is offline (thin-CI convention, docs-vs-
        // published-gate design note 1.1 item 2): the repo-version half of the committed banner
        // is always checked against gradle.properties; the published-version half only when a
        // fresh (<1h) cache is already on disk. With no fresh cache, any of the three well-formed
        // banner shapes is accepted.
        val cache = ai.narrativetrace.build.PublishedVersionSupport.readCache(cacheFileProvider.get().asFile)
        val llmsTxt = rootDir.resolve("documentation/llms.txt")
        val actual = ai.narrativetrace.build.LlmsTxtBannerSupport.currentLine(llmsTxt)
        problems += ai.narrativetrace.build.PublishedVersionSupport.bannerProblems(actual, repoVersion.get(), cache)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Snippet check failed — an embedded doc block drifted from its source; " +
                    "run snippetSync (rule 8, docs as tests):\n" +
                    problems.joinToString("\n")
            )
        }
        val pages = ai.narrativetrace.build.SnippetSupport.englishMarkdownFiles(rootDir).size
        println("snippetCheck: $pages page(s) match their embedded sources")
    }
}

// English pages only — translated mirrors never carry markers (see SnippetSupport); a translator
// restamps a mirror's own header after running this on the English source, same as translationCheck
// already expects for every other kind of code-block drift.
tasks.register("snippetSync") {
    description = "Rewrites embedded doc code/output blocks to match their source files (English pages only)"
    group = "verification"
    dependsOn(":sixty-seconds:test")
    val repoVersion = providers.gradleProperty("narrativetraceVersion")
    val cacheFileProvider = publishedVersionCacheFile
    doLast {
        val changed = ai.narrativetrace.build.SnippetSupport.sync(rootDir)
        if (changed.isEmpty()) {
            println("snippetSync: already in sync")
        } else {
            changed.forEach { println(it) }
        }
        // Best-effort network refresh — the only place this build makes that call. A registry
        // hiccup falls back to whatever was already cached (or null); never thrown, never a
        // build failure on its own.
        val cache = ai.narrativetrace.build.PublishedVersionSupport.refreshCache(cacheFileProvider.get().asFile)
        val line = ai.narrativetrace.build.PublishedVersionSupport.llmsTxtLine(repoVersion.get(), cache)
        val llmsTxt = rootDir.resolve("documentation/llms.txt")
        val before = ai.narrativetrace.build.LlmsTxtBannerSupport.currentLine(llmsTxt)
        ai.narrativetrace.build.LlmsTxtBannerSupport.writeLine(llmsTxt, line)
        if (before != line) {
            println("snippetSync: documentation/llms.txt banner -> ${ai.narrativetrace.build.PublishedVersionSupport.stripCacheAgeComment(line)}")
        }
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

// ------------------------------------------------------------------------------------------------
// Mutation testing (PIT) module selection — default-deny.
//
// Every subproject is classified in EXACTLY ONE of the three collections below;
// `mutationAccounting` (registered further down, wired into `check`) enforces that as a
// build-time check. Before this, the plugin-application `if` and this file's own `pitest`
// aggregate `dependsOn` were two independently hand-synced module-name lists with no accounting
// between them — a new module could ship unmutated forever and nothing would say so. Now there
// is one list per tier and a map of reasons for everything else; a module that falls through all
// three fails the build, naming itself.
// ------------------------------------------------------------------------------------------------

/**
 * The shared pitest tier: threshold 80 (or a measured, ratcheted floor — see
 * `mutationThresholdFloors`), DEFAULTS mutators, 8s per-mutant timeout. `narrativetrace-slf4j`,
 * `narrativetrace-diagrams`, `narrativetrace-junit5`, and `narrativetrace-opentelemetry` joined
 * the original five 2026-09-10 (owner order: "slf4j should be mutated, add a few more, we will
 * increment gradually").
 */
val mutationTestedModules = setOf(
    "narrativetrace-api",
    "narrativetrace-core",
    "narrativetrace-proxy",
    "narrativetrace-clarity",
    "narrativetrace-glossary",
    "narrativetrace-slf4j",
    "narrativetrace-diagrams",
    "narrativetrace-junit5",
    "narrativetrace-opentelemetry",
)

/**
 * Per-module mutation-score floors below the shared 80% threshold, set only where a measured
 * score genuinely falls short of it — never used to weaken the original five. Round DOWN to the
 * whole percent measured; raise with tests, never lower.
 */
val mutationThresholdFloors: Map<String, Int> = mapOf(
    "narrativetrace-diagrams" to 78, // measured floor 2026-09-10 — ratchet: raise with tests, never lower
)

/** The agent module's own isolated tier (owner ruling, 2026-09-03) — see the subproject block below. */
val mutationAgentModules = setOf("narrativetrace-agent")

/**
 * The JUnit Platform release this build's test suites already resolve to (every
 * mutationTestedModules/mutationAgentModules module declares `org.junit.jupiter:junit-jupiter:5.11.4`,
 * which aligns transitively to `org.junit.platform:junit-platform-launcher:1.11.4`). PIT's `pitest`
 * configuration resolves independently of `testRuntimeClasspath`: `pitest-junit5-plugin` pulls its
 * own transitive `junit-platform-launcher` (1.9.2, via its own older `junit-bom` import), a version
 * nothing else in the build ever requests, so it is never in the offline cache the rest of the build
 * populates — the nightly `--offline` job failed `:narrativetrace-api:pitest` and
 * `:narrativetrace-agent:pitest` on exactly this resolution for two consecutive nights (2026-09-12).
 * Importing this same BOM into the `pitest` configuration below raises its launcher constraint to
 * the version the build already has, so online and `--offline` resolve identically.
 */
val pitestJunitBom = "org.junit:junit-bom:5.11.4"

/**
 * Every other subproject, with the one-line reason it is not mutation-tested (yet, or ever).
 * Keyed by leaf module name, except the whole `narrativetrace-examples` tree (the parent plus its
 * six `narrativetrace-examples:*` children), which `mutationAccounting` folds into the single
 * "narrativetrace-examples" entry below by path prefix.
 *
 * `narrativetrace-maven-example` is not a Gradle subproject at all — a standalone Maven project
 * under narrativetrace-maven-example/, proven the same way as the Gradle example modules below:
 * by being executed, not by mutants — so it never reaches this map or `mutationAccounting`.
 */
val mutationExemptModules: Map<String, String> = mapOf(
    "narrativetrace-examples" to
        "demo/consumer code (this module and its narrativetrace-examples:* children) — proven by " +
            "being executed (acceptance/dockerTest), not by mutants",
    "narrativetrace-agent-example" to
        "demo/consumer code for narrativetrace-agent — proven by being executed, not by mutants",
    "narrativetrace-junit4-example" to
        "demo/consumer code for narrativetrace-junit4 — proven by being executed, not by mutants",
    "sixty-seconds" to
        "the 60-second tutorial embedded into documentation/sixty-seconds.md — proven by " +
            "being executed (its own test, snippetCheck), not by mutants",
    "narrativetrace-benchmarks" to
        "JMH harness — source lives in src/jmh, not src/main; no assertable invariants to mutate",
    "narrativetrace-jcstress" to
        "concurrency stress harness — probabilistic tests, mutants meaningless",
    "narrativetrace-build-tests" to
        "GradleTestKit functional suite for the root build itself (license packaging, build " +
            "configuration, the publication script) — is itself a test suite; mutating tests tests nothing",
    "narrativetrace-security-tests" to
        "corpus-replay test suite (src/test only, zero src/main) — is itself a test suite; " +
            "mutating tests tests nothing",
    "narrativetrace-gradle-plugin" to
        "build-time plugin, proven by its own src/functionalTest GradleTestKit executions; " +
            "DEFERRED — candidate for a later increment",
    "narrativetrace-junit4" to
        "thin JUnit 4 adapter, small surface; DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-servlet" to
        "thin servlet-filter adapter, small surface; DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-spring" to
        "thin Spring adapter, small surface; DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-spring-web" to
        "thin Spring Web adapter, small surface; DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-micrometer" to
        "thin Micrometer context-accessor adapter, small surface; DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-micronaut" to
        "Kotlin, not Java (src/main is 100% .kt) — thin adapter over the Micronaut DI container; " +
            "DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
    "narrativetrace-micronaut-http" to
        "Kotlin, not Java (src/main is 100% .kt) — thin HTTP filter/factory adapter over Micronaut; " +
            "DEFERRED — owner: \"we will increment gradually\" (2026-09-10)",
)

/**
 * Every subproject appears in exactly one of `mutationTestedModules`, `mutationAgentModules`, or
 * `mutationExemptModules` — never zero (a silently-unmutated new module), never more than one
 * (a stale entry nobody reconciled). The whole `narrativetrace-examples` tree collapses to its one
 * `mutationExemptModules` entry by path prefix; everything else is keyed by leaf name.
 */
tasks.register("mutationAccounting") {
    description = "Fails if any subproject is unclassified or double-classified for mutation testing"
    group = "verification"
    doLast {
        val problems = mutableListOf<String>()
        subprojects.forEach { sub ->
            val key = moduleKey(sub)
            val exemptKey = if (key == "narrativetrace-examples" || key.startsWith("narrativetrace-examples:")) {
                "narrativetrace-examples"
            } else {
                key
            }
            val memberships = listOfNotNull(
                "mutationTestedModules".takeIf { sub.name in mutationTestedModules },
                "mutationAgentModules".takeIf { sub.name in mutationAgentModules },
                "mutationExemptModules".takeIf { exemptKey in mutationExemptModules },
            )
            when {
                memberships.isEmpty() -> problems.add(
                    "$key: not classified for mutation testing — add it to mutationTestedModules, " +
                        "mutationAgentModules, or mutationExemptModules (with a reason) in build.gradle.kts"
                )
                memberships.size > 1 -> problems.add(
                    "$key: classified in more than one collection (${memberships.joinToString(", ")}) — " +
                        "remove it from all but one in build.gradle.kts"
                )
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Mutation testing module accounting failed:\n" + problems.joinToString("\n")
            )
        }
        println(
            "mutationAccounting: ${subprojects.size} modules accounted for — " +
                "${mutationTestedModules.size} mutation-tested, ${mutationAgentModules.size} agent-tier, " +
                "${mutationExemptModules.size} exempt reasons"
        )
    }
}

tasks.register("pitest") {
    description = "Runs mutation testing (PIT) across the mutationTestedModules set (build.gradle.kts)"
    group = "verification"
    dependsOn(mutationTestedModules.map { ":$it:pitest" })
    doLast {
        val entries = ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects, mutationTestedModules)
        ai.narrativetrace.build.MutationReportSupport.printReport(entries)
    }
}

// Isolated from the "pitest" aggregate above on purpose — see the pitest-extension comment on
// the narrativetrace-agent subproject block for why this module gets its own task and its own
// GitLab job (`mutation-agent`) rather than a spot in `mutationTestedModules` above.
tasks.register("pitestAgent") {
    description = "Runs mutation testing (PIT) on the agent module, isolated in its own time box"
    group = "verification"
    dependsOn(mutationAgentModules.map { ":$it:pitest" })
    doLast {
        val entries = ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects, mutationAgentModules)
        ai.narrativetrace.build.MutationReportSupport.printReport(entries)
    }
}

tasks.register("mutationReport") {
    description = "Shows mutation testing results from latest pitest run"
    group = "verification"
    doLast {
        val entries = ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects, mutationTestedModules)
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

// ------------------------------------------------------------------------------------------------
// Duplication detection (PMD CPD) — a report every commit, a ratchet against a committed baseline,
// never a fixed percentage. See documentation/duplication.md for the floor/ratchet/exemption rules
// this pair of tasks enforces; that doc is the one to update if either changes.
// ------------------------------------------------------------------------------------------------

/**
 * Token floor (owner ruling 2026-09-12): below this, CPD's matches are noise — a handful of tokens
 * two unrelated methods share by coincidence — rather than a genuine structural copy. Identifiers
 * and literals are always ignored (see `DuplicationReportSupport.runCpd`), so what clears this floor
 * is shape, not text.
 */
val duplicationMinTokens = 60

/**
 * Every subproject's conventional Java source directories, read straight off disk rather than
 * through each subproject's `SourceSetContainer` — this task is registered before the `subprojects
 * {}` block below applies `java-library`, so the extension those directories would otherwise come
 * from does not exist yet at this point in the script. No subproject in this build customises its
 * source directories (grep for `srcDirs`/`sourceSets {}` finds none), so the convention is exact.
 */
fun duplicationSourceDirs(sourceSet: String): List<File> =
    subprojects.map { it.projectDir.resolve("src/$sourceSet/java") }

tasks.register("duplicationReport") {
    description = "Runs PMD CPD over every module's main and test sources; writes XML, duplication.json, and a summary line"
    group = "verification"
    val reportsDir = layout.buildDirectory.dir("reports/duplication")
    val mainDirs = duplicationSourceDirs("main")
    val testDirs = duplicationSourceDirs("test")
    inputs.files(mainDirs.filter { it.isDirectory })
    inputs.files(testDirs.filter { it.isDirectory })
    val jsonFile = reportsDir.map { it.file("duplication.json") }
    outputs.file(jsonFile)
    outputs.file(reportsDir.map { it.file("main.xml") })
    outputs.file(reportsDir.map { it.file("test.xml") })
    doLast {
        val dir = reportsDir.get().asFile
        dir.mkdirs()
        val main = ai.narrativetrace.build.DuplicationReportSupport.runCpd(
            mainDirs, duplicationMinTokens, rootDir, dir.resolve("main.xml")
        )
        val test = ai.narrativetrace.build.DuplicationReportSupport.runCpd(
            testDirs, duplicationMinTokens, rootDir, dir.resolve("test.xml")
        )
        val scan = ai.narrativetrace.build.DuplicationScanResult(duplicationMinTokens, main, test)
        ai.narrativetrace.build.DuplicationReportSupport.writeJson(scan, jsonFile.get().asFile)
        println(ai.narrativetrace.build.DuplicationReportSupport.summaryLine(scan))
    }
}

tasks.register("duplicationCheck") {
    description = "Ratchets main-tree duplication against config/duplication/baseline.properties (test tree is reported only, never gates)"
    group = "verification"
    dependsOn(":duplicationReport")
    doLast {
        val jsonFile = layout.buildDirectory.file("reports/duplication/duplication.json").get().asFile
        val scan = ai.narrativetrace.build.DuplicationReportSupport.readJson(jsonFile)
        val baseline = ai.narrativetrace.build.DuplicationCheckSupport.readBaseline(
            rootProject.file("config/duplication/baseline.properties")
        )
        val exemptions = ai.narrativetrace.build.DuplicationCheckSupport.readExemptions(
            rootProject.file("config/duplication/exemptions.txt")
        )
        val result = ai.narrativetrace.build.DuplicationCheckSupport.decide(scan.main, baseline, exemptions)
        println(result.message)
        if (!result.passed) {
            throw GradleException(result.message)
        }
    }
}

// gitleaks: a named entry point so CI encodes only this task's name, never the
// binary's path, flags or version (THIN-CI rule). The local pre-commit hook
// covers the staged diff on every commit; this covers the whole git history —
// for a periodic sweep, and for a machine that never ran the hook.
// Deliberately not wired into `check` or a CI job here — see
// documentation/security-tooling.md for the cadence this repo runs it at.
fun findExecutableOnPath(name: String): String? {
    val path = System.getenv("PATH") ?: return null
    return path.split(File.pathSeparatorChar)
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }
        ?.absolutePath
}

// A missing scanner binary is never a silent green (the exact class
// that bit the .NET first release; release-retrospective rule 2: a graceful-skip tool must prove
// it has ever run). Locally it WARNS and records `skipped` under build/reports/security-scans/;
// in CI, or under -Pnarrativetrace.security.required=true, it fails the task. A scan that ran
// clean records `ran-clean`, so ran-clean / skipped / never-ran stay distinguishable after the
// fact. The decision logic lives in ScannerGateSupport (buildSrc), unit-tested there.
val securityScannersRequired =
    providers.gradleProperty("narrativetrace.security.required").orNull == "true" ||
        !System.getenv("CI").isNullOrEmpty()
val securityScanStatusDir = layout.buildDirectory.dir("reports/security-scans")

tasks.register("gitleaksScan") {
    description = "Scans the full git history for secrets with gitleaks (missing binary: WARN + skipped status locally, failure in CI/required mode)"
    group = "verification"
    doLast {
        val statusDir = securityScanStatusDir.get().asFile
        val gitleaks = findExecutableOnPath("gitleaks")
        if (gitleaks == null) {
            val decision = ai.narrativetrace.build.ScannerGateSupport.onMissingBinary(
                "gitleaks",
                securityScannersRequired,
                "https://github.com/gitleaks/gitleaks#installing (see documentation/security-tooling.md)"
            )
            ai.narrativetrace.build.ScannerGateSupport.recordSkipped(statusDir, "gitleaks", "binary not on PATH")
            if (decision.fail) {
                throw GradleException("gitleaksScan: ${decision.message}")
            }
            logger.warn("gitleaksScan: ${decision.message}")
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
        ai.narrativetrace.build.ScannerGateSupport.recordRanClean(statusDir, "gitleaks")
        println("gitleaksScan: clean — full git history scanned, report at $reportFile")
    }
}

// Semgrep: a named entry point over the community `p/java` registry ruleset
// (OSS/community-maintained; no custom rules — see documentation/security-tooling.md
// for why custom rules are out of scope here). Fetching the ruleset needs
// network, which is why this is not in `check` or a per-push job: wired into
// private CI on merge-request and scheduled pipelines only. A missing binary
// follows the scanner gate above: WARN + skipped status locally, failure in
// CI/required mode.
tasks.register("semgrepScan") {
    description = "Runs Semgrep's community p/java security ruleset over the source tree (needs network)"
    group = "verification"
    doLast {
        val statusDir = securityScanStatusDir.get().asFile
        val semgrep = findExecutableOnPath("semgrep")
        if (semgrep == null) {
            val decision = ai.narrativetrace.build.ScannerGateSupport.onMissingBinary(
                "semgrep",
                securityScannersRequired,
                "https://semgrep.dev/docs/getting-started/ (see documentation/security-tooling.md)"
            )
            ai.narrativetrace.build.ScannerGateSupport.recordSkipped(statusDir, "semgrep", "binary not on PATH")
            if (decision.fail) {
                throw GradleException("semgrepScan: ${decision.message}")
            }
            logger.warn("semgrepScan: ${decision.message}")
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
        ai.narrativetrace.build.ScannerGateSupport.recordRanClean(statusDir, "semgrep")
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
// web trigger only. A missing binary follows the scanner gate above: WARN +
// skipped status locally, failure in CI/required mode.
tasks.register("osvScan") {
    description = "Scans the aggregated dependency SBOM against the OSV database (needs network)"
    group = "verification"
    dependsOn("cyclonedxBom")
    doLast {
        val statusDir = securityScanStatusDir.get().asFile
        val osvScanner = findExecutableOnPath("osv-scanner")
        if (osvScanner == null) {
            val decision = ai.narrativetrace.build.ScannerGateSupport.onMissingBinary(
                "osv-scanner",
                securityScannersRequired,
                "https://google.github.io/osv-scanner/installation/ (see documentation/security-tooling.md)"
            )
            ai.narrativetrace.build.ScannerGateSupport.recordSkipped(statusDir, "osv-scanner", "binary not on PATH")
            if (decision.fail) {
                throw GradleException("osvScan: ${decision.message}")
            }
            logger.warn("osvScan: ${decision.message}")
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
        ai.narrativetrace.build.ScannerGateSupport.recordRanClean(statusDir, "osv-scanner")
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
    "narrativetrace-gradle-plugin",
    "sixty-seconds"
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

    if (name !in setOf("narrativetrace-benchmarks", "narrativetrace-build-tests", "narrativetrace-security-tests", "narrativetrace-gradle-plugin", "narrativetrace-jcstress", "sixty-seconds")) {
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
            dependsOn(":mutationAccounting")
            dependsOn(":snippetCheck")
            dependsOn(":duplicationCheck")
        }
    }

    if (name in mutationTestedModules) {
        apply(plugin = "info.solidsoft.pitest")
        dependencies {
            "pitest"(platform(pitestJunitBom))
        }
        configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
            pitestVersion = "1.17.4"
            junit5PluginVersion = "1.2.1"
            threads = 4
            outputFormats = setOf("HTML", "XML")
            timestampedReports = false
            timeoutConstInMillis = 8000
            mutators = setOf("DEFAULTS")
            mutationThreshold = mutationThresholdFloors[name] ?: 80
            // *FailingTestFixture: narrativetrace-junit5's NarrativeTraceExtensionTest launches this
            // fixture programmatically (via the JUnit Platform Launcher API) to assert on the trace a
            // *failing* test still produces — it never runs as a top-level test under `./gradlew test`
            // (its name doesn't match Gradle's default test-class patterns), but PIT's own test-class
            // scan finds and runs it directly, and PIT requires a green suite before it can mutate.
            excludedTestClasses = setOf("*PerfTest", "*FailingTestFixture")
        }
    }

    // narrativetrace-agent joins mutation testing (owner ruling, 2026-09-03) through its own
    // `pitestAgent` task, deliberately not folded into the shared `pitest` aggregate above: ASM
    // bytecode rewriting plus a double shadow-jar build (see the module's own build script) make
    // its baseline test run heavier than the other mutationTestedModules, and a blowout in an
    // instrumentation-visitor mutant must not consume the time box they share. Its own,
    // larger `timeoutConstInMillis` is that time box. It runs only where mutation already runs —
    // never per-commit, see `pitestAgent` at the bottom of this file and the GitLab `mutation-agent`
    // job, both isolated from the `mutationTestedModules`/`pitest`/`mutation` tier the same way.
    if (name in mutationAgentModules) {
        apply(plugin = "info.solidsoft.pitest")
        dependencies {
            "pitest"(platform(pitestJunitBom))
        }
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

// ================================================================================================
// verifyAll — pro repo TODO §35E: "one command that runs everything and reports numbers."
//
// Today the only way to know what actually ran is to read five build systems and a log directory.
// This is the family's first entry point of this shape — its JSON is the schema the other four
// runtimes (.NET/`VerifyAll`, TS/`verify:all`, Python/`verify-all`, Swift/`verify-all.sh`) mirror —
// so its output is deliberately generic (field names, statuses, category ids) rather than
// Java-shaped. See `reports/verification/SCHEMA.md` for the field-by-field contract.
//
// It runs EVERY verification category this repo has, gate and heavy alike, in one sitting: cheap
// static checks first (so a formatting break is visible in seconds), then the test/coverage/
// architecture/conformance tier (all sliced from ONE `./gradlew test` run — see below), then the
// genuinely heavy tier (mutation, coverage-guided fuzzing, benchmarks, concurrency stress) last.
// A category's failure is recorded and the run CONTINUES — the report's value is completeness, not
// an early exit — and `verifyAll` itself only fails at the very end, once every category has had
// its turn, if any category's status is `failed`.
//
// Each category is its OWN fresh, non-daemon `./gradlew` subprocess (never a second in parallel):
// this is a large, memory-constrained monorepo build, and the family's own container has choked on
// concurrent heavy Gradle invocations before. Parsing logic
// lives in buildSrc (JUnitAggregateSupport, PropertyTestClassifier, SpotBugsViolationSupport,
// JcstressReportSupport, VerificationReportSupport) and is unit-tested there — this task only
// orchestrates the sequence and shapes the rows.
// ================================================================================================

/** One subprocess's outcome: whether it succeeded, everything it printed, and how long it took. */
data class GradleRunOutcome(val exitCode: Int, val output: String, val seconds: Double, val logFile: File)

/**
 * Runs a fresh `./gradlew` invocation with [args] and blocks until it exits. Always `--no-daemon`
 * (no daemon left running to accumulate across dozens of these) and `--continue` (one failing task
 * inside the invocation must never hide the others' results) and `--max-workers=2` (the container
 * this repo builds in ships 8 GiB with its swap already committed).
 *
 * The full captured output is always written to `build/verifyAll-logs/<category>.log` — a summary
 * line alone is not enough to diagnose a failure, and this is the one place that output would
 * otherwise be lost (Gradle's own console only shows the OUTER `verifyAll` process; each category
 * is a separate, nested `./gradlew` process whose stdout nothing else captures).
 */
fun runGradleSubprocess(rootDir: File, category: String, vararg args: String): GradleRunOutcome {
    val gradlewCmd = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
    val command = listOf(gradlewCmd, "--continue", "--no-daemon", "--max-workers=2") + args.toList()
    val start = System.nanoTime()
    val process = ProcessBuilder(command).directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    val logFile = File(rootDir, "build/verifyAll-logs/$category.log")
    logFile.parentFile.mkdirs()
    logFile.writeText(output)
    return GradleRunOutcome(exitCode, output, (System.nanoTime() - start) / 1_000_000_000.0, logFile)
}

/** The short commit `verifyAll` ran at — falls back to `"unknown"` rather than failing the run over it. */
fun shortCommitOf(rootDir: File): String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(rootDir).start()
    val out = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    out.ifBlank { "unknown" }
} catch (e: Exception) {
    "unknown"
}

/** Appends the saved log path to [note] whenever [status] is not a clean pass — a passed row stays as clean as [note] already was. */
fun withLogHint(note: String?, outcome: GradleRunOutcome, status: ai.narrativetrace.build.VerificationStatus): String? =
    if (status == ai.narrativetrace.build.VerificationStatus.PASSED) note
    else (note?.let { "$it; " } ?: "") + "full output: ${outcome.logFile.path}"

/** `<hostname>-<arch>` — enough to explain a timing anomaly without carrying anything sensitive. */
fun hostDescriptor(): String {
    val hostname = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME")
        ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
    return "$hostname-${System.getProperty("os.arch")}"
}

/** Counts occurrences of `pattern` in [text] — used for jazzer's own "Done N runs in" console lines. */
fun countOccurrences(text: String, pattern: Regex): Int = pattern.findAll(text).count()

tasks.register("verifyAll") {
    description = "Runs EVERY verification this repo has end to end — unit tests, coverage, " +
        "mutation, property tests, both fuzz tiers, jcstress, benchmarks/allocation, ArchUnit, " +
        "conformance, secrets/SAST/SCA scanners, format/lint/complexity and translation checks — " +
        "and writes reports/verification/<date>.json + .md. LONG-RUNNING BY DESIGN: mutation " +
        "testing across the mutationTestedModules set plus the agent module, and the unbounded jcstress " +
        "sweep, are each historically well over an hour on this project's own dev container. " +
        "Slowness is fine here; hidden state is not. A category's failure never aborts the run — " +
        "see reports/verification/SCHEMA.md for how to read the report it produces."
    group = "verification"

    doLast {
        println("=".repeat(100))
        println("verifyAll: running every verification category this repo has, gate and heavy alike.")
        println("This is LONG-RUNNING BY DESIGN (mutation + the unbounded stress sweep are each")
        println("historically over an hour). Each category's result is collected regardless of")
        println("whether an earlier category failed; verifyAll only fails at the very end.")
        println("=".repeat(100))

        val startedAt = java.time.Instant.now()
        val results = mutableListOf<ai.narrativetrace.build.CategoryResult>()

        fun addRow(row: ai.narrativetrace.build.CategoryResult) {
            results += row
            val elapsed = java.time.Duration.between(startedAt, java.time.Instant.now()).seconds
            println(
                "[$elapsed s elapsed] ${row.category.id.padEnd(14)} ${row.status.id.padEnd(15)} " +
                    "(${"%.1f".format(row.durationSeconds)}s)  ${row.note ?: ""}"
            )
        }

        // -------------------------------------------------------------------------------- format
        run {
            val outcome = runGradleSubprocess(rootDir, "format", "spotlessCheck")
            val status = if (outcome.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
            addRow(
                ai.narrativetrace.build.CategoryResult(
                    ai.narrativetrace.build.VerificationCategory.FORMAT,
                    "Spotless (google-java-format 1.25.2 + ktlint 1.5.0)",
                    status,
                    emptyMap(),
                    outcome.seconds,
                    withLogHint(
                        if (status == ai.narrativetrace.build.VerificationStatus.PASSED) null
                        else "spotlessCheck reported formatting violations; the plugin's console output carries no structured violation count to parse",
                        outcome, status,
                    ),
                )
            )
        }

        // ------------------------------------------------------------------- lint + complexity + sast (spotbugs half)
        val staticAnalysis = runGradleSubprocess(rootDir, "lint-complexity", "pmdMain", "pmdTest", "spotbugsMain", "metricsReport")
        val pmdViolations = ai.narrativetrace.build.PmdViolationSupport.collectViolationsFromProjects(subprojects)
        val spotbugsViolations = ai.narrativetrace.build.SpotBugsViolationSupport.collectFromProjects(subprojects)
        val ncssViolations = pmdViolations.filter { it.rule == "NcssCount" }
        val styleViolations = pmdViolations.filter { it.rule != "NcssCount" }
        val spotbugsStyle = ai.narrativetrace.build.SpotBugsViolationSupport.styleFindings(spotbugsViolations)
        val spotbugsSecurity = ai.narrativetrace.build.SpotBugsViolationSupport.securityFindings(spotbugsViolations)

        val lintStatus = if (styleViolations.isEmpty() && spotbugsStyle.isEmpty()) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.LINT,
                "PMD 7.8.0 (bestpractices+errorprone) + SpotBugs 6.5.11 (non-security categories)",
                lintStatus,
                mapOf("findings" to (styleViolations.size + spotbugsStyle.size)),
                staticAnalysis.seconds,
                withLogHint(null, staticAnalysis, lintStatus),
            )
        )
        val complexityStatus = if (ncssViolations.isEmpty()) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.COMPLEXITY,
                "PMD NcssCount (config/pmd/ruleset.xml, hard gate ≤20 statements/method)",
                complexityStatus,
                mapOf("findings" to ncssViolations.size),
                0.0,
                withLogHint("derived from the same pmdMain/pmdTest run as the lint row above (0s: no separate invocation)", staticAnalysis, complexityStatus),
            )
        )

        // ------------------------------------------------------------------------------------ sast
        val sast = runGradleSubprocess(rootDir, "sast", "spotbugsMain", "semgrepScan")
        val semgrepStatus = ai.narrativetrace.build.ScannerGateSupport.status(
            layout.buildDirectory.dir("reports/security-scans").get().asFile, "semgrep"
        )
        val sastStatus = if (spotbugsSecurity.isNotEmpty()) ai.narrativetrace.build.VerificationStatus.FAILED
            else if (semgrepStatus.startsWith("skipped")) ai.narrativetrace.build.VerificationStatus.PASSED
            else if (sast.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED
            else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.SAST,
                "FindSecBugs 1.14.0 (SpotBugs SECURITY category) + Semgrep p/java",
                sastStatus,
                mapOf("findings" to spotbugsSecurity.size),
                sast.seconds,
                withLogHint("FindSecBugs ran as the gate tool; Semgrep (network, community p/java ruleset) status: $semgrepStatus", sast, sastStatus),
            )
        )

        // --------------------------------------------------------------------------------- secrets
        val secrets = runGradleSubprocess(rootDir, "secrets", "gitleaksScan")
        val gitleaksStatus = ai.narrativetrace.build.ScannerGateSupport.status(
            layout.buildDirectory.dir("reports/security-scans").get().asFile, "gitleaks"
        )
        val secretsStatus = when {
            gitleaksStatus.startsWith("skipped") -> ai.narrativetrace.build.VerificationStatus.SKIPPED
            secrets.exitCode == 0 -> ai.narrativetrace.build.VerificationStatus.PASSED
            else -> ai.narrativetrace.build.VerificationStatus.FAILED
        }
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.SECRETS,
                "gitleaks (full git history)",
                secretsStatus,
                emptyMap(),
                secrets.seconds,
                withLogHint("gitleaksScan status: $gitleaksStatus", secrets, secretsStatus),
            )
        )

        // ------------------------------------------------------------------------------------- sca
        val sca = runGradleSubprocess(rootDir, "sca", "osvScan")
        val osvStatus = ai.narrativetrace.build.ScannerGateSupport.status(
            layout.buildDirectory.dir("reports/security-scans").get().asFile, "osv-scanner"
        )
        val scaStatus = when {
            osvStatus.startsWith("skipped") -> ai.narrativetrace.build.VerificationStatus.SKIPPED
            sca.exitCode == 0 -> ai.narrativetrace.build.VerificationStatus.PASSED
            else -> ai.narrativetrace.build.VerificationStatus.FAILED
        }
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.SCA,
                "OSV-Scanner over the aggregated CycloneDX SBOM",
                scaStatus,
                emptyMap(),
                sca.seconds,
                withLogHint("osvScan status: $osvStatus (needs network + the osv-scanner binary; see documentation/security-tooling.md)", sca, scaStatus),
            )
        )

        // ------------------------------------------------------------------------------- translation
        val translation = runGradleSubprocess(rootDir, "translation", "translationCheck")
        val translationStatus = if (translation.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.TRANSLATION,
                "custom translationCheck (blob-hash headers + i18n manifest)",
                translationStatus,
                emptyMap(),
                translation.seconds,
                withLogHint(null, translation, translationStatus),
            )
        )

        // ----------------------------------------------------------------------------------- types
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.TYPES,
                "none",
                ai.narrativetrace.build.VerificationStatus.NOT_IMPLEMENTED,
                emptyMap(),
                0.0,
                "javac's own compile-time type checking runs on every build; no standalone type-checking tool is wired for Java",
            )
        )

        // --------------------------------------------------------------------------------- clarity
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.CLARITY,
                "none",
                ai.narrativetrace.build.VerificationStatus.NOT_IMPLEMENTED,
                emptyMap(),
                0.0,
                "no in-house clarity/naming-quality self-gate exists for this repo's own source. narrativetrace-clarity is a product feature this library offers CONSUMERS, not a self-check on this repo",
            )
        )

        // ------------------------------------------------------------------------------ unit-tests
        val unitTests = runGradleSubprocess(rootDir, "unit-tests", "test")
        val allSuites = subprojects.flatMap {
            ai.narrativetrace.build.JUnitAggregateSupport.readModuleTestResults(it.projectDir, it.name)
        }
        val unitTestsStatus = if (unitTests.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.UNIT_TESTS,
                "JUnit 5.11.4 (JUnit Platform)",
                unitTestsStatus,
                ai.narrativetrace.build.JUnitAggregateSupport.summarize(allSuites),
                unitTests.seconds,
                withLogHint(
                    "excludes @Tag(\"perf\") tests (see the separate perfTest task); JDK17 toolchain — the JDK21-only virtual-thread bodies are additionally covered by the nightly -Pnarrativetrace.toolchain=21 run, not by this row",
                    unitTests, unitTestsStatus,
                ),
            )
        )

        // ---------------------------------------------------------------- property + fuzz-tier-a (derived, 0 extra cost)
        val classification = ai.narrativetrace.build.PropertyTestClassifier.classify(rootDir)
        val propertySuites = ai.narrativetrace.build.PropertyTestClassifier.matching(allSuites, classification.propertyTestClasses)
        val fuzzTierASuites = ai.narrativetrace.build.PropertyTestClassifier.matching(allSuites, classification.fuzzTierAClasses)

        val propertyStatus = if (ai.narrativetrace.build.JUnitAggregateSupport.allGreen(propertySuites)) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.PROPERTY,
                "jqwik 1.9.2",
                propertyStatus,
                ai.narrativetrace.build.JUnitAggregateSupport.summarize(propertySuites) + mapOf("test_classes" to propertySuites.size),
                propertySuites.sumOf { it.timeSeconds },
                withLogHint(
                    "sliced from the unit-tests row's own ./gradlew test run (${classification.propertyTestClasses.size} classes using @Property, repo-wide) — 0 additional invocations",
                    unitTests, propertyStatus,
                ),
            )
        )
        val fuzzTierAStatus = if (ai.narrativetrace.build.JUnitAggregateSupport.allGreen(fuzzTierASuites)) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.FUZZ_TIER_A,
                "jqwik 1.9.2 (hostile-corpus properties) + Jazzer 0.30.0 (regression-mode corpus replay)",
                fuzzTierAStatus,
                ai.narrativetrace.build.JUnitAggregateSupport.summarize(fuzzTierASuites) + mapOf("test_classes" to fuzzTierASuites.size),
                fuzzTierASuites.sumOf { it.timeSeconds },
                withLogHint(
                    "sliced from the same test run: ${classification.hostileCorpusPropertyClasses.size} jqwik property classes over HostileCorpus + ${classification.fuzzTestClasses.size} @FuzzTest classes replaying the committed seed corpus (not fuzzing — see fuzz-tier-b)",
                    unitTests, fuzzTierAStatus,
                ),
            )
        )

        // ----------------------------------------------------------------------- architecture (ArchUnit + JDepend)
        val archUnitSuites = ai.narrativetrace.build.PropertyTestClassifier.matching(allSuites, setOf("ArchitectureTest", "ApiSurfaceTest"))
        val jdepend = runGradleSubprocess(rootDir, "architecture", "jdependCheck", "jdependCrossModule")
        val architectureStatus = if (ai.narrativetrace.build.JUnitAggregateSupport.allGreen(archUnitSuites) && jdepend.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.ARCHITECTURE,
                "ArchUnit 1.4 + JDepend 2.9.1 (per-module D≤0.7, cross-module D≤0.8, zero cycles)",
                architectureStatus,
                ai.narrativetrace.build.JUnitAggregateSupport.summarize(archUnitSuites),
                archUnitSuites.sumOf { it.timeSeconds } + jdepend.seconds,
                withLogHint(
                    "ArchUnit tests are sliced from the unit-tests run (${archUnitSuites.size} test classes); JDepend re-run separately (fast — reuses already-compiled classes)",
                    jdepend, architectureStatus,
                ),
            )
        )

        // -------------------------------------------------------------------------------- conformance
        val functionalTest = runGradleSubprocess(rootDir, "conformance", ":narrativetrace-gradle-plugin:functionalTest")
        val functionalTestSuites = ai.narrativetrace.build.JUnitAggregateSupport.readModuleTestResults(
            file("narrativetrace-gradle-plugin"), "narrativetrace-gradle-plugin", "functionalTest"
        )
        val buildTestsSuites = allSuites.filter { it.module == "narrativetrace-build-tests" }
        val conformanceSuites = functionalTestSuites + buildTestsSuites
        val conformanceStatus = if (ai.narrativetrace.build.JUnitAggregateSupport.allGreen(conformanceSuites)) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.CONFORMANCE,
                "GradleTestKit functionalTest + narrativetrace-build-tests (JUnit 5)",
                conformanceStatus,
                ai.narrativetrace.build.JUnitAggregateSupport.summarize(conformanceSuites),
                functionalTest.seconds + buildTestsSuites.sumOf { it.timeSeconds },
                withLogHint(
                    "narrativetrace-build-tests half sliced from the unit-tests run; functionalTest (GradleTestKit) run separately",
                    functionalTest, conformanceStatus,
                ),
            )
        )

        // ------------------------------------------------------------------------------------ coverage
        val jacocoToolVersion = runCatching {
            subprojects.first().extensions.getByType<org.gradle.testing.jacoco.plugins.JacocoPluginExtension>().toolVersion
        }.getOrDefault("unknown")
        val coverage = runGradleSubprocess(rootDir, "coverage", "jacocoTestCoverageVerification")
        val coverageEntries = ai.narrativetrace.build.CoverageReportSupport.collectEntriesFromProjects(subprojects)
        val totalMissed = coverageEntries.sumOf { it.missed }
        val totalCovered = coverageEntries.sumOf { it.covered }
        val coveragePct = if (totalMissed + totalCovered == 0) 0.0 else totalCovered.toDouble() / (totalMissed + totalCovered) * 100.0
        val coverageStatus = if (coverage.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.COVERAGE,
                "JaCoCo $jacocoToolVersion (98% line minimum, 97% for the agent module)",
                coverageStatus,
                mapOf("coverage_pct" to coveragePct, "lines_covered" to totalCovered, "lines_missed" to totalMissed),
                coverage.seconds,
                withLogHint(null, coverage, coverageStatus),
            )
        )

        // ---------------------------------------------------------------------------------- mutation
        val mutation = runGradleSubprocess(rootDir, "mutation", ":pitest", ":pitestAgent")
        val mutationEntries =
            ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects, mutationTestedModules) +
                ai.narrativetrace.build.MutationReportSupport.collectEntriesFromProjects(subprojects, mutationAgentModules)
        val killed = mutationEntries.count { it.status == "KILLED" }
        val survived = mutationEntries.count { it.status == "SURVIVED" }
        val noCoverage = mutationEntries.count { it.status == "NO_COVERAGE" }
        val mutationScore = if (mutationEntries.isEmpty()) 0.0 else killed.toDouble() / mutationEntries.size * 100.0
        val mutationStatus = if (mutation.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.MUTATION,
                "Pitest 1.17.4 (api, core, proxy, clarity, glossary + the agent module; 80% threshold)",
                mutationStatus,
                mapOf(
                    "mutants_killed" to killed, "mutants_survived" to survived,
                    "mutants_no_coverage" to noCoverage, "mutation_score" to mutationScore,
                ),
                mutation.seconds,
                withLogHint(if (mutationEntries.isEmpty()) "no mutation report produced" else null, mutation, mutationStatus),
            )
        )

        // -------------------------------------------------------------------------------- fuzz-tier-b
        val fuzzTierB = runGradleSubprocess(rootDir, "fuzz-tier-b", ":narrativetrace-security-tests:fuzz")
        val fuzzTargets = mapOf(
            "OutputFormat" to "ai.narrativetrace.security.fuzz.OutputFormatFuzzTest",
            "Template" to "ai.narrativetrace.security.fuzz.TemplateFuzzTest",
            "Traceparent" to "ai.narrativetrace.security.fuzz.TraceparentFuzzTest",
            "ValueRenderer" to "ai.narrativetrace.security.fuzz.ValueRendererFuzzTest",
        )
        val fuzzTargetReports = fuzzTargets.map { (target, testClass) ->
            ai.narrativetrace.build.FuzzReportSupport.read(
                target, testClass, file("narrativetrace-security-tests/build/test-results/fuzz$target")
            )
        }
        val fuzzExecutions = countOccurrences(fuzzTierB.output, Regex("""Done (\d+) runs in"""))
        val fuzzProblems = ai.narrativetrace.build.FuzzReportSupport.problems(fuzzTargetReports)
        val fuzzTierBStatus = if (fuzzProblems.isEmpty() && fuzzTierB.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.FUZZ_TIER_B,
                "Jazzer 0.30.0 (coverage-guided, JAZZER_FUZZ=1, 4 targets x FuzzBudget.PER_TARGET=5m)",
                fuzzTierBStatus,
                mapOf("executions" to fuzzExecutions, "targets_fuzzed" to (fuzzTargetReports.size - fuzzProblems.size), "targets_total" to fuzzTargetReports.size),
                fuzzTierB.seconds,
                withLogHint(
                    if (fuzzProblems.isEmpty()) "executions parsed from Jazzer's own \"Done N runs in\" console lines, summed across targets" else fuzzProblems.joinToString("; "),
                    fuzzTierB, fuzzTierBStatus,
                ),
            )
        )

        // ---------------------------------------------------------------------------------- benchmarks
        val benchmarks = runGradleSubprocess(rootDir, "benchmarks", ":narrativetrace-benchmarks:benchmarkCompare")
        val benchmarksMetrics = runCatching {
            val current = ai.narrativetrace.build.BenchmarkResultSupport.read(file("narrativetrace-benchmarks/build/reports/jmh/benchmark.json"))
            val baseline = ai.narrativetrace.build.BenchmarkCompareSupport.readBaseline(file("narrativetrace-benchmarks/baseline.txt"))
            val tolerances = ai.narrativetrace.build.BenchmarkCompareSupport.readTolerances(file("narrativetrace-benchmarks/benchmark-tolerances.properties"))
            val comparisons = ai.narrativetrace.build.BenchmarkCompareSupport.compare(baseline, current, tolerances)
            val missing = ai.narrativetrace.build.BenchmarkCompareSupport.missing(baseline, current)
            mapOf(
                "benchmarks_run" to current.size,
                "regressions" to (comparisons.count { it.regressed } + missing.size),
            )
        }.getOrDefault(mapOf("benchmarks_run" to 0, "regressions" to 0))
        val benchmarksStatus = if (benchmarks.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.BENCHMARKS,
                "JMH 0.7.3 (me.champeau.jmh) vs narrativetrace-benchmarks/baseline.txt",
                benchmarksStatus,
                benchmarksMetrics,
                benchmarks.seconds,
                withLogHint(null, benchmarks, benchmarksStatus),
            )
        )

        // ---------------------------------------------------------------------------------- allocation
        val allocation = runGradleSubprocess(rootDir, "allocation", ":narrativetrace-benchmarks:allocationCheck")
        val allocationMetrics = runCatching {
            val thresholds = ai.narrativetrace.build.AllocationCheckSupport.readThresholds(file("narrativetrace-benchmarks/allocation-thresholds.properties"))
            val measured = ai.narrativetrace.build.BenchmarkResultSupport.read(file("narrativetrace-benchmarks/build/reports/jmh/allocation.json"))
            val verdicts = ai.narrativetrace.build.AllocationCheckSupport.verdicts(thresholds, measured)
            mapOf("benchmarks_run" to verdicts.size, "regressions" to ai.narrativetrace.build.AllocationCheckSupport.problems(verdicts).size)
        }.getOrDefault(mapOf("benchmarks_run" to 0, "regressions" to 0))
        val allocationStatus = if (allocation.exitCode == 0) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.ALLOCATION,
                "JMH GC profiler vs narrativetrace-benchmarks/allocation-thresholds.properties",
                allocationStatus,
                allocationMetrics,
                allocation.seconds,
                withLogHint(null, allocation, allocationStatus),
            )
        )

        // ------------------------------------------------------------------------------- stress-short
        val stressShort = runGradleSubprocess(rootDir, "stress-short", ":narrativetrace-jcstress:jcstress", "-PjcstressMode=quick")
        val stressShortSummary = ai.narrativetrace.build.JcstressReportSupport.summarize(file("narrativetrace-jcstress/build/reports/jcstress"))
        val stressShortStatus = if (stressShortSummary.overallPassed) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.STRESS_SHORT,
                "OpenJDK jcstress 0.16 (-m quick)",
                stressShortStatus,
                mapOf("tests_passed" to stressShortSummary.testsPassed, "tests_failed" to stressShortSummary.testsFailed),
                stressShort.seconds,
                withLogHint(null, stressShort, stressShortStatus),
            )
        )

        // -------------------------------------------------------------------------------- stress-long
        // No mode override by default: jcstress's own default is its unbounded, hours-long sweep —
        // the honest "long" tier. -Pnarrativetrace.verifyAll.jcstressLongMode lets a validation run
        // time-box this one category explicitly (see README.md); the default `./gradlew verifyAll`
        // invocation passes nothing here and gets the real, hours-long sweep.
        val jcstressLongModeOverride = findProperty("narrativetrace.verifyAll.jcstressLongMode") as String?
        val stressLongArgs = mutableListOf(":narrativetrace-jcstress:jcstress")
        jcstressLongModeOverride?.let { stressLongArgs += "-PjcstressMode=$it" }
        val stressLong = runGradleSubprocess(rootDir, "stress-long", *stressLongArgs.toTypedArray())
        val stressLongSummary = ai.narrativetrace.build.JcstressReportSupport.summarize(file("narrativetrace-jcstress/build/reports/jcstress"))
        val stressLongStatus = if (stressLongSummary.overallPassed) ai.narrativetrace.build.VerificationStatus.PASSED else ai.narrativetrace.build.VerificationStatus.FAILED
        addRow(
            ai.narrativetrace.build.CategoryResult(
                ai.narrativetrace.build.VerificationCategory.STRESS_LONG,
                "OpenJDK jcstress 0.16 (" + (jcstressLongModeOverride?.let { "-m $it, time-boxed for this run" } ?: "unbounded default depth") + ")",
                stressLongStatus,
                mapOf("tests_passed" to stressLongSummary.testsPassed, "tests_failed" to stressLongSummary.testsFailed),
                stressLong.seconds,
                withLogHint(
                    jcstressLongModeOverride?.let { "time-boxed via -Pnarrativetrace.verifyAll.jcstressLongMode=$it for this run; the default ./gradlew verifyAll invocation runs jcstress's real unbounded sweep here" },
                    stressLong, stressLongStatus,
                ),
            )
        )

        // ---------------------------------------------------------------------------------- report
        val endedAt = java.time.Instant.now()
        val run = ai.narrativetrace.build.VerificationRun(
            runtime = "java",
            version = providers.gradleProperty("narrativetraceVersion").get(),
            commit = shortCommitOf(rootDir),
            host = hostDescriptor(),
            startedAt = startedAt,
            endedAt = endedAt,
            categories = results,
        )
        val dateStr = java.time.LocalDate.now().toString()
        val jsonFile = file("reports/verification/$dateStr.json")
        val mdFile = file("reports/verification/$dateStr.md")
        ai.narrativetrace.build.VerificationReportSupport.writeJson(run, jsonFile)
        mdFile.writeText(ai.narrativetrace.build.VerificationReportSupport.renderMarkdown(jsonFile))

        println()
        println(ai.narrativetrace.build.VerificationReportSupport.renderMarkdown(jsonFile))
        println("verifyAll: wrote $jsonFile and $mdFile")

        if (run.overallStatus == "failed") {
            throw GradleException(
                "verifyAll: one or more categories failed — see $mdFile for the full table " +
                    "(${results.count { it.status == ai.narrativetrace.build.VerificationStatus.FAILED }} of ${results.size} categories)"
            )
        }
    }
}
