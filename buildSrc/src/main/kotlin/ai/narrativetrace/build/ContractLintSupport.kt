/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File
import org.yaml.snakeyaml.Yaml

/** The four claim shapes `documentation/contract.yaml` can make (docs-vs-published-gate §2). */
enum class ContractKind {
    ENTRY_POINT,
    REFLECTABLE_DEFAULT,
    PROBED_DEFAULT,
    CONFIG_SHAPE;

    companion object {
        private val BY_YAML = mapOf(
            "entry-point" to ENTRY_POINT,
            "reflectable-default" to REFLECTABLE_DEFAULT,
            "probed-default" to PROBED_DEFAULT,
            "config-shape" to CONFIG_SHAPE
        )

        fun fromYaml(raw: String): ContractKind =
            BY_YAML[raw] ?: throw IllegalArgumentException(
                "unknown kind \"$raw\" — must be one of ${BY_YAML.keys.sorted()}"
            )
    }
}

/**
 * One `documentation/contract.yaml` entry. `expect` is the single observed string a probe must
 * produce for the claim to hold — the YAML spells it `documented_default` (reflectable-default,
 * probed-default) or `expected_effect` (config-shape, docs-vs-published-gate §2's own field
 * names); both land here as one field because every probe this repo runs already reduces to "one
 * stdout line, string-compared" (see `contract-probe/`'s `ContractRunner`) regardless of which
 * document field named it. `coordinate`/`registry` apply to `ENTRY_POINT` only; `probe` names the
 * source file (relative to the repo root) implementing the check — required for every kind so a
 * reader always finds the code proving the claim next to the claim itself.
 */
data class ContractEntry(
    val id: String,
    val kind: ContractKind,
    val page: String,
    val claim: String,
    val since: String,
    val expect: String,
    val probe: String,
    val coordinate: String? = null,
    val registry: String? = null
)

data class ContractDocument(val versionSource: String, val entries: List<ContractEntry>)

/** One page-anchor pointer, split for validation (`readAnchors` builds the target's real set). */
data class ContractPageRef(val relativePath: String, val anchor: String)

/**
 * INTENT: Backs the root `contractLint` task (docs-vs-published-gate §2/§5.1) — everything about
 * `documentation/contract.yaml` a per-commit gate can check WITHOUT the network: the schema
 * parses, every `since` is a real version string, no two entries make the same claim, every
 * `page#anchor` pointer resolves to a heading that actually exists, and every `*(since X,
 * unreleased)*` marker in the English docs (part (a)) is backed by at least one contract entry at
 * that version — the mechanical link between (a) and (c) the gate's design note calls for. What
 * this class deliberately does NOT do: run a probe, touch the registry, or decide holds/fails/
 * not-applicable-before-since for an actual probe result — that decision lives in
 * [ContractDecisionSupport], exercised nightly by `contract-probe/` and, standalone, by
 * `contractLint`'s own fixture tests for the four historical instances (docs-vs-published-gate
 * §3) so the decision logic is provable without a release.
 */
object ContractLintSupport {

    private val SINCE_PATTERN = Regex("""^\d+\.\d+\.\d+$""")
    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""")
    private val HEADING_SINCE = Regex("""^#{1,6}\s.*\(since """)

    /** The GitHub-flavoured-Markdown heading slug: lowercase, strip anything but
     * `[a-z0-9 _-]`, then turn spaces into hyphens. Deliberately does not collapse repeated
     * hyphens/spaces (a heading with an em dash or a slash between two words legitimately
     * slugs to a double hyphen) — matching the algorithm GitHub's own renderer uses, so an
     * anchor validated here is also the one a reader's click actually lands on. */
    fun slugify(heading: String): String {
        val lower = heading.lowercase()
        val kept = buildString {
            for (ch in lower) {
                if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_') append(ch)
            }
        }
        return kept.replace(' ', '-')
    }

    /**
     * Every anchor slug the given Markdown file's headings produce, in document order, with
     * GitHub's own disambiguation for a repeated slug (`foo`, `foo-1`, `foo-2`, …).
     */
    fun headingAnchors(markdown: File): Set<String> {
        val seen = mutableMapOf<String, Int>()
        val anchors = mutableSetOf<String>()
        for (line in markdown.readLines()) {
            val match = HEADING.find(line) ?: continue
            val base = slugify(match.groupValues[2])
            val count = seen.getOrDefault(base, 0)
            seen[base] = count + 1
            anchors += if (count == 0) base else "$base-$count"
        }
        return anchors
    }

