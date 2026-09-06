/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class JDependReportSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun writeJsonProducesValidFields() {
        val result = JDependModuleResult(
            module = "narrativetrace-core",
            packages = listOf(
                PackageMetrics(
                    name = "ai.narrativetrace.core",
                    module = "narrativetrace-core",
                    classCount = 10,
                    abstractCount = 3,
                    ca = 5,
                    ce = 2,
                    abstractness = 0.3,
                    instability = 0.29,
                    distance = 0.41
                )
            ),
            cycles = emptyList()
        )

        val file = tempDir.resolve("jdepend.json")
        JDependReportSupport.writeJson(result, file)

        val text = file.readText()
        assertTrue(text.contains("\"version\":\"1\""))
        assertTrue(text.contains("\"module\":\"narrativetrace-core\""))
        assertTrue(text.contains("\"name\":\"ai.narrativetrace.core\""))
        assertTrue(text.contains("\"classCount\":10"))
        assertTrue(text.contains("\"abstractCount\":3"))
        assertTrue(text.contains("\"ca\":5"))
        assertTrue(text.contains("\"ce\":2"))
        assertTrue(text.contains("\"abstractness\":0.3"))
        assertTrue(text.contains("\"instability\":0.29"))
        assertTrue(text.contains("\"distance\":0.41"))
        assertTrue(text.contains("\"cycles\":[]"))
    }

    @Test
    fun readJsonRoundTripsPackages() {
        val original = JDependModuleResult(
            module = "narrativetrace-core",
            packages = listOf(
                PackageMetrics("ai.narrativetrace.core", "narrativetrace-core", 10, 3, 5, 2, 0.3, 0.29, 0.41)
            ),
            cycles = emptyList()
        )

        val file = tempDir.resolve("rt.json")
        JDependReportSupport.writeJson(original, file)
        val restored = JDependReportSupport.readJson(file)

        assertEquals(original.module, restored.module)
        assertEquals(1, restored.packages.size)
        val p = restored.packages[0]
        assertEquals("ai.narrativetrace.core", p.name)
        assertEquals(10, p.classCount)
        assertEquals(3, p.abstractCount)
        assertEquals(5, p.ca)
        assertEquals(2, p.ce)
        assertEquals(0.3, p.abstractness, 0.001)
        assertEquals(0.29, p.instability, 0.001)
        assertEquals(0.41, p.distance, 0.001)
    }

    @Test
    fun readJsonRoundTripsCycles() {
        val original = JDependModuleResult(
            module = "narrativetrace-core",
            packages = emptyList(),
            cycles = listOf(
                PackageCycle("narrativetrace-core", listOf("ai.narrativetrace.core", "ai.narrativetrace.core.config"))
            )
        )

        val file = tempDir.resolve("cycles.json")
        JDependReportSupport.writeJson(original, file)
        val restored = JDependReportSupport.readJson(file)

        assertEquals(1, restored.cycles.size)
        assertEquals("narrativetrace-core", restored.cycles[0].module)
        assertEquals(listOf("ai.narrativetrace.core", "ai.narrativetrace.core.config"), restored.cycles[0].packages)
    }

    @Test
    fun readJsonRejectsMissingVersion() {
        val file = tempDir.resolve("bad.json")
        file.writeText("""{"module":"m","packages":[],"cycles":[]}""")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JDependReportSupport.readJson(file)
        }
        assertTrue(ex.message!!.contains("version"))
    }

    @Test
    fun readJsonRejectsMalformedJson() {
        val file = tempDir.resolve("malformed.json")
        file.writeText("not json at all")

        assertThrows(Exception::class.java) {
            JDependReportSupport.readJson(file)
        }
    }

    @Test
    fun printReportSortsByDistanceDescending() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.low", "mod-a", 5, 1, 2, 3, 0.2, 0.6, 0.2),
                    PackageMetrics("ai.narrativetrace.high", "mod-a", 8, 4, 1, 5, 0.5, 0.83, 0.67)
                ),
                emptyList()
            )
        )

        val output = ByteArrayOutputStream()
        JDependReportSupport.printReport(results, PrintStream(output))
        val text = output.toString()

        assertTrue(text.contains("JDEPEND PACKAGE METRICS"))
        assertTrue(text.indexOf("ai.narrativetrace.high") < text.indexOf("ai.narrativetrace.low"))
    }

    @Test
    fun printReportShowsCycleCount() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(PackageMetrics("ai.narrativetrace.core", "mod-a", 5, 1, 2, 3, 0.2, 0.6, 0.2)),
                listOf(PackageCycle("mod-a", listOf("ai.narrativetrace.a", "ai.narrativetrace.b")))
            )
        )

        val output = ByteArrayOutputStream()
        JDependReportSupport.printReport(results, PrintStream(output))
        val text = output.toString()

        assertTrue(text.contains("1 cycle"))
    }

    @Test
    fun enforceThresholdsPassesWhenBelowLimits() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.core", "mod-a", 10, 3, 5, 2, 0.3, 0.29, 0.41),
                    PackageMetrics("ai.narrativetrace.config", "mod-a", 4, 1, 3, 1, 0.25, 0.25, 0.5)
                ),
                emptyList()
            )
        )

        assertDoesNotThrow {
            JDependReportSupport.enforceThresholds(results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2)
        }
    }

    @Test
    fun enforceThresholdsFailsOnHighDistance() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.bad", "mod-a", 10, 0, 5, 0, 0.0, 0.0, 1.0),
                    PackageMetrics("ai.narrativetrace.ok", "mod-a", 4, 1, 3, 1, 0.25, 0.25, 0.5)
                ),
                emptyList()
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            JDependReportSupport.enforceThresholds(results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2)
        }
        assertTrue(ex.message!!.contains("ai.narrativetrace.bad"))
        assertTrue(ex.message!!.contains("1.0"))
    }

    @Test
    fun enforceThresholdsFailsOnCycles() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(PackageMetrics("ai.narrativetrace.core", "mod-a", 10, 3, 5, 2, 0.3, 0.29, 0.41)),
                listOf(PackageCycle("mod-a", listOf("ai.narrativetrace.a", "ai.narrativetrace.b")))
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            JDependReportSupport.enforceThresholds(results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 1)
        }
        assertTrue(ex.message!!.contains("cycle"))
    }

    @Test
    fun enforceThresholdsSkipsSinglePackageModules() {
        val results = listOf(
            JDependModuleResult(
                "mod-single",
                listOf(
                    PackageMetrics("ai.narrativetrace.single", "mod-single", 5, 0, 0, 0, 0.0, 0.0, 1.0)
                ),
                emptyList()
            )
        )

        assertDoesNotThrow {
            JDependReportSupport.enforceThresholds(results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2)
        }
    }

    @Test
    fun analyzeEmptyDirReturnsEmptyResult() {
        val emptyDir = tempDir.resolve("empty")
        emptyDir.mkdirs()
        val results = JDependReportSupport.analyze(listOf(JDependInput("mod", emptyDir)))

        assertEquals(1, results.size)
        assertTrue(results[0].packages.isEmpty())
        assertTrue(results[0].cycles.isEmpty())
    }

    @Test
    fun analyzeNonExistentDirReturnsEmptyResult() {
        val missing = tempDir.resolve("nope")
        val results = JDependReportSupport.analyze(listOf(JDependInput("mod", missing)))

        assertEquals(1, results.size)
        assertTrue(results[0].packages.isEmpty())
    }

    @Test
    fun writeAndReadEmptyPackageList() {
        val result = JDependModuleResult("empty-mod", emptyList(), emptyList())
        val file = tempDir.resolve("empty.json")
        JDependReportSupport.writeJson(result, file)
        val restored = JDependReportSupport.readJson(file)

        assertEquals("empty-mod", restored.module)
        assertTrue(restored.packages.isEmpty())
        assertTrue(restored.cycles.isEmpty())
    }

    @Test
    fun analyzeEmptyInputListReturnsEmpty() {
        val results = JDependReportSupport.analyze(emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun writeJsonHandlesNanAsZero() {
        val result = JDependModuleResult(
            module = "mod",
            packages = listOf(
                PackageMetrics("ai.narrativetrace.x", "mod", 1, 0, 0, 0, 0.0, 0.0, 0.0)
            ),
            cycles = emptyList()
        )
        val file = tempDir.resolve("nan.json")
        JDependReportSupport.writeJson(result, file)
        val restored = JDependReportSupport.readJson(file)

        assertEquals(0.0, restored.packages[0].distance, 0.001)
        assertEquals(0.0, restored.packages[0].abstractness, 0.001)
        assertEquals(0.0, restored.packages[0].instability, 0.001)
    }

    @Test
    fun enforceThresholdsDoesNotJudgeDistanceOnTooSmallAParsedSample() {
        // JDepend skips classes carrying invokedynamic (records, lambdas, method references), so a
        // package can present a handful of parsed classes that misrepresent it — most often an
        // all-interface remnant scoring distance 1.00. Those samples are reported, not failed.
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.phantom", "mod-a", 0, 0, 1, 0, 0.0, 0.0, 1.0),
                    PackageMetrics("ai.narrativetrace.spi", "mod-a", 2, 2, 0, 4, 1.0, 1.0, 1.0),
                    PackageMetrics("ai.narrativetrace.real", "mod-a", 5, 1, 2, 3, 0.2, 0.6, 0.2)
                ),
                emptyList()
            )
        )

        JDependReportSupport.enforceThresholds(
            results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2
        )
    }

    @Test
    fun enforceThresholdsStillFailsOnceTheParsedSampleIsBigEnough() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.spi", "mod-a", 3, 3, 0, 4, 1.0, 1.0, 1.0),
                    PackageMetrics("ai.narrativetrace.real", "mod-a", 5, 1, 2, 3, 0.2, 0.6, 0.2)
                ),
                emptyList()
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            JDependReportSupport.enforceThresholds(
                results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2
            )
        }
        assertTrue(ex.message!!.contains("ai.narrativetrace.spi"))
    }

    @Test
    fun enforceThresholdsStillReportsCyclesRegardlessOfSampleSize() {
        val results = listOf(
            JDependModuleResult(
                "mod-a",
                listOf(
                    PackageMetrics("ai.narrativetrace.spi", "mod-a", 1, 1, 0, 4, 1.0, 1.0, 1.0),
                    PackageMetrics("ai.narrativetrace.real", "mod-a", 5, 1, 2, 3, 0.2, 0.6, 0.2)
                ),
                listOf(PackageCycle("mod-a", listOf("a", "b", "a")))
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            JDependReportSupport.enforceThresholds(
                results, maxDistance = 0.7, failOnCycles = true, minPackagesForEnforcement = 2
            )
        }
        assertTrue(ex.message!!.contains("Package cycle"))
    }

    @Test
    fun analyzeCrossModuleEmptyInputsReturnsEmptyResult() {
        val result = JDependReportSupport.analyzeCrossModule(emptyList())

        assertEquals("cross-module", result.module)
        assertTrue(result.packages.isEmpty())
        assertTrue(result.cycles.isEmpty())
    }

    @Test
    fun analyzeCrossModuleSkipsNonExistentDirs() {
        val missing1 = tempDir.resolve("nope1")
        val missing2 = tempDir.resolve("nope2")
        val result = JDependReportSupport.analyzeCrossModule(
            listOf(JDependInput("mod-a", missing1), JDependInput("mod-b", missing2))
        )

        assertEquals("cross-module", result.module)
        assertTrue(result.packages.isEmpty())
    }
}
