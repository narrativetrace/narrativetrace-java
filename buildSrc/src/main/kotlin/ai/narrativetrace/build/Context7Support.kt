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
 * INTENT: Backs the root `context7Check` task. Context7 (context7.com) indexes this repository's
 * public Markdown and serves code blocks from it to coding agents; the committed root
 * `context7.json` tells it where to look. This keeps three things honest, offline, every commit:
 *
 * - `context7.json` validates against the structural rules of the committed schema copy at
 *   `config/context7/schema.json` (fetched from `https://context7.com/schema/context7.json` —
 *   see the fetch-date file beside it). The vocabulary understood here is exactly what that
 *   schema file uses (`type`, `properties`, `additionalProperties`, `items`, `minLength`,
 *   `maxLength`, `minItems`, `maxItems`, `pattern`, `oneOf`, `required`, `dependencies`) — a
 *   bounded reader, not a general JSON-Schema engine, because that is all the committed schema
 *   ever needs.
 * - `previousVersions` is derived from `git tag --list 'v*' --sort=-v:refname`, never typed by
 *   hand, so a release that forgets to update it fails this gate instead of leaving Context7
 *   blind to an old version.
 * - Every opening code fence in the files Context7 will actually index (`folders` minus
 *   `excludeFolders`/`excludeFiles`, plus root-level Markdown — Context7's own documented
 *   behaviour, read from the live docs, not just this brief) carries a language tag; an untagged
 *   fence indexes as prose.
 *
 * @llmNote `config/context7/schema.json` is a human/agent-reviewed snapshot, refreshed by hand.
 * If context7.com changes the schema, update the snapshot and the validation vocabulary above
 * together — see `documentation/agent-skills.md`'s Context7 paragraph for the refresh step.
 */
object Context7Support {

    private const val CONTEXT7_JSON = "context7.json"
    private const val SCHEMA_PATH = "config/context7/schema.json"

    private val FENCE_LINE = Regex("""^(\s{0,3})(`{3,}|~{3,})\s*(.*)$""")
    private val PARSEABLE_EXTENSIONS = setOf("md", "mdx", "markdown", "rst", "txt", "ipynb")

    /** Every problem across schema validation, the `previousVersions`/tags check and the fence audit. */
    fun check(repoRoot: File): List<String> {
        val document = loadJson(repoRoot.resolve(CONTEXT7_JSON))
        val schema = loadJson(repoRoot.resolve(SCHEMA_PATH))
        return (validateValue(CONTEXT7_JSON, schema, document) +
            previousVersionsProblems(repoRoot, document) +
            fenceProblems(repoRoot, document)).sorted()
    }

    @Suppress("UNCHECKED_CAST")
    fun loadJson(file: File): Map<String, Any?> =
        JsonSlurper().parseText(file.readText()) as Map<String, Any?>

    // ---------------------------------------------------------------------------------------
    // Schema validation — a bounded reader of config/context7/schema.json's own vocabulary.
    // ---------------------------------------------------------------------------------------

    private fun validateValue(path: String, schema: Map<*, *>, value: Any?): List<String> {
        val problems = mutableListOf<String>()
        (schema["required"] as? List<*>)?.let { problems += requiredProblems(path, it, value) }
        when (schema["type"]) {
            "string" -> problems += validateString(path, schema, value)
            "array" -> problems += validateArray(path, schema, value)
            "boolean" -> if (value !is Boolean) problems += "$path: expected a boolean"
            "object" -> problems += validateObject(path, schema, value)
        }
        (schema["oneOf"] as? List<*>)?.let { problems += oneOfProblems(path, it, value) }
        return problems
    }

    private fun requiredProblems(path: String, required: List<*>, value: Any?): List<String> {
        if (value !is Map<*, *>) {
            return listOf("$path: expected an object carrying $required")
        }
        return required.filter { it !in value.keys }.map { "$path: missing required field '$it'" }
    }

