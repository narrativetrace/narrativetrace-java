/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LlmsTxtBannerSupportTest {

    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("llms.txt")

    @Test
    fun currentLineIsNullWhenTheMarkersAreAbsent() {
        file.writeText("# NarrativeTrace\n\n> Code is the log.\n")

        assertNull(LlmsTxtBannerSupport.currentLine(file))
    }

    @Test
    fun currentLineIsNullWhenTheFileIsAbsent() {
        assertNull(LlmsTxtBannerSupport.currentLine(dir.resolve("missing.txt")))
    }

    @Test
    fun writeLineInsertsTheMarkerPairRightAfterTheHeading() {
        file.writeText("# NarrativeTrace\n\n> Code is the log.\n")

        LlmsTxtBannerSupport.writeLine(file, "*(Docs and published both at 0.2.1.)*")

        val lines = file.readText().lines()
        assertEquals("# NarrativeTrace", lines[0])
        assertEquals("", lines[1])
        assertEquals("<!-- docs-vs-published -->", lines[2])
        assertEquals("*(Docs and published both at 0.2.1.)*", lines[3])
        assertEquals("<!-- /docs-vs-published -->", lines[4])
        assertEquals("*(Docs and published both at 0.2.1.)*", LlmsTxtBannerSupport.currentLine(file))
    }

    @Test
    fun writeLineReplacesAnExistingBannerInPlace() {
        file.writeText(
            "# NarrativeTrace\n\n<!-- docs-vs-published -->\n*(old line)*\n<!-- /docs-vs-published -->\n\n> Code is the log.\n"
        )

        LlmsTxtBannerSupport.writeLine(file, "*(new line)*")

        assertEquals("*(new line)*", LlmsTxtBannerSupport.currentLine(file))
        assertEquals(1, file.readText().split("<!-- docs-vs-published -->").size - 1)
    }

    @Test
    fun writeLineIsIdempotent() {
        file.writeText("# NarrativeTrace\n\n> Code is the log.\n")
        LlmsTxtBannerSupport.writeLine(file, "*(Docs and published both at 0.2.1.)*")
        val onceWritten = file.readText()

        LlmsTxtBannerSupport.writeLine(file, "*(Docs and published both at 0.2.1.)*")

        assertEquals(onceWritten, file.readText())
    }
}
