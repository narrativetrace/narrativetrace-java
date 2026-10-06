/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * INTENT: "No version talk anywhere" is about NARRATIVETRACE's versions (owner ruling 2026-09-24,
 * narrowed by the same owner 2026-09-25). Repository documentation is assumed to describe the code
 * it is committed with, so no README, guide or `llms.txt` says which version it describes or which
 * one is published. The ONE version literal a public document may carry for THIS project is an
 * **install coordinate** — a snippet that cannot be pasted is not a quickstart — and that one is
 * machine-written: [sync] substitutes `gradle.properties#narrativetraceVersion` into every
 * coordinate form, [check] fails the build on a coordinate pinned anywhere else. `snippetSync` is
 * the only writer, `snippetCheck` the reader, exactly as for the embedded code blocks
 * [SnippetSupport] governs.
 *
 * <p>Two rules, and deliberately no third. [check] rejects, each naming file and line:
 * <ul>
 *   <li>a NarrativeTrace coordinate pinned to a version other than the build's;</li>
 *   <li>a `*(since X)*` marker, a `docs-vs-published` banner marker, a cache-age comment — the
 *       machinery the ruling removed, so a reintroduction fails rather than rots.</li>
 * </ul>
 *
 * <p>A version literal that is not one of OURS is not this lint's business, and the third rule
 * that used to make it one — "any other three-part literal", with a reviewed allowlist to buy each
 * hit back — was retired on 2026-09-25. The installation guide's compatibility table, the Maven
 * guide's `<version>` elements, an advisory note's "1.5.15 -> 1.5.38": every one of those is a fact
 * about somebody else's release rather than version talk about NarrativeTrace, so every real hit
 * the rule ever produced needed an exemption, and the allowlist holding them was a second copy of
 * other projects' release notes that went stale on each dependency bump.
 *
 * @llmNote A marker-shaped string inside a fenced block is still a marker. Fenced blocks are where
 * the install snippets live, so exempting them would blind this gate on the one surface that
 * matters most; a page that needs to *discuss* the marker syntax writes a placeholder instead.
 */
object VersionLiteralSupport {

    /** `0.2.4`, `0.2.0-SNAPSHOT` — two or more dotted numbers, an optional qualifier after them. */
    private const val VERSION = """\d+(?:\.\d+)+(?:-[A-Za-z0-9.+]+)?"""

    /** `ai.narrativetrace:narrativetrace-core:0.2.4`, any quoting, any configuration name. */
    private val DEPENDENCY_COORDINATE =
        Regex("""(?<![\w.-])(ai\.narrativetrace:[A-Za-z0-9._-]+:)($VERSION)""")

    /** `id("ai.narrativetrace") version "0.2.4"` and the Groovy `id 'ai.narrativetrace' version '0.2.4'`. */
    private val PLUGIN_COORDINATE =
        Regex("""(id\s*\(?\s*(["'])ai\.narrativetrace\2\s*\)?\s+version\s+(["']))($VERSION)(\3)""")

    /** A Maven Central path: `ai/narrativetrace/<module>/X/<module>-X…`, both halves at once. */
    private val CENTRAL_PATH =
        Regex("""(ai/narrativetrace/([A-Za-z0-9._-]+)/)($VERSION)(/\2-)\3""")

    /** Both hard-wrap shapes survive this: the wrap can only land after `since` or after the
     * version's comma, and `*(since` is intact either way (`scripts/publish-public.sh` tolerated
     * exactly these two before it stopped rewriting markers at all). */
    private val SINCE_MARKER = Regex("""\*\(since\b""")

    private val BANNER_MARKER = Regex("""<!--\s*/?\s*docs-vs-published\s*-->""")
    private val CACHE_AGE_COMMENT = Regex("""published-version cache age""")

    // -------------------------------------------------------------------------------------------
    // sync — the only writer
    // -------------------------------------------------------------------------------------------

    /**
     * Rewrites every NarrativeTrace install coordinate in every governed document to [version],
     * translated mirrors included (a coordinate is language-neutral). A mirror whose ENGLISH source
     * this touched has its line-1 blob hash restamped — the hash only, never the translated or
     * reviewed dates — so a version bump leaves `translationCheck` green in the same run.
     *
     * @return one line per file actually rewritten, sorted; empty when nothing needed it.
     */
    fun sync(repoRoot: File, version: String): List<String> {
        val changed = mutableListOf<String>()
        val rewrittenSources = mutableSetOf<String>()
        for (file in governedFiles(repoRoot)) {
            val original = file.readText()
            val rewritten = rewriteCoordinates(original, version)
            if (rewritten != original) {
                file.writeText(rewritten)
                val relative = relativePath(repoRoot, file)
                rewrittenSources += relative
                changed += "$relative: install coordinate -> $version"
            }
        }
        changed += restampMirrorsOf(repoRoot, rewrittenSources)
        return changed.sorted()
    }

    private fun rewriteCoordinates(text: String, version: String): String =
        text
            .replace(DEPENDENCY_COORDINATE) { it.groupValues[1] + version }
            .replace(PLUGIN_COORDINATE) { it.groupValues[1] + version + it.groupValues[5] }
            .replace(CENTRAL_PATH) { it.groupValues[1] + version + it.groupValues[4] + version }

