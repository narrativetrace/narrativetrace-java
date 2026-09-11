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
