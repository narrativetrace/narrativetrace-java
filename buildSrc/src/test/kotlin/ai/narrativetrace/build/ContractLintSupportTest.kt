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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContractLintSupportTest {

    @TempDir
    lateinit var tempDir: File

    // ---- slugify / headingAnchors -----------------------------------------------------------

    @Test
    fun slugifyMatchesKnownGitHubAnchor() {
        // Real anchor this repo already links to (maven-guide.md, sixty-seconds.md) — ground truth.
        assertEquals("7-slf4j-configuration", ContractLintSupport.slugify("7. SLF4J Configuration"))
    }

    @Test
    fun slugifyStripsPunctuationButKeepsHyphensAndUnderscores() {
        assertEquals(
            "a-types-own-tostring-is-never-trusted-while-the-type-has-state",
            ContractLintSupport.slugify("A type's own `toString()` is never trusted while the type has state")
        )
    }

    @Test
    fun slugifyDoesNotCollapseAdjacentSeparators() {
        // An em dash between two words removes to nothing, leaving both surrounding spaces —
        // which become a double hyphen. Deliberate: this is what GitHub's own renderer produces.
        assertEquals(
            "the-buffered-path--capture-that-never-blocks",
            ContractLintSupport.slugify("The buffered path — capture that never blocks")
        )
    }

    @Test
    fun headingAnchorsDisambiguatesRepeatedSlugs() {
        val file = tempDir.resolve("page.md")
        file.writeText(
            """
            # Title
            ## Properties
            some text
            ## Properties
            """.trimIndent()
        )
        assertEquals(setOf("title", "properties", "properties-1"), ContractLintSupport.headingAnchors(file))
    }

    // ---- parsePageRef -------------------------------------------------------------------------

    @Test
    fun parsePageRefSplitsPathAndAnchor() {
        val ref = ContractLintSupport.parsePageRef("documentation/foo.md#some-anchor")
        assertEquals("documentation/foo.md", ref.relativePath)
        assertEquals("some-anchor", ref.anchor)
    }

    @Test
    fun parsePageRefRequiresAnAnchor() {
        assertThrows(IllegalArgumentException::class.java) {
            ContractLintSupport.parsePageRef("documentation/foo.md")
        }
    }

    // ---- parse ----------------------------------------------------------------------------

    // Deliberately NOT built with a single outer trimIndent() around interpolated multi-line
    // blocks: Kotlin does not re-indent an interpolated value's own line breaks, so mixing a
    // trimIndent()'d block into a trimIndent()'d template computes the wrong common indent and
    // produces malformed YAML. Each entry block owns its exact indentation from column 0.
    private fun contractYaml(vararg entryBlocks: String): String =
        "version_source: \"gradle.properties#narrativetraceVersion\"\nentries:\n" +
            entryBlocks.joinToString("\n") { it }

    private val validEntryPointEntry = """
        |  - id: entry-point-core
        |    kind: entry-point
        |    registry: maven-central
        |    coordinate: "ai.narrativetrace:narrativetrace-core"
        |    page: "documentation/foo.md#some-anchor"
        |    claim: "narrativetrace-core resolves on Maven Central"
        |    since: "0.1.0"
        |    documented_default: "PRESENT"
        |    probe: "contract-probe/Probe.java"
    """.trimMargin()

    @Test
    fun parseReadsAnEntryPointEntry() {
        val file = tempDir.resolve("contract.yaml")
        file.writeText(contractYaml(validEntryPointEntry))
        val document = ContractLintSupport.parse(file)
        assertEquals("gradle.properties#narrativetraceVersion", document.versionSource)
        assertEquals(1, document.entries.size)
        val entry = document.entries.single()
        assertEquals("entry-point-core", entry.id)
        assertEquals(ContractKind.ENTRY_POINT, entry.kind)
        assertEquals("ai.narrativetrace:narrativetrace-core", entry.coordinate)
        assertEquals("PRESENT", entry.expect)
    }

    @Test
    fun parseAcceptsExpectedEffectAsTheExpectField() {
        val file = tempDir.resolve("contract.yaml")
        file.writeText(
            contractYaml(
                """
              - id: config-shape-example
                kind: config-shape
                page: "documentation/foo.md#some-anchor"
                claim: "an example config shape produces an effect"
                since: "0.1.0"
                expected_effect: "field redacted"
                probe: "contract-probe/Probe.java"
                """.trimIndent()
            )
        )
        val entry = ContractLintSupport.parse(file).entries.single()
        assertEquals("field redacted", entry.expect)
    }

    @Test
    fun parseMissingVersionSourceThrows() {
        val file = tempDir.resolve("contract.yaml")
        file.writeText("entries: []")
        assertThrows(IllegalArgumentException::class.java) { ContractLintSupport.parse(file) }
    }

    @Test
    fun parseEntryMissingExpectFieldThrows() {
        val file = tempDir.resolve("contract.yaml")
        file.writeText(
            contractYaml(
                """
              - id: broken
                kind: probed-default
                page: "documentation/foo.md#some-anchor"
                claim: "something"
                since: "0.1.0"
                probe: "contract-probe/Probe.java"
                """.trimIndent()
            )
        )
        assertThrows(IllegalArgumentException::class.java) { ContractLintSupport.parse(file) }
    }

    @Test
    fun parseUnknownKindThrows() {
        val file = tempDir.resolve("contract.yaml")
        file.writeText(
            contractYaml(
                """
              - id: broken
                kind: not-a-real-kind
                page: "documentation/foo.md#some-anchor"
                claim: "something"
                since: "0.1.0"
                documented_default: "true"
                probe: "contract-probe/Probe.java"
                """.trimIndent()
            )
        )
        assertThrows(IllegalArgumentException::class.java) { ContractLintSupport.parse(file) }
    }

    // ---- lint -------------------------------------------------------------------------------

    private fun entry(
        id: String = "e1",
        kind: ContractKind = ContractKind.PROBED_DEFAULT,
        page: String = "documentation/foo.md#heading",
        claim: String = "claim $id",
        since: String = "0.1.0",
        expect: String = "true",
        probe: String = "probe.java",
        coordinate: String? = null
    ) = ContractEntry(id, kind, page, claim, since, expect, probe, coordinate, null)

    @Test
    fun lintCleanDocumentHasNoProblems() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("# Foo\n## Heading\n")
        val probe = tempDir.resolve("probe.java")
        probe.writeText("// probe")

        val document = ContractDocument("gradle.properties#narrativetraceVersion", listOf(entry()))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.isEmpty(), "expected no problems, got: $problems")
    }

    @Test
    fun lintFlagsDuplicateIds() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument(
            "v", listOf(entry(id = "dup", claim = "a"), entry(id = "dup", claim = "b"))
        )
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("duplicate entry id") })
    }

    @Test
    fun lintFlagsDuplicateClaims() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument(
            "v", listOf(entry(id = "a", claim = "same claim"), entry(id = "b", claim = "same claim"))
        )
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("same claim") })
    }

    @Test
    fun lintFlagsBadSinceFormat() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument("v", listOf(entry(since = "not-a-version")))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("not a real version string") })
    }

    @Test
    fun lintFlagsMissingProbeFile() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        val document = ContractDocument("v", listOf(entry(probe = "does-not-exist.java")))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("does not exist") && it.contains("does-not-exist.java") })
    }

    @Test
    fun lintFlagsMissingPageFile() {
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument("v", listOf(entry(page = "documentation/missing.md#heading")))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("page \"documentation/missing.md\" does not exist") })
    }

    @Test
    fun lintFlagsAnchorNotFoundOnAnExistingPage() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## A Different Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument("v", listOf(entry(page = "documentation/foo.md#heading")))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("anchor \"#heading\" not found") })
    }

    @Test
    fun lintFlagsAnEntryPointWithNoCoordinate() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument("v", listOf(entry(kind = ContractKind.ENTRY_POINT)))
        val problems = ContractLintSupport.lint(tempDir, document, emptySet())
        assertTrue(problems.any { it.contains("entry-point requires \"coordinate\"") })
    }

    @Test
    fun lintFlagsAnUnreleasedMarkerVersionWithNoContractEntry() {
        val page = tempDir.resolve("documentation/foo.md")
        page.parentFile.mkdirs()
        page.writeText("## Heading\n")
        tempDir.resolve("probe.java").writeText("// probe")
        val document = ContractDocument("v", listOf(entry(since = "0.2.2")))
        val problems = ContractLintSupport.lint(tempDir, document, setOf("0.2.2", "0.3.0"))
        assertTrue(problems.any { it.contains("since: \"0.3.0\"") })
        assertFalse(problems.any { it.contains("since: \"0.2.2\"") })
    }

    // ---- headingsWithSinceMarker -----------------------------------------------------------
    // Port of the TS repo's tools/contract-lint.ts `headingsWithSinceMarker` (read-only reference):
    // a heading anchor's slug must survive the tag rewrite (settle-markers.sh, the release
    // publish script), so a since-marker may only ever sit in a section's body.

    @Test
    fun headingsWithSinceMarkerFlagsAHeadingCarryingAnInlineMarker() {
        val docs = tempDir.resolve("documentation")
        docs.mkdirs()
        docs.resolve("guide.md").writeText("### The run has a name *(since 0.1.3, unreleased)*\n")

        val hits = ContractLintSupport.headingsWithSinceMarker(tempDir)

        assertEquals(1, hits.size)
        assertTrue(hits[0].contains("guide.md:1"))
        assertTrue(
            hits[0].contains(
                "since-markers belong in the body: heading anchors must survive the tag rewrite"
            )
        )
    }

    @Test
    fun headingsWithSinceMarkerDoesNotFlagAMarkerInTheSectionBody() {
        val docs = tempDir.resolve("documentation")
        docs.mkdirs()
        docs.resolve("guide.md")
            .writeText("### The run has a name\n\n*(since 0.1.3, unreleased)*\n\nBody text.\n")

        assertTrue(ContractLintSupport.headingsWithSinceMarker(tempDir).isEmpty())
    }

    @Test
    fun headingsWithSinceMarkerFlagsATranslatedMirrorHeading() {
        val esDir = tempDir.resolve("documentation/es")
        esDir.mkdirs()
        esDir.resolve("guia.md")
            .writeText("### La ejecución tiene un nombre *(since 0.1.3, unreleased)*\n")

        val hits = ContractLintSupport.headingsWithSinceMarker(tempDir)

        assertTrue(hits.any { it.contains("es/guia.md:1") })
    }

    @Test
    fun headingsWithSinceMarkerFlagsALlmsTxtHeading() {
        val docs = tempDir.resolve("documentation")
        docs.mkdirs()
        docs.resolve("llms.txt").writeText("## Behaviours *(since 0.1.3, unreleased)*\n")

        val hits = ContractLintSupport.headingsWithSinceMarker(tempDir)

        assertTrue(hits.any { it.contains("llms.txt:1") })
    }

    @Test
    fun headingsWithSinceMarkerFlagsTheRootReadme() {
        tempDir.resolve("README.md").writeText("## Feature *(since 0.1.3, unreleased)*\n")

        val hits = ContractLintSupport.headingsWithSinceMarker(tempDir)

        assertTrue(hits.any { it.contains("README.md:1") })
    }

    @Test
    fun headingsWithSinceMarkerFlagsARootReadmeTranslatedMirror() {
        tempDir.resolve("LEAME.md").writeText(
            "<!-- source: README.md blob 000000000000 | translated: 2026-09-01 | reviewed: - -->\n" +
                "## Funcionalidad *(since 0.1.3, unreleased)*\n"
        )

        val hits = ContractLintSupport.headingsWithSinceMarker(tempDir)

        assertTrue(hits.any { it.contains("LEAME.md:2") })
    }

    @Test
    fun headingsWithSinceMarkerIgnoresARootMarkdownFileThatIsNotAReadmeMirror() {
        tempDir.resolve("CHANGES.md").writeText("## Feature *(since 0.1.3, unreleased)*\n")

        assertTrue(ContractLintSupport.headingsWithSinceMarker(tempDir).isEmpty())
    }

    @Test
    fun headingsWithSinceMarkerReturnsNothingWhenDocumentationDoesNotExist() {
        assertTrue(ContractLintSupport.headingsWithSinceMarker(tempDir).isEmpty())
    }

    // ---- ContractDecisionSupport --------------------------------------------------------------

    @Test
    fun isApplicableWhenSinceIsEarlierThanInstalled() {
        assertTrue(ContractDecisionSupport.isApplicable("0.1.0", "0.2.1"))
    }

    @Test
    fun isApplicableWhenSinceEqualsInstalled() {
        // Ruling 1: exempt only while STRICTLY later than installed — equal still applies.
        assertTrue(ContractDecisionSupport.isApplicable("0.2.1", "0.2.1"))
    }

    @Test
    fun notApplicableWhenSinceIsLaterThanInstalled() {
        assertFalse(ContractDecisionSupport.isApplicable("0.2.2", "0.2.1"))
    }

    @Test
    fun decideHoldsWhenObservedMatchesExpectation() {
        val outcome = ContractDecisionSupport.decide(entry(expect = "true"), "0.2.1", "true")
        assertEquals(ContractVerdict.HOLDS, outcome.verdict)
    }

    @Test
    fun decideSkipsAFutureSinceRegardlessOfObserved() {
        val outcome = ContractDecisionSupport.decide(entry(since = "0.2.2", expect = "true"), "0.2.1", "false")
        assertEquals(ContractVerdict.NOT_APPLICABLE_BEFORE_SINCE, outcome.verdict)
    }

    @Test
    fun decideFailsAndNamesAllFourFactsWhenObservedDiffers() {
        val outcome = ContractDecisionSupport.decide(
            entry(id = "narrativetrace-output", since = "0.2.2", expect = "true", coordinate = "ai.narrativetrace:narrativetrace-core"),
            "0.2.2",
            "false"
        )
        assertEquals(ContractVerdict.FAILS, outcome.verdict)
        assertTrue(outcome.message.contains("narrativetrace-output"))
        assertTrue(outcome.message.contains("\"true\""))
        assertTrue(outcome.message.contains("0.2.2"))
        assertTrue(outcome.message.contains("\"false\""))
    }

    // ---- the four historical instances (docs-vs-published-gate §3) --------------------------
    // Each fixture proves the DECISION LOGIC would have fired: a contract entry shaped like the
    // real defect, paired with the probe result the real defect would have produced, run through
    // the exact `decide()` `contractCheck` uses. These are not re-runs of history (the defects are
    // fixed); they pin the class of bug so a regression of the same shape is caught by this logic
    // again, offline, without a release.

    @Test
    fun row2JavaDocCoordinateThatDoesNotResolveWouldHaveFailed() {
        // "docs cite 0.2.0, Central serves 0.2.1": an entry-point whose coordinate does not
        // actually resolve at the version under test reads as MISSING, never silently PRESENT.
        val row2 = entry(
            id = "entry-point-proxy", kind = ContractKind.ENTRY_POINT, since = "0.1.0",
            expect = "PRESENT", coordinate = "ai.narrativetrace:narrativetrace-proxy"
        )
        val outcome = ContractDecisionSupport.decide(row2, "0.2.0", "MISSING")
        assertEquals(ContractVerdict.FAILS, outcome.verdict)
    }

    @Test
    fun row3PythonOutputDocSaysTrueWheelDefaultsFalseWouldHaveFailed() {
        val row3 = entry(
            id = "output-default", kind = ContractKind.PROBED_DEFAULT, since = "0.1.1", expect = "true"
        )
        val outcome = ContractDecisionSupport.decide(row3, "0.1.1", "false")
        assertEquals(ContractVerdict.FAILS, outcome.verdict)
        assertTrue(outcome.message.contains("documented default \"true\""))
        assertTrue(outcome.message.contains("reads \"false\""))
    }

    @Test
    fun row4DotnetDocCommittedAfterPublishStillOffWouldHaveFailed() {
        // The doc author believed the config was already on; probing the PUBLISHED package
        // directly (never what the commit believed) is what catches it regardless of intent.
        val row4 = entry(
            id = "proxy-options-redaction", kind = ContractKind.PROBED_DEFAULT, since = "0.1.3", expect = "true"
        )
        val outcome = ContractDecisionSupport.decide(row4, "0.1.3", "false")
        assertEquals(ContractVerdict.FAILS, outcome.verdict)
    }

    @Test
    fun row5TypescriptConfigExampleSilentNoOpWouldHaveFailed() {
        // The documented shape, applied to the published package, produces no such effect.
        val row5 = entry(
            id = "trace-object-methods", kind = ContractKind.CONFIG_SHAPE, since = "0.1.1",
            expect = "return value redacted"
        )
        val outcome = ContractDecisionSupport.decide(row5, "0.1.1", "no effect")
        assertEquals(ContractVerdict.FAILS, outcome.verdict)
    }
}
