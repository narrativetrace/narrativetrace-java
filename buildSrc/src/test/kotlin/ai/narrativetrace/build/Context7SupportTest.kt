/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class Context7SupportTest {

    @TempDir
    lateinit var repo: File

    private val minimalSchema =
        """
        {
          "type": "object",
          "properties": {
            "projectTitle": {"type": "string", "minLength": 1, "maxLength": 100},
            "description": {"type": "string", "minLength": 10, "maxLength": 500},
            "folders": {"type": "array", "maxItems": 50, "items": {"type": "string", "minLength": 1, "maxLength": 255}},
            "excludeFolders": {"type": "array", "maxItems": 50, "items": {"type": "string", "minLength": 1, "maxLength": 255}},
            "excludeFiles": {"type": "array", "maxItems": 100, "items": {"type": "string", "minLength": 1, "maxLength": 255, "pattern": "^[^/]+$"}},
            "rules": {"type": "array", "maxItems": 50, "items": {"type": "string", "minLength": 1, "maxLength": 255}},
            "previousVersions": {
              "type": "array",
              "maxItems": 20,
              "items": {
                "oneOf": [
                  {"type": "string", "minLength": 1, "maxLength": 50},
                  {
                    "type": "object",
                    "properties": {
                      "tag": {"type": "string", "minLength": 1, "maxLength": 50},
                      "branch": {"type": "string", "minLength": 1, "maxLength": 50}
                    },
                    "oneOf": [{"required": ["tag"]}, {"required": ["branch"]}],
                    "additionalProperties": false
                  }
                ]
              }
            },
            "url": {"type": "string", "minLength": 1, "maxLength": 500},
            "public_key": {"type": "string", "minLength": 1, "maxLength": 100}
          },
          "dependencies": {"url": ["public_key"], "public_key": ["url"]},
          "additionalProperties": false
        }
        """.trimIndent()

    private val validDocument =
        """
        {
          "projectTitle": "Example Library",
          "description": "Example library description long enough to pass.",
          "folders": ["documentation"],
          "excludeFolders": ["documentation/es"],
          "excludeFiles": ["SECURITY.md"],
          "rules": ["Use the one-line setup."],
          "previousVersions": [{"tag": "v0.1.0"}]
        }
        """.trimIndent()

    private fun writeSchema(text: String = minimalSchema) {
        val file = repo.resolve("config/context7/schema.json")
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun writeDocument(text: String) {
        repo.resolve("context7.json").writeText(text)
    }

    private fun writeDoc(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args).directory(repo).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }

    private fun initRepository() {
        git("init", "--quiet")
        git("config", "user.email", "build@example.invalid")
        git("config", "user.name", "Build")
        git("config", "commit.gpgsign", "false")
    }

    private fun tagCommit(tag: String) {
        repo.resolve("marker-$tag.txt").writeText(tag)
        git("add", "marker-$tag.txt")
        git("commit", "--quiet", "-m", tag)
        git("tag", tag)
    }

    // --------------------------------------------------------------- the whole gate, green

    @Test
    fun `check is clean for a well-formed document, matching tags and tagged fences`() {
        writeSchema()
        writeDocument(validDocument)
        initRepository()
        tagCommit("v0.1.0")
        writeDoc("documentation/guide.md", "# Guide\n\n```text\nhello\n```\n")

        assertEquals(emptyList<String>(), Context7Support.check(repo))
    }

    // --------------------------------------------------------------- schema validation

    @Test
    fun `rejects an unknown top-level field`() {
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "bogus": true}"""
        )
        initRepository()

        val problems = Context7Support.check(repo)

        assertTrue(problems.any { it.contains("unknown field 'bogus'") }, problems.toString())
    }

    @Test
    fun `rejects a description shorter than the schema minimum`() {
        writeSchema()
        writeDocument("""{"projectTitle": "x", "description": "short"}""")
        initRepository()

        val problems = Context7Support.check(repo)

        assertTrue(problems.any { it.contains("description") && it.contains("shorter than 10") }, problems.toString())
    }

    @Test
    fun `rejects an excludeFiles entry that carries a path separator`() {
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "excludeFiles": ["a/b.md"]}"""
        )
        initRepository()

        val problems = Context7Support.check(repo)

        assertTrue(problems.any { it.contains("excludeFiles[0]") && it.contains("pattern") }, problems.toString())
    }

    @Test
    fun `rejects a previousVersions entry with neither tag nor branch`() {
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "previousVersions": [{}]}"""
        )
        initRepository()

        val problems = Context7Support.check(repo)

        assertTrue(problems.any { it.contains("previousVersions[0]") }, problems.toString())
    }

    @Test
    fun `rejects a previousVersions entry carrying both tag and branch`() {
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "previousVersions": [{"tag": "v1.0.0", "branch": "main"}]}"""
        )
        initRepository()

        val problems = Context7Support.check(repo)

        assertTrue(problems.any { it.contains("previousVersions[0]") }, problems.toString())
    }

    // --------------------------------------------------------------- previousVersions vs git tags

    @Test
    fun `a checkout with no version tags has nothing to compare previousVersions against`() {
        // The publish verify stages the snapshot into a fresh `git init` tree with no tags while
        // context7.json keeps the real list; the rule has nothing to derive from there and the
        // private checkout, which has the tags, is where it runs (family convention: TS, .NET).
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "previousVersions": [{"tag": "v0.1.0"}]}"""
        )
        initRepository()
        git("commit", "--quiet", "--allow-empty", "-m", "verify snapshot")

        val problems = Context7Support.check(repo)

        assertTrue(problems.none { it.contains("previousVersions") }, problems.toString())
    }

    @Test
    fun `previousVersions must equal the git tags, newest first`() {
        writeSchema()
        writeDocument(
            """{"projectTitle": "x", "description": "0123456789", "previousVersions": [{"tag": "v9.9.9"}]}"""
        )
        initRepository()
        tagCommit("v0.1.0")
        tagCommit("v0.2.0")

        val problems = Context7Support.check(repo)

        assertTrue(
            problems.any { it.contains("previousVersions") && it.contains("git tag") },
            problems.toString(),
        )
    }

    @Test
    fun `gitTags throws rather than silently reporting no tags`() {
        // No git repository at all: the gate must refuse rather than report an empty tag list.
        val failure = assertThrows(IllegalStateException::class.java) {
            Context7Support.gitTags(repo)
        }

        assertTrue(failure.message!!.contains("git tag"), failure.message)
    }

    // --------------------------------------------------------------- the fence audit

    @Test
    fun `fenceProblems flags an opening fence with no language tag`() {
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc("documentation/guide.md", "text before\n\n```\nbare output\n```\n")

        val problems = Context7Support.fenceProblems(repo, document)

        assertEquals(listOf("documentation/guide.md:3: code fence has no language tag"), problems)
    }

    @Test
    fun `fenceProblems accepts a fence that carries a language tag`() {
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc("documentation/guide.md", "```bash\necho hi\n```\n")

        assertEquals(emptyList<String>(), Context7Support.fenceProblems(repo, document))
    }

    @Test
    fun `fenceProblems treats a shorter marker inside a longer fence as literal content`() {
        // A 4-backtick fence containing a literal 3-backtick line on its own must not close early
        // (CommonMark requires the closing run to be at least as long as the opening one), and
        // that literal line is content, not a second opening fence that would need its own tag.
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc(
            "documentation/guide.md",
            "````text\nsome content\n```\nmore fenced stuff\n````\n",
        )

        assertEquals(emptyList<String>(), Context7Support.fenceProblems(repo, document))
    }

    @Test
    fun `fenceProblems never closes a backtick fence with a tilde line`() {
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc(
            "documentation/guide.md",
            "```bash\n~~~ not a close\n```\n",
        )

        assertEquals(emptyList<String>(), Context7Support.fenceProblems(repo, document))
    }

    // --------------------------------------------------------------- indexedMarkdownFiles scoping

    @Test
    fun `indexedMarkdownFiles excludes a translated-mirror folder named in excludeFolders`() {
        val document = mapOf("folders" to listOf("documentation"), "excludeFolders" to listOf("documentation/es"))
        writeDoc("documentation/guide.md", "kept")
        writeDoc("documentation/es/guia.md", "excluded")

        val names = Context7Support.indexedMarkdownFiles(repo, document).map { it.name }

        assertEquals(listOf("guide.md"), names)
    }

    @Test
    fun `indexedMarkdownFiles excludes a bare filename anywhere under the scanned folders`() {
        val document = mapOf("folders" to listOf("documentation"), "excludeFiles" to listOf("SECURITY.md"))
        writeDoc("documentation/guide.md", "kept")
        writeDoc("documentation/nested/SECURITY.md", "excluded")

        val names = Context7Support.indexedMarkdownFiles(repo, document).map { it.name }

        assertEquals(listOf("guide.md"), names)
    }

    @Test
    fun `indexedMarkdownFiles always includes root markdown except what publishignore strips`() {
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc(".publishignore", "CLAUDE.md\nAGENTS.md\n.agent-*.md\n")
        writeDoc("README.md", "public")
        writeDoc("CLAUDE.md", "private")
        writeDoc("AGENTS.md", "private")
        writeDoc(".agent-progress-2026-01-01.md", "private working record")

        val names = Context7Support.indexedMarkdownFiles(repo, document).map { it.name }.toSet()

        assertEquals(setOf("README.md"), names)
    }

    @Test
    fun `indexedMarkdownFiles excludes nothing extra when publishignore is absent`() {
        val document = mapOf("folders" to listOf("documentation"))
        writeDoc("README.md", "public")

        val names = Context7Support.indexedMarkdownFiles(repo, document).map { it.name }.toSet()

        assertEquals(setOf("README.md"), names)
    }
}