    private fun oneOfProblems(path: String, alternatives: List<*>, value: Any?): List<String> {
        val matches = alternatives.count { validateValue(path, it as Map<*, *>, value).isEmpty() }
        return when (matches) {
            1 -> emptyList()
            0 -> listOf("$path: matches none of the allowed shapes")
            else -> listOf("$path: matches more than one of the allowed shapes (expected exactly one)")
        }
    }

    private fun validateObject(path: String, schema: Map<*, *>, value: Any?): List<String> {
        if (value !is Map<*, *>) {
            return listOf("$path: expected an object")
        }
        val properties = (schema["properties"] as? Map<*, *>).orEmpty()
        val additionalAllowed = schema["additionalProperties"] != false
        val problems = mutableListOf<String>()
        if (!additionalAllowed) {
            problems += value.keys.filter { it !in properties.keys }.map { "$path: unknown field '$it'" }
        }
        for ((key, propSchema) in properties) {
            val fieldValue = value[key] ?: continue
            problems += validateValue("$path.$key", propSchema as Map<*, *>, fieldValue)
        }
        problems += dependencyProblems(path, schema, value)
        return problems
    }

    private fun dependencyProblems(path: String, schema: Map<*, *>, value: Map<*, *>): List<String> {
        val dependencies = (schema["dependencies"] as? Map<*, *>).orEmpty()
        val problems = mutableListOf<String>()
        for ((key, required) in dependencies) {
            if (key !in value.keys) continue
            problems += (required as List<*>).filter { it !in value.keys }
                .map { "$path: '$key' requires '$it'" }
        }
        return problems
    }

    private fun validateString(path: String, schema: Map<*, *>, value: Any?): List<String> {
        if (value !is String) {
            return listOf("$path: expected a string")
        }
        val problems = mutableListOf<String>()
        (schema["minLength"] as? Int)?.let { if (value.length < it) problems += "$path: shorter than $it chars" }
        (schema["maxLength"] as? Int)?.let { if (value.length > it) problems += "$path: longer than $it chars" }
        (schema["pattern"] as? String)?.let {
            if (!Regex(it).containsMatchIn(value)) problems += "$path: does not match pattern $it"
        }
        return problems
    }

    private fun validateArray(path: String, schema: Map<*, *>, value: Any?): List<String> {
        if (value !is List<*>) {
            return listOf("$path: expected an array")
        }
        val problems = mutableListOf<String>()
        (schema["maxItems"] as? Int)?.let { if (value.size > it) problems += "$path: more than $it items" }
        (schema["minItems"] as? Int)?.let { if (value.size < it) problems += "$path: fewer than $it items" }
        val itemSchema = schema["items"] as? Map<*, *>
        if (itemSchema != null) {
            value.forEachIndexed { i, item -> problems += validateValue("$path[$i]", itemSchema, item) }
        }
        return problems
    }

    // ---------------------------------------------------------------------------------------
    // previousVersions vs git tags — derived, never typed by hand.
    // ---------------------------------------------------------------------------------------

