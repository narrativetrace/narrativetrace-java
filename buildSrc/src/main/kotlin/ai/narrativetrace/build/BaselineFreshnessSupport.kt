/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * INTENT: Backs the root `baselineFreshnessCheck` task — says out loud, on every build, how old the
 * committed benchmark baseline is.
 *
 * `baseline.txt` and the README's performance FAQ are the project's public performance claim. In
 * 2026 that claim went six months without a run behind it, through a buffer refactor and a level
 * rename, and nobody noticed because nothing ever mentioned its age. This check never fails a
 * build: a stale baseline is not a broken build, it is a claim whose evidence expired, and the
 * remedy (a 10-minute benchmark run) is not something to block a commit on.
 */
object BaselineFreshnessSupport {

    /** How old a baseline may be before every build starts saying so. */
    const val MAX_AGE_DAYS = 90L

    /** What to run when the warning appears. */
    const val REFRESH_COMMAND = "./gradlew :narrativetrace-benchmarks:jmh -PjmhProfilers=gc"

    /** `# Benchmark baseline — 2026-02-25`, with either dash spelling. */
    private val DATE_HEADER = Regex("""^#.*?[-—]\s*(\d{4}-\d{2}-\d{2})\s*$""", RegexOption.MULTILINE)

    /** `# Commit: bbeec28 (allocation baseline run)`. */
    private val COMMIT_HEADER = Regex("""^#\s*Commit:\s*(\S+)""", RegexOption.MULTILINE)

    /** The date the baseline's own header states, or `null` when it states none. */
    fun recordedDate(text: String): LocalDate? {
        val match = DATE_HEADER.find(text) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1])
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** The commit the baseline's own header names, or `null` when it names none. */
    fun recordedCommit(text: String): String? = COMMIT_HEADER.find(text)?.groupValues?.get(1)

    /**
     * The line every build prints: how old the baseline is, and what to do when that is too old.
     *
     * Always returns something. A build that says nothing about the baseline is how the baseline
     * got to be six months old in the first place.
     */
    fun report(file: File, today: LocalDate): String {
        val label = "baselineFreshnessCheck"
        if (!file.isFile) {
            return "$label: WARNING — no baseline at ${file.path}. Refresh with: $REFRESH_COMMAND"
        }
        val text = file.readText()
        val date = recordedDate(text)
            ?: return "$label: WARNING — ${file.name} carries no dated header, so its age is unknown. " +
                "Refresh with: $REFRESH_COMMAND"
        val ageDays = ChronoUnit.DAYS.between(date, today)
        val commit = recordedCommit(text)?.let { " (commit $it)" } ?: ""
        return if (ageDays > MAX_AGE_DAYS) {
            "$label: WARNING — ${file.name} is $ageDays days old, recorded $date$commit, " +
                "over the $MAX_AGE_DAYS-day limit. Refresh with: $REFRESH_COMMAND"
        } else {
            "$label: ${file.name} is $ageDays days old, recorded $date$commit"
        }
    }

    /** Whether [report] would warn — the same decision, for tests and for callers that branch. */
    fun isStale(file: File, today: LocalDate): Boolean {
        if (!file.isFile) {
            return true
        }
        val date = recordedDate(file.readText()) ?: return true
        return ChronoUnit.DAYS.between(date, today) > MAX_AGE_DAYS
    }
}