    /**
     * Every heading line, across every Markdown file and `llms.txt` anywhere under `documentation/`
     * and the root README's language mirrors, that carries an inline `*(since X.Y.Z...)*` marker —
     * ported from the TS repo's `tools/contract-lint.ts` `headingsWithSinceMarker` (read-only
     * reference, not shared code). A heading's GitHub-rendered anchor slug is exactly the text
     * [headingAnchors] above computes from it; a since-marker's own tag rewrite (settle-markers.sh,
     * the release publish script) can later shorten or drop the parenthetical, and that mutates the
     * slug — any reader link into that anchor breaks the instant a release settles. Keeping the
     * marker in the section's body, never the heading itself, is the only shape immune to that.
     * One entry per hit, `"<relative path>:<line>: <reason>"`, sorted; empty when the tree is clean.
     */
    fun headingsWithSinceMarker(repoRoot: File): List<String> {
        val hits = mutableListOf<String>()
        for (file in sinceMarkerHeadingScanScope(repoRoot)) {
            file.readLines().forEachIndexed { index, line ->
                if (HEADING_SINCE.containsMatchIn(line)) {
                    val relative = file.relativeTo(repoRoot).path
                    hits += "$relative:${index + 1}: since-markers belong in the body: heading " +
                        "anchors must survive the tag rewrite"
                }
            }
        }
        return hits.sorted()
    }

    /**
     * Every Markdown file and `llms.txt` anywhere under `documentation/` (every language —
     * translated mirrors live under `documentation/<lang>/` and are in scope too), plus the root README and
     * its own language mirrors: `README.md` itself, and any other root-level `*.md` file whose
     * line-1 translation header ([TranslationCheckSupport.parseHeader]) names `README.md` as its
     * source — the same header-driven "is this a translation of X" test `TranslationCheckSupport`
     * already uses, so this never keeps a second, independent list of root README mirror filenames.
     */
    private fun sinceMarkerHeadingScanScope(repoRoot: File): List<File> {
        val docsDir = repoRoot.resolve("documentation")
        val docs = if (docsDir.isDirectory) {
            docsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "md" || it.name == "llms.txt") }
                .toList()
        } else {
            emptyList()
        }
        val readmeMirrors = repoRoot.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "md" && (it.name == "README.md" || isReadmeMirror(it)) }
        return docs + readmeMirrors
    }

    private fun isReadmeMirror(file: File): Boolean {
        val firstLine = file.useLines { it.firstOrNull() } ?: return false
        return TranslationCheckSupport.parseHeader(firstLine)?.sourcePath == "README.md"
    }

    /** Splits `"documentation/foo.md#some-anchor"` into path and anchor; throws on a pointer with
     * no `#anchor` half — a contract entry is always about one specific claim, never a whole page. */
    fun parsePageRef(page: String): ContractPageRef {
        val hashIndex = page.indexOf('#')
        require(hashIndex > 0 && hashIndex < page.length - 1) {
            "\"$page\" — a contract entry's page must be \"<path>#<anchor>\""
        }
        return ContractPageRef(page.substring(0, hashIndex), page.substring(hashIndex + 1))
    }

    /**
     * Parses `documentation/contract.yaml`. Throws (never returns a partial document) on anything
     * the schema does not allow — a malformed contract must fail loud, the same "init: fail fast"
     * idiom as [DuplicationCheckSupport.readExemptions]'s malformed-pair guard.
     */
    @Suppress("UNCHECKED_CAST")
    fun parse(file: File): ContractDocument {
        val root = Yaml().load<Map<String, Any>>(file.readText())
            ?: throw IllegalArgumentException("${file.path}: empty document")
        val versionSource = root["version_source"] as? String
            ?: throw IllegalArgumentException("${file.path}: missing \"version_source\"")
        val rawEntries = root["entries"] as? List<Map<String, Any>>
            ?: throw IllegalArgumentException("${file.path}: missing \"entries\" list")
        val entries = rawEntries.map { raw -> parseEntry(file, raw) }
        return ContractDocument(versionSource, entries)
    }

    private fun parseEntry(file: File, raw: Map<String, Any>): ContractEntry {
        fun field(name: String): String =
            (raw[name] as? String)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("${file.path}: entry missing \"$name\": $raw")

        val id = field("id")
        val kind = ContractKind.fromYaml(field("kind"))
        val since = field("since")
        val expect = (raw["documented_default"] as? String) ?: (raw["expected_effect"] as? String)
            ?: throw IllegalArgumentException(
                "${file.path}: entry \"$id\" needs \"documented_default\" or \"expected_effect\""
            )
        if (kind == ContractKind.ENTRY_POINT) {
            field("coordinate")
        }
        return ContractEntry(
            id = id,
            kind = kind,
            page = field("page"),
            claim = field("claim"),
            since = since,
            expect = expect,
            probe = field("probe"),
            coordinate = raw["coordinate"] as? String,
            registry = raw["registry"] as? String
        )
    }

    /**
     * Every problem `contractLint` reports, empty when the contract is internally consistent.
     * `repoRoot` resolves `page` and `probe` pointers; `unreleasedMarkerVersions` is the distinct
     * set of versions cited by `*(since X.Y.Z, unreleased)*` across the English docs ([SnippetSupport]
     * already walks that file set for part (a) — passed in rather than re-walked here so the two
     * checks can never quietly disagree on which files count as "the English docs").
     */
    fun lint(repoRoot: File, document: ContractDocument, unreleasedMarkerVersions: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()
        val seenClaims = mutableMapOf<String, String>()

        for (entry in document.entries) {
            if (!seenIds.add(entry.id)) {
                problems += "duplicate entry id \"${entry.id}\""
            }
            seenClaims.put(entry.claim, entry.id)?.let { firstId ->
                problems += "\"${entry.id}\" and \"$firstId\" make the same claim: \"${entry.claim}\""
            }
            if (!SINCE_PATTERN.matches(entry.since)) {
                problems += "\"${entry.id}\": since \"${entry.since}\" is not a real version string (x.y.z)"
            }
            if (entry.kind == ContractKind.ENTRY_POINT && entry.coordinate.isNullOrBlank()) {
                problems += "\"${entry.id}\": entry-point requires \"coordinate\""
            }

            val probeFile = File(repoRoot, entry.probe)
            if (!probeFile.isFile) {
                problems += "\"${entry.id}\": probe \"${entry.probe}\" does not exist"
            }

            val ref = try {
                parsePageRef(entry.page)
            } catch (e: IllegalArgumentException) {
                problems += "\"${entry.id}\": ${e.message}"
                null
            }
            if (ref != null) {
                val pageFile = File(repoRoot, ref.relativePath)
                if (!pageFile.isFile) {
                    problems += "\"${entry.id}\": page \"${ref.relativePath}\" does not exist"
                } else if (ref.anchor !in headingAnchors(pageFile)) {
                    problems += "\"${entry.id}\": anchor \"#${ref.anchor}\" not found in ${ref.relativePath}"
                }
            }
        }

        val coveredVersions = document.entries.map { it.since }.toSet()
        for (version in unreleasedMarkerVersions.sorted()) {
            if (version !in coveredVersions) {
                problems += "documentation carries \"*(since $version, unreleased)*\" but no " +
                    "contract.yaml entry has since: \"$version\" — add one in the same commit as " +
                    "the feature (docs-vs-published-gate §5.1 ruling 3)"
            }
        }

        return problems.sorted()
    }
}