    /** `git tag --list 'v*' --sort=-v:refname` as lines, newest first. Throws rather than skipping. */
    fun gitTags(repoRoot: File): List<String> {
        val process = ProcessBuilder("git", "tag", "--list", "v*", "--sort=-v:refname")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException(
                "context7Check: `git tag --list` exited $exitCode in $repoRoot:\n$output"
            )
        }
        return output.lines().filter { it.isNotEmpty() }
    }

    /**
     * Whether the `previousVersions` rule has tags to compare against. A checkout with no `v*` tag
     * at all (the publish verify's fresh `git init` tree) has nothing to derive from, so the rule is
     * not checked there and the task says so; the checkout that carries the tags is where it runs.
     */
    fun previousVersionsChecked(repoRoot: File): Boolean = gitTags(repoRoot).isNotEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun previousVersionsProblems(repoRoot: File, document: Map<*, *>): List<String> {
        val tags = gitTags(repoRoot)
        if (tags.isEmpty()) {
            return emptyList()
        }
        val declared = (document["previousVersions"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("tag") as? String }
        if (declared == tags) {
            return emptyList()
        }
        return listOf(
            "context7.json: previousVersions $declared does not match " +
                "`git tag --list 'v*' --sort=-v:refname` $tags"
        )
    }

    // ---------------------------------------------------------------------------------------
    // The fence audit — scoped to exactly what context7.json's own folders/excludeFolders/
    // excludeFiles will have Context7 index, plus its documented "root markdown always
    // included" behaviour (verified against https://context7.com/docs/library-owners).
    // ---------------------------------------------------------------------------------------

    fun indexedMarkdownFiles(repoRoot: File, document: Map<*, *>): List<File> {
        val excludeFolders = stringList(document, "excludeFolders").map { repoRoot.resolve(it).canonicalFile }
        val excludeFiles = stringList(document, "excludeFiles").toSet()
        val folders = stringList(document, "folders").map { repoRoot.resolve(it) }
        val rootFiles = repoRoot.listFiles().orEmpty().filter { it.isFile }
        val folderFiles = folders.flatMap { walkFolder(it, excludeFolders) }
        val privatePatterns = privateRootPatterns(repoRoot)
        return (rootFiles + folderFiles)
            .filter { it.extension.lowercase() in PARSEABLE_EXTENSIONS }
            .filter { it.name !in excludeFiles && !matchesAny(it.name, privatePatterns) }
            .distinctBy { it.canonicalFile }
    }

    private fun stringList(document: Map<*, *>, key: String): List<String> =
        (document[key] as? List<*>).orEmpty().filterIsInstance<String>()

    private fun walkFolder(folder: File, excludeFolders: List<File>): List<File> =
        folder.walkTopDown()
            .onEnter { dir -> dir.canonicalFile !in excludeFolders }
            .filter { it.isFile }
            .toList()

    /**
     * The repository's own private-working-file names, read from `.publishignore` rather than
     * duplicated here by hand: every ROOT-scoped strip pattern (no `/`, not a comment, not a
     * `!`-exception — `CLAUDE.md`, `AGENTS.md`, `TODO.md`, `.agent-*.md`, …). `scripts/publish-
     * public.sh` owns the full strip semantics (recursive globs, `!`-exceptions); this reads only
     * the one category that can shadow a root-level Markdown file Context7 would otherwise always
     * index.
     *
     * @llmNote Missing `.publishignore` (a fork without one) degrades to no exclusions, same as
     * every other gate here that reads an optional convention file.
     */
    private fun privateRootPatterns(repoRoot: File): List<String> {
        val file = repoRoot.resolve(".publishignore")
        if (!file.isFile) {
            return emptyList()
        }
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") && '/' !in it }
    }

    private fun matchesAny(name: String, patterns: List<String>): Boolean {
        val fileSystem = java.nio.file.FileSystems.getDefault()
        val path = fileSystem.getPath(name)
        return patterns.any { fileSystem.getPathMatcher("glob:$it").matches(path) }
    }

    fun fenceProblems(repoRoot: File, document: Map<*, *>): List<String> =
        indexedMarkdownFiles(repoRoot, document)
            .flatMap { file ->
                untaggedFenceLines(file).map { line ->
                    "${relativePath(repoRoot, file)}:$line: code fence has no language tag"
                }
            }
            .sorted()

    private fun untaggedFenceLines(file: File): List<Int> {
        var openMarker: String? = null
        val untagged = mutableListOf<Int>()
        file.readLines().forEachIndexed { index, raw ->
            val match = FENCE_LINE.matchEntire(raw)
            if (match != null) {
                openMarker = trackFence(openMarker, match, index + 1, untagged)
            }
        }
        return untagged
    }

    private fun trackFence(openMarker: String?, match: MatchResult, line: Int, untagged: MutableList<Int>): String? {
        val marker = match.groupValues[2]
        val info = match.groupValues[3].trim()
        if (openMarker == null) {
            if (info.isEmpty()) untagged += line
            return marker
        }
        val closes = marker[0] == openMarker[0] && marker.length >= openMarker.length && info.isEmpty()
        return if (closes) null else openMarker
    }

    private fun relativePath(repoRoot: File, file: File): String =
        repoRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
}
