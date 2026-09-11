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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SnippetSupportTest {

    @TempDir
    lateinit var repo: File

    private fun writeSource(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun writeDoc(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    // Deliberately flush-left (no trimIndent()): trimIndent() picks its margin from every line of
    // a multi-line $body too, so a body whose own lines are not indented to match this function's
    // nesting would defeat it and leave stray leading whitespace on the template's own lines.
    private fun page(marker: String, body: String, lang: String = "java") =
        "# Title\n\n$marker\n```$lang\n$body\n```\n<!-- /snippet -->\n"

    // ---------------------------------------------------------------------------------------
    // parseMarker
    // ---------------------------------------------------------------------------------------

    @Test
    fun parseMarkerReadsThePlainPath() {
        val marker = SnippetSupport.parseMarker("<!-- snippet: sixty-seconds/src/main/java/Main.java -->")

        assertEquals(SnippetSupport.SnippetMarker("sixty-seconds/src/main/java/Main.java", null, null), marker)
    }

    @Test
    fun parseMarkerReadsRegionAndMask() {
        val marker = SnippetSupport.parseMarker("<!-- snippet: a/b.txt region=NAME mask=duration -->")

        assertEquals("a/b.txt", marker!!.path)
        assertEquals("NAME", marker.region)
        assertEquals("duration", marker.mask)
    }

    @Test
    fun parseMarkerRejectsAnUnrelatedLine() {
        assertNull(SnippetSupport.parseMarker("just some prose"))
    }

    @Test
    fun parseMarkerRejectsTheClosingMarker() {
        assertNull(SnippetSupport.parseMarker("<!-- /snippet -->"))
    }

    // ---------------------------------------------------------------------------------------
    // englishMarkdownFiles
    // ---------------------------------------------------------------------------------------

    @Test
    fun englishMarkdownFilesFindsAMarkedPageAtTheRepoRoot() {
        writeSource("sixty-seconds/src/main/java/com/example/orders/Main.java", "class Main {}\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->", "class Main {}")
        )

        val found = SnippetSupport.englishMarkdownFiles(repo)

        assertEquals(1, found.size, found.toString())
        assertTrue(found.single().path.endsWith("first-10-minutes.md"))
    }

    @Test
    fun englishMarkdownFilesSkipsTranslatedMirrors() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main {}\n")
        val content = page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "class Main {}")
        writeDoc("documentation/first-10-minutes.md", content)
        writeDoc("documentation/es/primeros-10-minutos.md", content)

        val found = SnippetSupport.englishMarkdownFiles(repo)

        assertEquals(1, found.size, found.toString())
        assertTrue(found.single().path.contains("first-10-minutes.md") && !found.single().path.contains("/es/"))
    }

    // ---------------------------------------------------------------------------------------
    // check
    // ---------------------------------------------------------------------------------------

    @Test
    fun checkAcceptsAPageThatMatchesItsSourceExactly() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main {}\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "class Main {}")
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkReportsADriftedBlockNamingBothPaths() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main { /* changed */ }\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "class Main {}")
        )

        val problems = SnippetSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("documentation/first-10-minutes.md"), problems.single())
        assertTrue(problems.single().contains("sixty-seconds/src/main/java/Main.java"), problems.single())
    }

    @Test
    fun checkReportsAMissingSourceInsteadOfThrowing() {
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Gone.java -->", "class Gone {}")
        )

        val problems = SnippetSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("is missing"), problems.single())
    }

    // ---------------------------------------------------------------------------------------
    // license headers — stamped into the source only, at publish time; never on the page
    // ---------------------------------------------------------------------------------------

    @Test
    fun checkStripsALeadingBlockCommentLicenseHeaderBeforeComparing() {
        writeSource(
            "sixty-seconds/src/main/java/Main.java",
            """
            /*
             * Copyright (c) 2026 Empower Agile
             *
             * SPDX-License-Identifier: BUSL-1.1
             * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
             * years from publication; Change License: Apache-2.0
             */
            // src/main/java/Main.java
            package com.example.orders;

            class Main {}
            """.trimIndent() + "\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/src/main/java/Main.java -->",
                "// src/main/java/Main.java\npackage com.example.orders;\n\nclass Main {}"
            )
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkStripsALeadingLineCommentLicenseHeaderBeforeComparing() {
        writeSource(
            "sixty-seconds/deploy.sh",
            "// SPDX-License-Identifier: BUSL-1.1\n" +
                "// Licensed under the Business Source License 1.1 (see LICENSE)\n" +
                "echo hello\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/deploy.sh -->", "echo hello", lang = "bash")
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkStripsALineCommentLicenseHeaderThatTouchesTheFilesOwnFollowingComment() {
        // The publish script's real shape for a "free" module: SPDX line, one Licensed-under line,
        // one Copyright line, then the original file's first line — no blank line separates them,
        // and here that first line is itself the tutorial's own `//` path label.
        writeSource(
            "sixty-seconds/src/main/java/Main.java",
            "// SPDX-License-Identifier: BUSL-1.1\n" +
                "// Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0\n" +
                "// Copyright (c) 2026 Empower Agile\n" +
                "// src/main/java/Main.java\n" +
                "package com.example.orders;\n\n" +
                "class Main {}\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/src/main/java/Main.java -->",
                "// src/main/java/Main.java\npackage com.example.orders;\n\nclass Main {}"
            )
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkStripsALeadingXmlCommentLicenseHeaderBeforeComparing() {
        writeSource(
            "sixty-seconds/src/main/resources/logback.xml",
            """
            <!--
              Copyright (c) 2026 Empower Agile

              SPDX-License-Identifier: BUSL-1.1
              Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
              years from publication; Change License: Apache-2.0
            -->
            <!-- src/main/resources/logback.xml -->
            <configuration/>
            """.trimIndent() + "\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/src/main/resources/logback.xml -->",
                "<!-- src/main/resources/logback.xml -->\n<configuration/>",
                lang = "xml"
            )
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkPreservesANonLicenseLeadingXmlComment() {
        // logback.xml's own first line, unheadered — must never be mistaken for a license block.
        writeSource(
            "sixty-seconds/src/main/resources/logback.xml",
            "<!-- src/main/resources/logback.xml -->\n<configuration/>\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/resources/logback.xml -->", "<configuration/>", lang = "xml")
        )

        assertEquals(1, SnippetSupport.check(repo).size)
    }

    @Test
    fun checkPreservesANonLicenseLeadingComment() {
        writeSource(
            "sixty-seconds/src/main/java/Main.java",
            "// src/main/java/Main.java\npackage com.example.orders;\n\nclass Main {}\n"
        )
        // The page is missing the tutorial's own path-comment line — a real drift, not a header.
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/src/main/java/Main.java -->",
                "package com.example.orders;\n\nclass Main {}"
            )
        )

        assertEquals(1, SnippetSupport.check(repo).size)
    }

    @Test
    fun checkComparesUnchangedWhenTheSourceHasNoHeaderAtAll() {
        writeSource("sixty-seconds/src/main/java/Main.java", "package com.example.orders;\n\nclass Main {}\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "package com.example.orders;\n\nclass Main {}")
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun syncNeverWritesALicenseHeaderIntoThePage() {
        writeSource(
            "sixty-seconds/src/main/java/Main.java",
            """
            /*
             * Copyright (c) 2026 Empower Agile
             *
             * SPDX-License-Identifier: BUSL-1.1
             * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
             * years from publication; Change License: Apache-2.0
             */
            // src/main/java/Main.java
            package com.example.orders;

            class Main { /* changed */ }
            """.trimIndent() + "\n"
        )
        val doc = repo.resolve("documentation/first-10-minutes.md")
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/src/main/java/Main.java -->",
                "// src/main/java/Main.java\npackage com.example.orders;\n\nclass Main {}"
            )
        )

        SnippetSupport.sync(repo)

        val content = doc.readText()
        assertTrue(content.contains("class Main { /* changed */ }"), content)
        assertTrue(!content.contains("SPDX-License-Identifier"), content)
        assertTrue(!content.contains("Copyright (c) 2026 Empower Agile"), content)
    }

    @Test
    fun checkMasksDurationOnBothSidesBeforeComparing() {
        writeSource("sixty-seconds/build/narrativetrace/see-a-trace.txt", "placeOrder(...) — 17ms\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/build/narrativetrace/see-a-trace.txt mask=duration -->",
                "placeOrder(...) — 3ms",
                lang = "text"
            )
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun checkStillFailsUnderMaskingWhenSomethingOtherThanDurationChanged() {
        writeSource("sixty-seconds/build/narrativetrace/see-a-trace.txt", "placeOrder(other) — 17ms\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/build/narrativetrace/see-a-trace.txt mask=duration -->",
                "placeOrder(...) — 3ms",
                lang = "text"
            )
        )

        assertEquals(1, SnippetSupport.check(repo).size)
    }

    @Test
    fun checkReportsAnUnknownMask() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main {}\n")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java mask=timestamp -->", "class Main {}")
        )

        val problems = SnippetSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("unknown mask"), problems.single())
    }

    @Test
    fun checkReadsANamedRegionInsteadOfTheWholeFile() {
        writeSource(
            "sixty-seconds/src/main/java/Main.java",
            """
            package com.example.orders;

            // snippet:begin body
            class Main {}
            // snippet:end body
            """.trimIndent() + "\n"
        )
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java region=body -->", "class Main {}")
        )

        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    // ---------------------------------------------------------------------------------------
    // sync
    // ---------------------------------------------------------------------------------------

    @Test
    fun syncRewritesADriftedBlockToMatchTheSource() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main { /* changed */ }\n")
        val doc = repo.resolve("documentation/first-10-minutes.md")
        writeDoc(
            "documentation/first-10-minutes.md",
            page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "class Main {}")
        )

        val changed = SnippetSupport.sync(repo)

        assertEquals(1, changed.size, changed.toString())
        assertTrue(doc.readText().contains("class Main { /* changed */ }"))
        assertEquals(emptyList<String>(), SnippetSupport.check(repo))
    }

    @Test
    fun syncLeavesAnAlreadyInSyncPageUntouched() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main {}\n")
        val content = page("<!-- snippet: sixty-seconds/src/main/java/Main.java -->", "class Main {}")
        val doc = repo.resolve("documentation/first-10-minutes.md")
        writeDoc("documentation/first-10-minutes.md", content)
        val before = doc.lastModified()

        val changed = SnippetSupport.sync(repo)

        assertEquals(emptyList<String>(), changed)
        assertEquals(before, doc.lastModified())
    }

    @Test
    fun syncCopiesTheRealDurationEvenUnderAMaskedMarker() {
        writeSource("sixty-seconds/build/narrativetrace/see-a-trace.txt", "placeOrder(...) — 17ms\n")
        val doc = repo.resolve("documentation/first-10-minutes.md")
        writeDoc(
            "documentation/first-10-minutes.md",
            page(
                "<!-- snippet: sixty-seconds/build/narrativetrace/see-a-trace.txt mask=duration -->",
                "placeOrder(...) — 3ms",
                lang = "text"
            )
        )

        SnippetSupport.sync(repo)

        // sync never masks what it writes — the page keeps the real, current duration.
        assertTrue(doc.readText().contains("— 17ms"))
    }

    @Test
    fun syncNeverTouchesATranslatedMirror() {
        writeSource("sixty-seconds/src/main/java/Main.java", "class Main { /* changed */ }\n")
        val marker = "<!-- snippet: sixty-seconds/src/main/java/Main.java -->"
        val stale = page(marker, "class Main {}")
        writeDoc("documentation/first-10-minutes.md", stale)
        writeDoc("documentation/es/primeros-10-minutos.md", stale)

        SnippetSupport.sync(repo)

        assertTrue(repo.resolve("documentation/es/primeros-10-minutos.md").readText().contains("class Main {}"))
    }
}
