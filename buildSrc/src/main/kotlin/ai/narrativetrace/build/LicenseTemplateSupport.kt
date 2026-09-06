/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.time.LocalDate

/**
 * The BSL parameter block, filled at build time.
 *
 * INTENT: Backs the `narrativetrace-license-packaging` convention plugin, which puts the licence
 * text and `NOTICE` inside every published jar. Apache-2.0 §4(a) requires a recipient of the api
 * jar to get a copy of the License with it, and BSL 1.1 requires the License to be "conspicuously
 * displayed on each original or modified copy of the Licensed Work" — a Maven artifact is a copy,
 * and until now none of them carried either text.
 *
 * The root `LICENSE` is a *template*, not a licence: `{{VERSION}}` and `{{CHANGE_DATE}}` stand in
 * for the two BSL parameters that are per-version rather than per-repository. Copying it into a jar
 * unfilled would ship a licence that names no version and no Change Date — worse than shipping
 * none, because it looks like one. So the fill happens here, on the same arithmetic
 * `scripts/publish-public.sh` uses for the public snapshot (four years from the build date), and
 * anything still matching `{{...}}` afterwards fails the build rather than being packaged.
 *
 * @llmNote [fill] replaces only the two placeholders this build knows the meaning of. That is the
 * point of the pairing with [unfilledPlaceholders]: a third placeholder added to the template by a
 * future licence change is not silently carried into a jar, it stops the build until someone
 * teaches the build what it means.
 */
object LicenseTemplateSupport {

    /** Stands in for the version the Licensed Work parameter names. */
    const val VERSION_PLACEHOLDER = "{{VERSION}}"

    /** Stands in for the date the Change Date parameter names. */
    const val CHANGE_DATE_PLACEHOLDER = "{{CHANGE_DATE}}"

    /** BSL's conversion horizon, and the one number this build may not get wrong. */
    const val CHANGE_DATE_YEARS = 4L

    private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

    private val ANY_PLACEHOLDER = Regex("""\{\{[^}]*}}""")

    /**
     * The version a licence may name: the project version with `-SNAPSHOT` removed.
     *
     * A Change Date attaches to a released version, so the licence inside a `0.2.0-SNAPSHOT` jar
     * names 0.2.0 — the version that jar is a pre-release of. The alternative, naming
     * "0.2.0-SNAPSHOT", would be a licence parameter no released artifact ever matches.
     *
     * @throws IllegalArgumentException when the version is blank or Gradle's `unspecified`
     */
    fun releaseVersion(version: String): String {
        val trimmed = version.trim()
        require(trimmed.isNotEmpty() && trimmed != "unspecified") {
            "the project version is '$version' — a licence cannot name a version the build does not have"
        }
        return trimmed.removeSuffix(SNAPSHOT_SUFFIX)
    }

    /** The Change Date for a copy built on [buildDate]: four years on, ISO-8601. */
    fun changeDate(buildDate: LocalDate): String = buildDate.plusYears(CHANGE_DATE_YEARS).toString()

    /**
     * Fills the two BSL parameters. A template with neither placeholder (`LICENSE-APACHE`, which
     * has no parameters at all) comes back unchanged — the same code path packages both licences.
     */
    fun fill(template: String, version: String, changeDate: String): String =
        template
            .replace(VERSION_PLACEHOLDER, version)
            .replace(CHANGE_DATE_PLACEHOLDER, changeDate)

    /**
     * Every `{{...}}` left in [text], as `<line number>: <the whole line>` — empty when the text is
     * a finished licence. The line is reported whole because the caller's message is read by
     * someone who has to decide what the placeholder was supposed to mean.
     */
    fun unfilledPlaceholders(text: String): List<String> =
        text.lineSequence()
            .mapIndexedNotNull { index, line ->
                if (ANY_PLACEHOLDER.containsMatchIn(line)) "${index + 1}: ${line.trim()}" else null
            }
            .toList()
}
