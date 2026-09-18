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

/**
 * INTENT: Backs the root `deepFixtureBudget` task — a per-commit lint mirroring the TS repo's
 * `tools/deep-fixture-budget.ts` and the Python repo's `scripts/deep_fixture_budget.py` (read-only
 * references, not shared code) for the Java source tree: release retrospective rule 3 says
 * wall-clock/GC/scheduler are never test inputs, so a test whose legitimate cost scales with a
 * large chain/tree fixture must declare its own budget rather than trust an implicit default. The
 * Java idiom differs from those two ports' (no vitest third argument, no `pytest.mark.timeout`) —
 * it is JUnit5's own `@Timeout`, already used this way in `EventTrailTest`.
 *
 * Detects, inside every `@Test`/`@ParameterizedTest`/`@RepeatedTest` method under a scanned
 * module's `src/test/java`, either shape this suite's own deep fixtures are built in (see
 * `ValueRendererDepthTest`, `TreeWalkTest`, `TraceTreeBuilderTest`): a call to a chain/tree/nest
 * builder passed a literal depth of [THRESHOLD] or more (`chainOfRecords(10_000)`,
 * `chainEvents(depth)` once `depth` traces to such a literal), or a local variable named for depth
 * or size assigned such a literal directly (`var depth = 5_000;`). Only ever reasons about
 * [codeOnly] text — comments and string/text-block literals are blanked out first, so a fixture-
 * shaped number or word mentioned in prose can never trigger a false hit.
 */
object DeepFixtureBudgetSupport {

    /** Below this, a fixture is not "deep" enough to need its own wall-clock budget — chosen to
     * sit under this suite's smallest real deep-fixture literal (5,000, `TreeWalkTest`/
     * `TraceTreeBuilderTest`) and cover the 10,000 `ValueRendererDepthTest` itself uses. */
    const val THRESHOLD = 5_000

    private val TEST_METHOD_ANNOTATION =
        Regex("""@(?:org\.junit\.jupiter\.api\.)?(?:Test|ParameterizedTest|RepeatedTest)\b""")
    private val TIMEOUT_ANNOTATION = Regex("""@(?:org\.junit\.jupiter\.api\.)?Timeout\b""")
    private val METHOD_SIGNATURE =
        Regex("""\b(?:void|[\w<>\[\],. ?]+)\s+(\w+)\s*\([^)]*\)[^{;]*\{""")
    private val BUILDER_CALL =
        Regex("""(?i)\b(\w*(?:chain|nest)\w*)\s*\(""")
    private val DEPTH_VAR_ASSIGN =
        Regex("""(?i)\b(?:var|int|long|final\s+var|final\s+int|final\s+long)\s+(\w*(?:depth|size)\w*)\s*=\s*(\d[\d_]*)\s*[;,)]""")
    private val LARGE_LITERAL = Regex("""\b\d[\d_]*\b""")

    /** One `@Test`-family method that builds a fixture at or beyond [THRESHOLD]. `file` is
     * repo-relative POSIX; `hasTimeout` says whether the method (or its enclosing class) already
     * declares `@Timeout`. */
    data class Hit(val file: String, val method: String, val line: Int, val hasTimeout: Boolean)

    /** A named, reasoned exception — one test by name, never a whole file (a file can hold both a
     * flagged and an excused test), mirroring the TS repo's own allowlist entry shape. */
    data class AllowlistEntry(val file: String, val method: String, val reason: String)

    data class LintResult(
        val violations: List<Hit>,
        val staleAllowlistEntries: List<AllowlistEntry>,
    )

    /** Every `.java` file under each `<moduleName>/src/test/java` in [moduleNames], sorted —
     * mirrors [CommentHygieneSupport.moduleSourceFiles], `src/test` rather than `src/main`. */
    fun moduleTestFiles(repoRoot: File, moduleNames: Collection<String>): List<File> {
        val files = mutableListOf<File>()
        for (name in moduleNames) {
            val src = File(repoRoot, "$name/src/test/java")
            if (src.isDirectory) {
                files += src.walkTopDown().filter { it.isFile && it.extension == "java" }
            }
        }
        return files.sortedBy { it.path }
    }