/** holds: the probe observed exactly what the docs claim. fails: it observed something else.
 * not-applicable-before-since: the claim's `since` is later than the version actually installed —
 * ruling 1 (docs-vs-published-gate §5.1): exempt only while later than the INSTALLED published
 * version, never the repo's own. */
enum class ContractVerdict { HOLDS, FAILS, NOT_APPLICABLE_BEFORE_SINCE }

data class ContractOutcome(val entry: ContractEntry, val verdict: ContractVerdict, val message: String)

/**
 * INTENT: The decision `contractCheck` (nightly, against a real probe) and `contractLint`'s own
 * fixture tests (offline, against a fake probe result standing in for one of the four historical
 * instances, docs-vs-published-gate §3) both go through — so "would the gate have fired" is
 * exactly the same code path whether the probe result came from Maven Central or from a test
 * fixture.
 */
object ContractDecisionSupport {

    /** `x.y.z` -> `[x, y, z]`, for a plain lexicographic-after-parse compare — every `since` string
     * is already validated against [ContractLintSupport]'s pattern before this is ever called. */
    private fun parts(version: String): List<Int> = version.split(".").map { it.toInt() }

    /** True while `since` is NOT strictly later than `installedVersion` — the only case
     * docs-vs-published-gate §5.1 ruling 1 exempts a claim from being checked at all. */
    fun isApplicable(since: String, installedVersion: String): Boolean {
        val a = parts(since)
        val b = parts(installedVersion)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return true // equal versions: since holds AT the installed version, so it is applicable
    }

    /**
     * `observed` is null when the probe itself could not even run (registry unreachable, artifact
     * missing) — treated as a failure with its own explaining message, never silently skipped;
     * only a `since` later than [installedVersion] is ever skipped.
     */
    fun decide(entry: ContractEntry, installedVersion: String, observed: String?): ContractOutcome {
        if (!isApplicable(entry.since, installedVersion)) {
            return ContractOutcome(
                entry, ContractVerdict.NOT_APPLICABLE_BEFORE_SINCE,
                "\"${entry.id}\": since ${entry.since} is later than installed $installedVersion — skipped"
            )
        }
        if (observed == entry.expect) {
            return ContractOutcome(entry, ContractVerdict.HOLDS, "\"${entry.id}\": holds")
        }
        val coordinate = entry.coordinate ?: entry.id
        return ContractOutcome(
            entry, ContractVerdict.FAILS,
            "documentation/contract.yaml: ${entry.id} documented default \"${entry.expect}\" " +
                "(since ${entry.since}) but $coordinate $installedVersion (published) reads " +
                "\"${observed ?: "<no answer>"}\""
        )
    }
}
