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

/** The four claim shapes `documentation/contract.yaml` can make. */
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
 * probed-default) or `expected_effect` (config-shape); both land here as one field because every
 * probe this repo runs already reduces to "one stdout line, string-compared" (see
 * `contract-probe/`'s `ContractRunner`) regardless of which document field named it.
 * `coordinate`/`registry` apply to `ENTRY_POINT` only; `probe` names the source file (relative to
 * the repo root) implementing the check — required for every kind so a reader always finds the
 * code proving the claim next to the claim itself.
 *
 * @llmNote There is no version field, and adding one back is the mistake this shape exists to
 * prevent: an entry describes the code it is committed with, like every other document here, and
 * the gate reads the contract at the tag whose artifact it installs, so claim and artifact already
 * come from one commit. An older release's file may still carry a `since:` key — parsing ignores
 * any key this record does not name.
 */
data class ContractEntry(
    val id: String,
    val kind: ContractKind,
    val page: String,
    val claim: String,
    val expect: String,
    val probe: String,
    val coordinate: String? = null,
    val registry: String? = null
)

data class ContractDocument(val versionSource: String, val entries: List<ContractEntry>)

/** One page-anchor pointer, split for validation (`readAnchors` builds the target's real set). */
data class ContractPageRef(val relativePath: String, val anchor: String)

/**
 * INTENT: Backs the root `contractLint` task — everything about `documentation/contract.yaml` a
 * per-commit gate can check WITHOUT the network: the schema parses, no two entries make the same
 * claim, every entry's `probe` file exists, and every `page#anchor` pointer resolves to a heading
 * that actually exists. What this class deliberately does NOT do: run a probe, touch the
 * registry, or decide holds/fails for an actual probe result — that decision lives in
 * [ContractDecisionSupport], exercised nightly by `contract-probe/` and, standalone, by
 * `contractLint`'s own fixture tests for the four historical instances, so the decision logic is
 * provable without a release.
 *
 * @llmNote Version talk in a document — a `*(since X)*` marker included, in a heading or anywhere
 * else — is [VersionLiteralSupport]'s rule, enforced by `snippetCheck`. This class never scans
 * prose for one; two scans for one rule is how the two drift apart.
 */
object ContractLintSupport {

    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""")

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
            expect = expect,
            probe = field("probe"),
            coordinate = raw["coordinate"] as? String,
            registry = raw["registry"] as? String
        )
    }

    /**
     * Every problem `contractLint` reports, empty when the contract is internally consistent.
     * `repoRoot` resolves the `page` and `probe` pointers each entry carries.
     */
    fun lint(repoRoot: File, document: ContractDocument): List<String> {
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

        return problems.sorted()
    }
}

/** holds: the probe observed exactly what the docs claim. fails: it observed something else — a
 * probe that could not answer at all included. There is no third verdict: an entry describes the
 * code it was committed with, and the gate reads the contract at the tag whose artifact it
 * installs, so no entry can ever be "not released yet". */
enum class ContractVerdict { HOLDS, FAILS }

data class ContractOutcome(val entry: ContractEntry, val verdict: ContractVerdict, val message: String)

/**
 * INTENT: The decision `contractCheck` (nightly, against a real probe) and `contractLint`'s own
 * fixture tests (offline, against a fake probe result standing in for one of the four historical
 * instances) both go through — so "would the gate have fired" is exactly the same code path
 * whether the probe result came from Maven Central or from a test fixture.
 *
 * <p>Where `installedVersion` comes from, and why it is only ever NAMED here: it is passed in by
 * the caller, never inferred, and it decides nothing — every entry is checked, always. It is
 * reported so a failure line says which artifact answered. `scripts/contract-check.sh` resolves it
 * the way `scripts/verify-publication.sh` does — the newest `v*` tag reachable from HEAD, else
 * Maven Central's `maven-metadata.xml <latest>` for `narrativetrace-core` — hands it to
 * `contract-probe`'s `-PcontractVersion`, and reads the contract itself from that same tag, so a
 * claim and the artifact it is checked against always come from one commit.
 */
object ContractDecisionSupport {

    /**
     * `observed` is null when the probe itself could not even run (registry unreachable, artifact
     * missing) — treated as a failure with its own explaining message, never silently skipped.
     * Nothing is ever skipped: there is no verdict for it.
     */
    fun decide(entry: ContractEntry, installedVersion: String, observed: String?): ContractOutcome {
        if (observed == entry.expect) {
            return ContractOutcome(entry, ContractVerdict.HOLDS, "\"${entry.id}\": holds")
        }
        val coordinate = entry.coordinate ?: entry.id
        return ContractOutcome(
            entry, ContractVerdict.FAILS,
            "documentation/contract.yaml: ${entry.id} documented default \"${entry.expect}\" " +
                "but $coordinate $installedVersion (published) reads \"${observed ?: "<no answer>"}\""
        )
    }
}