    /** Restamps the line-1 blob hash of every translation whose source is in [rewrittenSources]. */
    private fun restampMirrorsOf(repoRoot: File, rewrittenSources: Set<String>): List<String> {
        if (rewrittenSources.isEmpty()) {
            return emptyList()
        }
        val restamped = mutableListOf<String>()
        for (mirror in TranslationCheckSupport.translatedFiles(repoRoot)) {
            val lines = mirror.readLines()
            val header = TranslationCheckSupport.parseHeader(lines.firstOrNull().orEmpty()) ?: continue
            if (header.sourcePath !in rewrittenSources) {
                continue
            }
            val source = repoRoot.resolve(header.sourcePath).takeIf { it.isFile } ?: continue
            val fresh = TranslationCheckSupport.gitBlobHash(source.readBytes()).take(12)
            if (fresh == header.blobHashPrefix) {
                continue
            }
            val head = lines[0].replace("blob ${header.blobHashPrefix}", "blob $fresh")
            mirror.writeText((listOf(head) + lines.drop(1)).joinToString("\n") + trailingNewline(mirror))
            restamped += "${relativePath(repoRoot, mirror)}: blob hash restamped"
        }
        return restamped
    }

    private fun trailingNewline(file: File): String =
        if (file.readText().endsWith("\n")) "\n" else ""

    // -------------------------------------------------------------------------------------------
    // check — the gate
    // -------------------------------------------------------------------------------------------

    /** Every problem across every governed document, sorted; empty when no page talks versions. */
    fun check(repoRoot: File, version: String): List<String> {
        val problems = mutableListOf<String>()
        for (file in governedFiles(repoRoot)) {
            val relative = relativePath(repoRoot, file)
            file.readLines().forEachIndexed { index, line ->
                problems += markerProblems(relative, index + 1, line)
                problems += coordinateProblems(relative, index + 1, line, version)
            }
        }
        return problems.sorted()
    }

    private fun markerProblems(relative: String, line: Int, text: String): List<String> {
        val problems = mutableListOf<String>()
        if (SINCE_MARKER.containsMatchIn(text)) {
            problems += "$relative:$line: a *(since …)* marker is version talk — state the " +
                "behaviour in the present tense instead"
        }
        if (BANNER_MARKER.containsMatchIn(text)) {
            problems += "$relative:$line: the docs-vs-published banner was removed — docs describe " +
                "the code they ship with"
        }
        if (CACHE_AGE_COMMENT.containsMatchIn(text)) {
            problems += "$relative:$line: the published-version cache was removed — delete the comment"
        }
        return problems
    }

    private fun coordinateProblems(relative: String, line: Int, text: String, version: String): List<String> =
        coordinateVersions(text)
            .filter { it != version }
            .map {
                "$relative:$line: NarrativeTrace coordinate pinned to $it but the build is at " +
                    "$version — run snippetSync, never hand-type a coordinate"
            }

    private fun coordinateVersions(text: String): List<String> =
        DEPENDENCY_COORDINATE.findAll(text).map { it.groupValues[2] }.toList() +
            PLUGIN_COORDINATE.findAll(text).map { it.groupValues[4] }.toList() +
            CENTRAL_PATH.findAll(text).map { it.groupValues[3] }.toList()

    // -------------------------------------------------------------------------------------------
    // Scope
    // -------------------------------------------------------------------------------------------

    /**
     * The public documents this rule governs: the root README and its language mirrors, every
     * `*.md` and `llms.txt` anywhere under `documentation/` (translated mirrors included — the
     * literal is language-neutral), and the `README.md` of every module that ships.
     *
     * <p>`documentation/contract.yaml` is outside the scan by construction — it is machine-read
     * data for `contractLint` and `contract-probe`, not prose a reader ever sees, and only Markdown
     * and `llms.txt` are walked.
     */
    fun governedFiles(repoRoot: File): List<File> =
        (rootReadmes(repoRoot) + documentationPages(repoRoot) + moduleReadmes(repoRoot))
            .distinctBy { it.canonicalFile }
            .sortedBy { it.path }

    private fun rootReadmes(repoRoot: File): List<File> =
        repoRoot.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "md" }
            .filter { it.name == "README.md" || isReadmeMirror(it) }

    private fun isReadmeMirror(file: File): Boolean {
        val firstLine = file.useLines { it.firstOrNull() } ?: return false
        return TranslationCheckSupport.parseHeader(firstLine)?.sourcePath == "README.md"
    }

    private fun documentationPages(repoRoot: File): List<File> {
        val documentation = repoRoot.resolve("documentation")
        if (!documentation.isDirectory) {
            return emptyList()
        }
        return documentation.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.name == "llms.txt") }
            .toList()
    }

    /** One `README.md` per top-level module directory — skipping build output, dot-directories and
     * every directory `.publishignore` strips whole, since a private page is not a public document. */
    private fun moduleReadmes(repoRoot: File): List<File> {
        val stripped = strippedDirectories(repoRoot)
        return repoRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
            .filterNot { it.name in stripped }
            .map { it.resolve("README.md") }
            .filter { it.isFile }
    }

    /** The plain directory names `.publishignore` strips — a bare name, no slash, no glob. */
    private fun strippedDirectories(repoRoot: File): Set<String> {
        val file = repoRoot.resolve(".publishignore")
        if (!file.isFile) {
            return emptySet()
        }
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filterNot { it.contains('/') || it.contains('*') || it.contains('!') || it.contains('.') }
            .toSet()
    }

    private fun relativePath(repoRoot: File, file: File): String =
        repoRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
}