    /** [content] with every line comment, block comment, and `"…"`/`"""…"""` literal blanked to
     * spaces (line breaks kept, for accurate line numbers) — real code structure survives at the
     * same offsets, but a fixture-shaped number or word quoted as prose or test data never is. */
    fun codeOnly(content: String): String {
        val out = StringBuilder(content.length)
        var i = 0
        while (i < content.length) {
            val skippedTo = skipNonCode(content, i)
            if (skippedTo != i) {
                for (j in i until skippedTo) out.append(if (content[j] == '\n') '\n' else ' ')
                i = skippedTo
                continue
            }
            out.append(content[i])
            i++
        }
        return out.toString()
    }

    private fun skipNonCode(source: String, i: Int): Int {
        if (source.startsWith("//", i)) {
            val nl = source.indexOf('\n', i)
            return if (nl == -1) source.length else nl
        }
        if (source.startsWith("/*", i)) {
            val end = source.indexOf("*/", i + 2)
            return if (end == -1) source.length else end + 2
        }
        if (source.startsWith("\"\"\"", i)) {
            val end = source.indexOf("\"\"\"", i + 3)
            return if (end == -1) source.length else end + 3
        }
        if (source[i] == '"' || source[i] == '\'') return skipQuoted(source, i, source[i])
        return i
    }

    private fun skipQuoted(source: String, i: Int, quote: Char): Int {
        var j = i + 1
        while (j < source.length && source[j] != quote && source[j] != '\n') {
            j += if (source[j] == '\\') 2 else 1
        }
        return (j + 1).coerceAtMost(source.length)
    }

