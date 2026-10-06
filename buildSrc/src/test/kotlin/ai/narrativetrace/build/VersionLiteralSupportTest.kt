/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Random

class VersionLiteralSupportTest {

    @TempDir
    lateinit var repo: File

    private fun write(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun read(relative: String): String = repo.resolve(relative).readText()

    private fun guide(body: String): String {
        write("documentation/guide.md", body)
        return body
    }

    private fun problems(version: String = "0.2.4"): List<String> =
        VersionLiteralSupport.check(repo, version)

    // -------------------------------------------------------------------------------------------
    // sync — every coordinate form
    // -------------------------------------------------------------------------------------------

    @Test
    fun syncRewritesAGradleDependencyCoordinate() {
        guide("    implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("    implementation(\"ai.narrativetrace:narrativetrace-core:0.2.4\")\n", read("documentation/guide.md"))
    }

    @Test
    fun syncRewritesEveryQuotingAndConfigurationName() {
        guide(
            "testImplementation 'ai.narrativetrace:narrativetrace-junit5:0.1.9'\n" +
                "runtimeOnly(\"ai.narrativetrace:narrativetrace-slf4j:0.1.9\")\n" +
                "compileOnly ai.narrativetrace:narrativetrace-api:0.1.9\n"
        )

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals(
            "testImplementation 'ai.narrativetrace:narrativetrace-junit5:0.2.4'\n" +
                "runtimeOnly(\"ai.narrativetrace:narrativetrace-slf4j:0.2.4\")\n" +
                "compileOnly ai.narrativetrace:narrativetrace-api:0.2.4\n",
            read("documentation/guide.md"),
        )
    }

    @Test
    fun syncRewritesTheKotlinPluginCoordinate() {
        guide("    id(\"ai.narrativetrace\") version \"0.1.9\"\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("    id(\"ai.narrativetrace\") version \"0.2.4\"\n", read("documentation/guide.md"))
    }

    @Test
    fun syncRewritesTheGroovyPluginCoordinate() {
        guide("    id 'ai.narrativetrace' version '0.1.9'\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("    id 'ai.narrativetrace' version '0.2.4'\n", read("documentation/guide.md"))
    }

    @Test
    fun syncRewritesBothHalvesOfAMavenCentralPath() {
        guide("https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/0.1.9/narrativetrace-core-0.1.9-sources.jar\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals(
            "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/0.2.4/narrativetrace-core-0.2.4-sources.jar\n",
            read("documentation/guide.md"),
        )
    }

    @Test
    fun syncRewritesATranslatedMirror() {
        write("documentation/es/guia.md", "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("implementation(\"ai.narrativetrace:narrativetrace-core:0.2.4\")\n", read("documentation/es/guia.md"))
    }

    @Test
    fun syncLeavesAThirdPartyCoordinateAlone() {
        guide("runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")\n", read("documentation/guide.md"))
    }

    @Test
    fun syncTreatsANearMissGroupAsThirdParty() {
        guide("implementation(\"ai.narrativetracex:narrativetrace-core:0.1.9\")\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("implementation(\"ai.narrativetracex:narrativetrace-core:0.1.9\")\n", read("documentation/guide.md"))
    }

    @Test
    fun syncReportsEveryFileItRewroteAndNothingElse() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")
        write("documentation/untouched.md", "nothing to see\n")

        val changed = VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals(listOf("documentation/guide.md: install coordinate -> 0.2.4"), changed)
    }

    @Test
    fun syncIsIdempotent() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")

        VersionLiteralSupport.sync(repo, "0.2.4")
        val second = VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals(emptyList<String>(), second)
    }

    @Test
    fun syncKeepsAFileWithoutATrailingNewlineAsItIs() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals("implementation(\"ai.narrativetrace:narrativetrace-core:0.2.4\")", read("documentation/guide.md"))
    }

    @Test
    fun syncRestampsAMirrorWhoseEnglishSourceItRewrote() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")
        write(
            "documentation/es/guia.md",
            "<!-- source: documentation/guide.md blob 000000000000 | translated: 2026-01-01 -->\n" +
                "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n",
        )

        VersionLiteralSupport.sync(repo, "0.2.4")

        val expected = TranslationCheckSupport.gitBlobHash(repo.resolve("documentation/guide.md").readBytes()).take(12)
        assertEquals(
            "<!-- source: documentation/guide.md blob $expected | translated: 2026-01-01 -->",
            read("documentation/es/guia.md").lines().first(),
        )
    }

    @Test
    fun syncLeavesAMirrorOfAnUntouchedSourceStamped() {
        write("documentation/guide.md", "no coordinate here\n")
        val header = "<!-- source: documentation/guide.md blob 000000000000 | translated: 2026-01-01 -->"
        write("documentation/es/guia.md", "$header\nhola\n")

        VersionLiteralSupport.sync(repo, "0.2.4")

        assertEquals(header, read("documentation/es/guia.md").lines().first())
    }

    // -------------------------------------------------------------------------------------------
    // check — coordinates
    // -------------------------------------------------------------------------------------------

    @Test
    fun checkFailsOnACoordinateAtTheWrongVersionNamingFileAndLine() {
        guide("intro\nimplementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")

        assertTrue(problems().single().startsWith("documentation/guide.md:2: NarrativeTrace coordinate pinned to 0.1.9"))
    }

    @Test
    fun checkPassesACoordinateAtTheBuildVersion() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.2.4\")\n")

        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun checkPassesAThirdPartyCoordinate() {
        guide("runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")\n")

        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun checkTreatsANearMissGroupAsThirdParty() {
        guide("implementation(\"ai.narrativetracex:narrativetrace-core:0.1.9\")\n")

        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun checkFailsOnAPluginCoordinateAtTheWrongVersion() {
        guide("    id(\"ai.narrativetrace\") version \"0.1.9\"\n")

        assertEquals(1, problems().size)
    }

    // The narrowed rule, both halves in one page: somebody else's version in prose — a
    // compatibility-table row, an advisory's before/after pair — is a fact about their release and
    // passes, while OUR coordinate at the wrong version is still the one thing reported.
    @Test
    fun checkPassesThirdPartyVersionsInProseAndStillFailsOurOwnCoordinate() {
        guide(
            "| JUnit 5 | 5.11.4 | brought in by narrativetrace-junit5 |\n" +
                "The logback advisory moved 1.5.15 -> 1.5.38.\n" +
                "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n"
        )

        assertTrue(problems().single().startsWith("documentation/guide.md:3: NarrativeTrace coordinate pinned to 0.1.9"))
    }

    // -------------------------------------------------------------------------------------------
    // check — the markers the ruling removed
    // -------------------------------------------------------------------------------------------

    @Test
    fun checkFailsOnASinceMarker() {
        guide("Output is on by default *(since 0.2.3)*.\n")

        assertTrue(problems().single().contains("*(since …)* marker"))
    }

    @Test
    fun checkFailsOnASinceMarkerWrappedAfterSince() {
        guide("Output is on by default *(since\n0.2.3, unreleased)*.\n")

        assertTrue(problems().any { it.startsWith("documentation/guide.md:1:") && it.contains("*(since …)* marker") })
    }

    @Test
    fun checkFailsOnASinceMarkerWrappedAfterTheVersionComma() {
        guide("Output is on by default *(since 0.2.3,\nunreleased)*.\n")

        assertTrue(problems().any { it.startsWith("documentation/guide.md:1:") && it.contains("*(since …)* marker") })
    }

    @Test
    fun checkFailsOnASinceMarkerInsideAFencedBlock() {
        guide("```text\nguide.md:7: *(since 0.2.3)* is version talk\n```\n")

        assertTrue(problems().any { it.contains("*(since …)* marker") })
    }

    // A marker inside a HEADING is the shape `contractLint` used to scan for separately: a marker
    // there is part of the heading's own anchor, so it breaks every reader link into the section
    // as well as talking versions. One rule owns both now — a marker is a violation wherever it
    // sits, heading included.
    @Test
    fun checkFailsOnASinceMarkerInsideAHeading() {
        guide("### The run has a name *(since 0.1.3, unreleased)*\n")

        assertTrue(problems().any { it.contains("*(since …)* marker") })
    }

    @Test
    fun checkFailsOnTheDocsVsPublishedBanner() {
        guide("<!-- docs-vs-published -->\n")

        assertTrue(problems().single().contains("docs-vs-published banner"))
    }

    @Test
    fun checkFailsOnTheCacheAgeComment() {
        guide("something <!-- published-version cache age: 0m -->\n")

        assertTrue(problems().single().contains("published-version cache"))
    }

    // -------------------------------------------------------------------------------------------
    // Scope
    // -------------------------------------------------------------------------------------------

    // The fixture carries a coordinate the rule WOULD flag in a page, so this proves the exemption
    // is real rather than that the file happened to be clean.
    @Test
    fun checkExemptsTheContractDocument() {
        write(
            "documentation/contract.yaml",
            "entries:\n  - claim: \"ai.narrativetrace:narrativetrace-core:0.1.9 resolves\"\n",
        )

        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun checkReadsAnEmptyDocumentationDirectory() {
        repo.resolve("documentation").mkdirs()

        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun checkReadsTheRootReadmeAndItsMirrors() {
        write("README.md", "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")
        write(
            "LEAME.md",
            "<!-- source: README.md blob 000000000000 | translated: 2026-01-01 -->\n" +
                "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.8\")\n",
        )

        assertEquals(listOf("LEAME.md", "README.md"), problems().map { it.substringBefore(':') }.sorted())
    }

    // The stripped directory here is an invented name, not one of this repository's real private
    // roots: the publish script's own reference gate greps every shipped file for those names, and
    // a test fixture citing one would fail that gate for saying it, not for doing anything wrong.
    @Test
    fun checkReadsAShippingModuleReadmeButNotAStrippedOne() {
        write(".publishignore", "private-notes\n")
        write("narrativetrace-examples/README.md", "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.9\")\n")
        write("private-notes/README.md", "implementation(\"ai.narrativetrace:narrativetrace-core:0.1.8\")\n")

        assertEquals(listOf("narrativetrace-examples/README.md"), problems().map { it.substringBefore(':') })
    }

    @Test
    fun checkIgnoresAFileWithoutATrailingNewline() {
        guide("implementation(\"ai.narrativetrace:narrativetrace-core:0.2.4\")")

        assertEquals(emptyList<String>(), problems())
    }

    // -------------------------------------------------------------------------------------------
    // Property: sync then check is clean, for any version, and sync never moves twice — with
    // somebody else's version sitting in the same page's prose, untouched by either half.
    // -------------------------------------------------------------------------------------------

    @Test
    fun syncThenCheckIsCleanAndIdempotentForAnyVersion() {
        val random = Random(20260924L)
        repeat(200) {
            val version = "${random.nextInt(20)}.${random.nextInt(20)}.${random.nextInt(20)}"
            guide(
                "plugins { id(\"ai.narrativetrace\") version \"9.9.9\" }\n" +
                    "implementation(\"ai.narrativetrace:narrativetrace-core:9.9.9\")\n" +
                    "id 'ai.narrativetrace' version '9.9.9'\n" +
                    "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/9.9.9/narrativetrace-core-9.9.9.jar\n" +
                    "Developed and tested against Gradle 8.14.2.\n"
            )

            VersionLiteralSupport.sync(repo, version)
            assertEquals(emptyList<String>(), VersionLiteralSupport.check(repo, version), "version $version")
            assertEquals(emptyList<String>(), VersionLiteralSupport.sync(repo, version), "version $version")
        }
    }
}
