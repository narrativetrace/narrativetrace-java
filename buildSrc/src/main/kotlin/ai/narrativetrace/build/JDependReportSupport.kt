/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import groovy.json.JsonSlurper
import java.io.File
import java.io.PrintStream

data class PackageMetrics(
    val name: String,
    val module: String,
    val classCount: Int,
    val abstractCount: Int,
    val ca: Int,
    val ce: Int,
    val abstractness: Double,
    val instability: Double,
    val distance: Double
)

data class PackageCycle(val module: String, val packages: List<String>)

data class JDependModuleResult(
    val module: String,
    val packages: List<PackageMetrics>,
    val cycles: List<PackageCycle>
)

data class JDependInput(val module: String, val classesDir: File)

object JDependReportSupport {

    fun writeJson(result: JDependModuleResult, file: File) {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"version\":\"1\",")
        sb.append("\"module\":\"${result.module}\",")
        sb.append("\"packages\":[")
        result.packages.forEachIndexed { i, p ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"name\":\"${p.name}\",")
            sb.append("\"classCount\":${p.classCount},")
            sb.append("\"abstractCount\":${p.abstractCount},")
            sb.append("\"ca\":${p.ca},")
            sb.append("\"ce\":${p.ce},")
            sb.append("\"abstractness\":${p.abstractness},")
            sb.append("\"instability\":${p.instability},")
            sb.append("\"distance\":${p.distance}")
            sb.append("}")
        }
        sb.append("],")
        sb.append("\"cycles\":[")
        result.cycles.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"module\":\"${c.module}\",")
            sb.append("\"packages\":[")
            c.packages.forEachIndexed { j, pkg ->
                if (j > 0) sb.append(",")
                sb.append("\"$pkg\"")
            }
            sb.append("]}")
        }
        sb.append("]}")
        file.parentFile?.mkdirs()
        file.writeText(sb.toString())
    }

    @Suppress("UNCHECKED_CAST")
    fun readJson(file: File): JDependModuleResult {
        val root = JsonSlurper().parseText(file.readText()) as Map<String, Any?>
        val version = root["version"] as? String
            ?: throw IllegalArgumentException("Missing 'version' field in ${file.name}")
        if (version != "1") {
            throw IllegalArgumentException("Unsupported version '$version' in ${file.name}")
        }
        val module = root["module"] as? String
            ?: throw IllegalArgumentException("Missing 'module' field in ${file.name}")

        val rawPackages = root["packages"] as? List<Map<String, Any?>> ?: emptyList()
        val packages = rawPackages.map { p ->
            PackageMetrics(
                name = p["name"] as String,
                module = module,
                classCount = (p["classCount"] as Number).toInt(),
                abstractCount = (p["abstractCount"] as Number).toInt(),
                ca = (p["ca"] as Number).toInt(),
                ce = (p["ce"] as Number).toInt(),
                abstractness = (p["abstractness"] as Number).toDouble(),
                instability = (p["instability"] as Number).toDouble(),
                distance = (p["distance"] as Number).toDouble()
            )
        }

        val rawCycles = root["cycles"] as? List<Map<String, Any?>> ?: emptyList()
        val cycles = rawCycles.map { c ->
            PackageCycle(
                module = c["module"] as String,
                packages = (c["packages"] as List<String>)
            )
        }

        return JDependModuleResult(module, packages, cycles)
    }

    fun printReport(results: List<JDependModuleResult>, out: PrintStream = System.out) {
        val allPackages = results.flatMap { it.packages }.sortedByDescending { it.distance }
        val allCycles = results.flatMap { it.cycles }

        out.println("\n${"=".repeat(120)}")
        out.println("JDEPEND PACKAGE METRICS (sorted by Distance from Main Sequence)")
        out.println("=".repeat(120))
        out.println(
            "%-6s  %-6s  %-6s  %-4s  %-4s  %-4s  %-50s  %s".format(
                "Dist", "Abstr", "Insta", "Ca", "Ce", "Cls", "Package", "Module"
            )
        )
        out.println("-".repeat(120))
        allPackages.forEach { p ->
            out.println(
                "%-6s  %-6s  %-6s  %-4d  %-4d  %-4d  %-50s  %s".format(
                    "%.2f".format(p.distance),
                    "%.2f".format(p.abstractness),
                    "%.2f".format(p.instability),
                    p.ca,
                    p.ce,
                    p.classCount,
                    p.name,
                    p.module
                )
            )
        }
        out.println("-".repeat(120))
        out.println("${allPackages.size} packages, ${allCycles.size} cycle(s)")
        if (allCycles.isNotEmpty()) {
            out.println("\nCYCLES:")
            allCycles.forEach { c ->
                out.println("  [${c.module}] ${c.packages.joinToString(" -> ")}")
            }
        }
    }

    fun analyzeCrossModule(inputs: List<JDependInput>): JDependModuleResult {
        val dirs = inputs.map { it.classesDir }.filter { it.exists() && it.isDirectory }
        if (dirs.isEmpty()) {
            return JDependModuleResult("cross-module", emptyList(), emptyList())
        }

        val jdep = jdepend.framework.JDepend()
        dirs.forEach { jdep.addDirectory(it.absolutePath) }
        val analyzed = jdep.analyze()

        val packages = extractPackages(analyzed, "cross-module")
        val cycles = extractCycles(analyzed, "cross-module")

        return JDependModuleResult("cross-module", packages, cycles)
    }

    fun analyze(inputs: List<JDependInput>): List<JDependModuleResult> {
        return inputs.map { input -> analyzeModule(input) }
    }

    private fun analyzeModule(input: JDependInput): JDependModuleResult {
        if (!input.classesDir.exists() || !input.classesDir.isDirectory) {
            return JDependModuleResult(input.module, emptyList(), emptyList())
        }

        val jdep = jdepend.framework.JDepend()
        jdep.addDirectory(input.classesDir.absolutePath)
        val analyzed = jdep.analyze()

        val packages = extractPackages(analyzed, input.module)
        val cycles = extractCycles(analyzed, input.module)

        return JDependModuleResult(input.module, packages, cycles)
    }

    private fun extractPackages(
        analyzed: Collection<*>,
        moduleName: String
    ): List<PackageMetrics> {
        return analyzed
            .filterIsInstance<jdepend.framework.JavaPackage>()
            .filter { it.name.startsWith("ai.narrativetrace") && it.classCount > 0 }
            .map { pkg ->
                PackageMetrics(
                    name = pkg.name,
                    module = moduleName,
                    classCount = pkg.classCount,
                    abstractCount = pkg.abstractClassCount,
                    ca = pkg.afferentCoupling(),
                    ce = pkg.efferentCoupling(),
                    abstractness = safeDouble(pkg.abstractness()),
                    instability = safeDouble(pkg.instability()),
                    distance = safeDouble(pkg.distance())
                )
            }
    }

    private fun extractCycles(
        analyzed: Collection<*>,
        moduleName: String
    ): List<PackageCycle> {
        val cycles = mutableListOf<PackageCycle>()
        val seen = mutableSetOf<Set<String>>()
        for (pkg in analyzed.filterIsInstance<jdepend.framework.JavaPackage>()) {
            if (!pkg.name.startsWith("ai.narrativetrace")) continue
            if (pkg.containsCycle()) {
                val cyclePackages = mutableListOf<jdepend.framework.JavaPackage>()
                pkg.collectCycle(cyclePackages)
                val names = cyclePackages.map { it.name }.filter { it.startsWith("ai.narrativetrace") }
                val key = names.toSet()
                if (key.size > 1 && seen.add(key)) {
                    cycles.add(PackageCycle(moduleName, names))
                }
            }
        }
        return cycles
    }

    private fun safeDouble(value: Float): Double {
        return if (value.isNaN()) 0.0 else value.toDouble()
    }

    /**
     * Fails the build on package cycles and on packages too far from the main sequence.
     *
     * Distance is a ratio over the classes JDepend actually parsed, and JDepend silently skips
     * classes carrying `invokedynamic` constant-pool entries — which today means records, lambdas,
     * method references, and compiled string concatenation. Across this repository it parses only a
     * fraction of each package (8 of 44 classes in `core.event`, 12 of 21 in `core.pipeline`), so on
     * a very small parsed sample abstractness and instability describe the parser's blind spots
     * rather than the design: a package whose only parseable members are interfaces scores a
     * distance of 1.00 no matter what depends on it. [minClassesForDistanceEnforcement] withholds
     * the distance check for those samples, the same reasoning [minPackagesForEnforcement] already
     * applies at module level. Cycles are unaffected — that check does not depend on sample size,
     * and ArchUnit polices cycles independently.
     */
    fun enforceThresholds(
        results: List<JDependModuleResult>,
        maxDistance: Double,
        failOnCycles: Boolean,
        minPackagesForEnforcement: Int,
        minClassesForDistanceEnforcement: Int = 3
    ) {
        val failures = mutableListOf<String>()
        val unmeasured = mutableListOf<String>()

        for (result in results) {
            if (failOnCycles && result.cycles.isNotEmpty()) {
                result.cycles.forEach { c ->
                    failures.add("[${c.module}] Package cycle: ${c.packages.joinToString(" -> ")}")
                }
            }

            if (result.packages.size < minPackagesForEnforcement) continue

            for (p in result.packages) {
                if (p.classCount < minClassesForDistanceEnforcement) {
                    unmeasured.add("[${p.module}] ${p.name} (${p.classCount} classes parsed)")
                    continue
                }
                if (p.distance > maxDistance) {
                    val dist = "%.2f".format(p.distance)
                    failures.add("[${p.module}] ${p.name} distance=$dist exceeds max $maxDistance")
                }
            }
        }

        if (unmeasured.isNotEmpty()) {
            println(
                "JDepend distance not enforced (parsed sample too small):\n  " +
                    unmeasured.joinToString("\n  ")
            )
        }

        if (failures.isNotEmpty()) {
            throw RuntimeException("JDepend threshold violations:\n  ${failures.joinToString("\n  ")}")
        }
    }
}
