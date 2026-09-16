/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScannerGateSupportTest {

    @TempDir
    lateinit var dir: File

    // ---------------------------------------------------------------- missing-binary decision

    @Test
    fun `a missing binary fails outright when scanners are required`() {
        val decision = ScannerGateSupport.onMissingBinary("gitleaks", required = true, installHint = "https://example/install")

        assertTrue(decision.fail)
        assertTrue(decision.message.contains("gitleaks"))
        assertTrue(decision.message.contains("required"))
    }

    @Test
    fun `a missing binary warns loudly when scanners are not required`() {
        val decision = ScannerGateSupport.onMissingBinary("semgrep", required = false, installHint = "https://example/install")

        assertFalse(decision.fail)
        assertTrue(decision.message.contains("SKIPPED"))
        assertTrue(decision.message.contains("NOT a clean scan"))
        assertTrue(decision.message.contains("https://example/install"))
    }

    // ------------------------------------------------------------ semgrep exit-code verdict

    @Test
    fun `semgrep exit 0 is a clean scan`() {
        assertEquals(ScannerGateSupport.ScanVerdict.CLEAN, ScannerGateSupport.semgrepVerdict(0))
    }

    @Test
    fun `semgrep exit 1 under --error means findings`() {
        assertEquals(ScannerGateSupport.ScanVerdict.FINDINGS, ScannerGateSupport.semgrepVerdict(1))
    }

    @Test
    fun `any other semgrep exit code is a scanner error, never findings and never clean`() {
        for (code in listOf(2, 3, 4, 5, 7, 8, 13, 127, -1)) {
            assertEquals(ScannerGateSupport.ScanVerdict.SCANNER_ERROR, ScannerGateSupport.semgrepVerdict(code), "exit $code")
        }
    }

    // ---------------------------------------------------------------- findings summary

    private val twoFindings = """
        {"results":[
          {"check_id":"java.lang.security.audit.crypto.use-of-md5.use-of-md5",
           "path":"src/main/java/Hash.java","start":{"line":7,"col":5},"end":{"line":7,"col":40},
           "extra":{"message":"Detected MD5 hash algorithm which is considered insecure.","severity":"WARNING"}},
          {"check_id":"java.lang.security.audit.command-injection-process-builder.command-injection-process-builder",
           "path":"src/main/java/Shell.java","start":{"line":12,"col":9},"end":{"line":12,"col":60},
           "extra":{"message":"A formatted or concatenated string was detected as input to a ProcessBuilder call.","severity":"ERROR"}}
        ],"errors":[],"paths":{"scanned":["src/main/java/Hash.java","src/main/java/Shell.java"]}}
    """.trimIndent()

    @Test
    fun `findings are summarised one per line as rule, path and line`() {
        val summary = ScannerGateSupport.summarizeSemgrepFindings(twoFindings)

        assertEquals(
            listOf(
                "java.lang.security.audit.crypto.use-of-md5.use-of-md5 — src/main/java/Hash.java:7",
                "java.lang.security.audit.command-injection-process-builder.command-injection-process-builder — src/main/java/Shell.java:12",
            ),
            summary,
        )
    }

    @Test
    fun `an empty results array summarises to nothing`() {
        assertEquals(emptyList<String>(), ScannerGateSupport.summarizeSemgrepFindings("""{"results":[],"errors":[]}"""))
    }

    @Test
    fun `an unreadable report still yields a summary line rather than hiding the failure`() {
        val summary = ScannerGateSupport.summarizeSemgrepFindings("not json at all")

        assertEquals(1, summary.size)
        assertTrue(summary.single().contains("could not be parsed"), summary.single())
    }

    @Test
    fun `a report with no results key is reported as unreadable, not as clean`() {
        val summary = ScannerGateSupport.summarizeSemgrepFindings("""{"errors":[]}""")

        assertEquals(1, summary.size)
        assertTrue(summary.single().contains("could not be parsed"), summary.single())
    }

    // ------------------------------------------------------- ran-clean vs skipped vs never-ran

    @Test
    fun `a scan that never ran reports never-ran, not a clean pass`() {
        assertEquals("never-ran", ScannerGateSupport.status(dir, "gitleaks"))
    }

    @Test
    fun `a recorded skip is distinguishable from a clean run`() {
        ScannerGateSupport.recordSkipped(dir, "gitleaks", "binary not on PATH")

        val status = ScannerGateSupport.status(dir, "gitleaks")
        assertTrue(status.startsWith("skipped"))
        assertTrue(status.contains("binary not on PATH"))
    }

    @Test
    fun `a clean run is recorded as ran-clean`() {
        ScannerGateSupport.recordRanClean(dir, "gitleaks")

        assertEquals("ran-clean", ScannerGateSupport.status(dir, "gitleaks"))
    }

    @Test
    fun `a later clean run replaces an earlier skip`() {
        ScannerGateSupport.recordSkipped(dir, "osv-scanner", "binary not on PATH")
        ScannerGateSupport.recordRanClean(dir, "osv-scanner")

        assertEquals("ran-clean", ScannerGateSupport.status(dir, "osv-scanner"))
    }

    @Test
    fun `each tool has its own status`() {
        ScannerGateSupport.recordRanClean(dir, "gitleaks")
        ScannerGateSupport.recordSkipped(dir, "semgrep", "binary not on PATH")

        assertEquals("ran-clean", ScannerGateSupport.status(dir, "gitleaks"))
        assertTrue(ScannerGateSupport.status(dir, "semgrep").startsWith("skipped"))
    }
}