    /** Index just past the `{` matching `source[openBrace]`, skipping nested braces inside
     * [codeOnly]-blanked spans (the caller passes already-blanked text). -1 if unmatched. */
    private fun matchingBrace(source: String, openBrace: Int): Int {
        var depth = 0
        var i = openBrace
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun lineOf(source: String, index: Int): Int {
        var line = 1
        for (i in 0 until index) if (source[i] == '\n') line++
        return line
    }

    /** True if a chain/tree/nest builder call inside [bodyCode] is passed a literal argument at or
     * beyond [THRESHOLD] — the call's own argument span (its matching parens), not the whole body,
     * so an unrelated large literal elsewhere in the method never counts. */
    private fun hasLargeBuilderCall(bodyCode: String): Boolean {
        for (m in BUILDER_CALL.findAll(bodyCode)) {
            val openParen = m.range.last
            if (openParen >= bodyCode.length || bodyCode[openParen] != '(') continue
            val close = matchingParen(bodyCode, openParen)
            if (close == -1) continue
            val args = bodyCode.substring(openParen + 1, close)
            if (LARGE_LITERAL.findAll(args).any { largeEnough(it.value) }) return true
        }
        return false
    }

    private fun matchingParen(source: String, openParen: Int): Int {
        var depth = 0
        var i = openParen
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** True if a local variable named for depth/size is assigned a literal at or beyond
     * [THRESHOLD] directly (`var depth = 5_000;`) — the idiom `TreeWalkTest`/`TraceTreeBuilderTest`
     * use instead of an inline builder-call literal. */
    private fun hasLargeDepthVariable(bodyCode: String): Boolean =
        DEPTH_VAR_ASSIGN.findAll(bodyCode).any { largeEnough(it.groupValues[2]) }

    private fun largeEnough(literal: String): Boolean =
        literal.replace("_", "").toLongOrNull()?.let { it >= THRESHOLD } ?: false

    /** True if a fixture at or beyond [THRESHOLD] is built directly in [bodyCode] (already
     * [codeOnly]) — a known builder call or a depth/size variable assignment. */
    fun isDeepFixtureBody(bodyCode: String): Boolean =
        hasLargeBuilderCall(bodyCode) || hasLargeDepthVariable(bodyCode)

    /** Every `@Test`-family method in [file]'s content that builds a fixture at or beyond
     * [THRESHOLD], repo-relative to [repoRoot] (POSIX). A class-level `@Timeout` (declared on the
     * class itself, before its first member) budgets every method in the file — JUnit5 applies it
     * that way, so this scanner does too. */
    fun findHits(repoRoot: File, file: File): List<Hit> {
        val relative = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
        val raw = file.readText()
        val code = codeOnly(raw)
        val classHasTimeout = classLevelTimeout(code)
        val hits = mutableListOf<Hit>()
        for (m in TEST_METHOD_ANNOTATION.findAll(code)) {
            val sigMatch = METHOD_SIGNATURE.find(code, m.range.last)
            if (sigMatch == null || sigMatch.range.first - m.range.last > 400) continue
            val openBrace = sigMatch.range.last
            val closeBrace = matchingBrace(code, openBrace)
            if (closeBrace == -1) continue
            val body = code.substring(openBrace, closeBrace + 1)
            if (!isDeepFixtureBody(body)) continue
            val methodName = sigMatch.groupValues[1]
            val hasOwnTimeout = TIMEOUT_ANNOTATION.containsMatchIn(
                code.substring(annotationBlockStart(code, m.range.first), sigMatch.range.first),
            )
            hits += Hit(relative, methodName, lineOf(code, m.range.first), classHasTimeout || hasOwnTimeout)
        }
        return hits
    }

    /** Start of the contiguous run of annotations (and blank/whitespace lines) immediately
     * preceding [testAnnotationStart] — as far back as the previous `}`/`;`, so `@Timeout` on a
     * DIFFERENT, earlier method is never attributed to this one. */
    private fun annotationBlockStart(code: String, testAnnotationStart: Int): Int {
        var i = testAnnotationStart - 1
        while (i >= 0 && code[i] != '}' && code[i] != ';') i--
        return i + 1
    }

    /** True if `@Timeout` annotates the file's own top-level `class`/`record` declaration, before
     * any member — JUnit5 applies a class-level `@Timeout` to every test method in it. */
    private fun classLevelTimeout(code: String): Boolean {
        val classDecl = Regex("""\bclass\s+\w+""").find(code) ?: return false
        return TIMEOUT_ANNOTATION.containsMatchIn(code.substring(0, classDecl.range.first))
    }

    /** Parses the allowlist JSON — a flat array of `{file, method, reason}` objects, mirroring the
     * TS repo's `deep-fixture-budget-allowlist.json` (per-test, not per-file, since one file can
     * hold both a flagged and an excused test). Missing file reads as the empty allowlist. */
    fun readAllowlist(file: File): List<AllowlistEntry> {
        if (!file.isFile) return emptyList()
        val text = file.readText().trim()
        if (text.isEmpty() || text == "[]") return emptyList()
        @Suppress("UNCHECKED_CAST")
        val parsed = JsonSlurper().parseText(text) as List<Map<String, Any?>>
        return parsed.map {
            AllowlistEntry(
                file = it["file"].toString(),
                method = it["method"].toString(),
                reason = it["reason"].toString(),
            )
        }
    }

    private fun key(file: String, method: String) = "$file#$method"

    /** Excuses exactly the `(file, method)` pairs named in [allowlist] — a hit with no `@Timeout`
     * outside the allowlist is a violation; an allowlist entry naming a pair no longer detected as
     * a deep-fixture hit is stale, mirroring [CommentHygieneSupport.lint]'s per-file idiom at
     * per-test grain. */
    fun lint(hits: List<Hit>, allowlist: List<AllowlistEntry>): LintResult {
        val allowedKeys = allowlist.map { key(it.file, it.method) }.toSet()
        val hitKeys = hits.map { key(it.file, it.method) }.toSet()
        return LintResult(
            violations = hits.filter { !it.hasTimeout && key(it.file, it.method) !in allowedKeys },
            staleAllowlistEntries = allowlist.filter { key(it.file, it.method) !in hitKeys },
        )
    }
}
