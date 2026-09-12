/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PublishedVersionSupportTest {

    @TempDir
    lateinit var dir: File

    private val cacheFile get() = dir.resolve("published-version-cache.txt")

    // ---------------------------------------------------------------------------------------
    // llmsTxtLine — the three cases the design note names
    // ---------------------------------------------------------------------------------------

    @Test
    fun lineStatesBothAtTheSameVersionWhenTheyAreEqual() {
        val cache = PublishedVersionSupport.CacheEntry("0.2.1", 1_000L)

        val line = PublishedVersionSupport.llmsTxtLine("0.2.1", cache, now = 1_000L)

        assertTrue(
            PublishedVersionSupport.stripCacheAgeComment(line) == "*(Docs and published both at 0.2.1.)*",
            line,
        )
    }

    @Test
    fun lineNamesBothVersionsWhenTheyDiffer() {
        val cache = PublishedVersionSupport.CacheEntry("0.2.1", 1_000L)

        val line = PublishedVersionSupport.llmsTxtLine("0.2.2", cache, now = 1_000L)

        assertEquals(
            "*(These docs describe 0.2.2; published is 0.2.1.)*",
            PublishedVersionSupport.stripCacheAgeComment(line),
        )
    }

    @Test
    fun lineStatesUnknownOfflineWithNoFreshCache() {
        val line = PublishedVersionSupport.llmsTxtLine("0.2.2", cache = null)

        assertEquals(
            "*(These docs describe 0.2.2; published: unknown offline.)*",
            PublishedVersionSupport.stripCacheAgeComment(line),
        )
    }

    @Test
    fun lineNeverGuessesFromAGoneStaleCache() {
        // Simulates the check task's own view: readCache already returned null for a stale
        // file, so llmsTxtLine is called with cache = null exactly as it would be for "never
        // fetched at all" — the two must be indistinguishable to a reader.
        val line = PublishedVersionSupport.llmsTxtLine("0.2.2", cache = null, now = 99_999_999L)

        assertTrue(line.contains("unknown offline"), line)
        assertTrue(!line.contains("0.2.1"), line)
    }

    @Test
    fun lineCarriesAStrippableCacheAgeComment() {
        val cache = PublishedVersionSupport.CacheEntry("0.2.1", fetchedAtEpochMillis = 0L)

        val line = PublishedVersionSupport.llmsTxtLine("0.2.1", cache, now = 15 * 60_000L)

        assertTrue(line.contains("<!--"), line)
        assertTrue(line.contains("15m"), line)
        assertEquals("*(Docs and published both at 0.2.1.)*", PublishedVersionSupport.stripCacheAgeComment(line))
    }

    // ---------------------------------------------------------------------------------------
    // readCache — pure, network-free, age-gated
    // ---------------------------------------------------------------------------------------

    @Test
    fun readCacheReturnsNullWhenTheFileIsAbsent() {
        assertNull(PublishedVersionSupport.readCache(cacheFile))
    }

    @Test
    fun readCacheReturnsNullWhenMalformed() {
        cacheFile.writeText("not-a-cache-line")

        assertNull(PublishedVersionSupport.readCache(cacheFile))
    }

    @Test
    fun readCacheReturnsTheEntryWhenFresh() {
        cacheFile.writeText("0.2.1|1000")

        val entry = PublishedVersionSupport.readCache(cacheFile, now = 1000L + 30 * 60_000L)

        assertEquals(PublishedVersionSupport.CacheEntry("0.2.1", 1000L), entry)
    }

    @Test
    fun readCacheReturnsNullPastOneHour() {
        cacheFile.writeText("0.2.1|1000")

        val entry = PublishedVersionSupport.readCache(cacheFile, now = 1000L + 61 * 60_000L)

        assertNull(entry)
    }

    // ---------------------------------------------------------------------------------------
    // refreshCache — network path is exercised via fetchLatestPublishedVersion's own failure
    // mode (an unreachable host), never a real Central call from a unit test.
    // ---------------------------------------------------------------------------------------

    @Test
    fun refreshCacheFallsBackToTheExistingEntryWhenTheFetchFails() {
        cacheFile.writeText("0.2.1|1000")
        val unreachableBase = "http://127.0.0.1:1"

        val entry = PublishedVersionSupport.refreshCache(cacheFile, mavenCentralBase = unreachableBase, now = 1000L + 60_000L)

        assertEquals(PublishedVersionSupport.CacheEntry("0.2.1", 1000L), entry)
        assertEquals("0.2.1|1000", cacheFile.readText())
    }

    @Test
    fun refreshCacheReturnsNullWhenTheFetchFailsAndNothingWasCachedYet() {
        val unreachableBase = "http://127.0.0.1:1"

        val entry = PublishedVersionSupport.refreshCache(cacheFile, mavenCentralBase = unreachableBase)

        assertNull(entry)
        assertTrue(!cacheFile.isFile)
    }

    @Test
    fun fetchLatestPublishedVersionReturnsNullOnAnUnreachableHost() {
        assertNull(PublishedVersionSupport.fetchLatestPublishedVersion("http://127.0.0.1:1"))
    }

    // ---------------------------------------------------------------------------------------
    // stripCacheAgeComment
    // ---------------------------------------------------------------------------------------

    @Test
    fun stripCacheAgeCommentLeavesALineWithNoCommentUnchanged() {
        val line = "*(These docs describe 0.2.2; published: unknown offline.)*"

        assertEquals(line, PublishedVersionSupport.stripCacheAgeComment(line))
    }

    // ---------------------------------------------------------------------------------------
    // bannerProblems — snippetCheck's read-only verdict (docs-vs-published-gate design note
    // 1.1, item 2): repo-version half always checked; published half only with a fresh cache.
    // ---------------------------------------------------------------------------------------

    @Test
    fun bothAtFormPassesWithNoCacheWhenTheRepoVersionMatches() {
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(Docs and published both at 0.2.1.)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun describePublishedFormPassesWithNoCacheWhenTheRepoVersionMatches() {
        // The published half ("0.2.0") is unverifiable offline and must not be second-guessed —
        // only the repo-version half is deterministic without a fetch.
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(These docs describe 0.2.1; published is 0.2.0.)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun unknownOfflineFormPassesWithNoCacheWhenTheRepoVersionMatches() {
        // This is exactly the staged-public-snapshot defect: no build cache in a fresh checkout,
        // committed banner in the "unknown offline" shape.
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(These docs describe 0.2.1; published: unknown offline.)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun bannerCitingAWrongRepoVersionFailsNamingBoth() {
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(Docs and published both at 0.2.0.)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("0.2.0"), problems[0])
        assertTrue(problems[0].contains("0.2.1"), problems[0])
    }

    @Test
    fun freshCacheDisagreeingWithTheCommittedPublishedHalfFails() {
        val cache = PublishedVersionSupport.CacheEntry("0.2.0", fetchedAtEpochMillis = 1_000L)

        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(Docs and published both at 0.2.1.)*",
            repoVersion = "0.2.1",
            cache = cache,
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("stale"), problems[0])
    }

    @Test
    fun freshCacheAgreeingWithTheCommittedLinePasses() {
        val cache = PublishedVersionSupport.CacheEntry("0.2.1", fetchedAtEpochMillis = 1_000L)

        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(Docs and published both at 0.2.1.)* <!-- published-version cache age: 0m -->",
            repoVersion = "0.2.1",
            cache = cache,
        )

        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun staleCacheIsTreatedAsNoCacheSoAnUnknownOfflineCommittedLineStillPasses() {
        // readCache already returns null past one hour; a caller (snippetCheck) that always
        // routes through readCache before calling bannerProblems can never observe a stale entry
        // here — this proves the "no fresh cache" acceptance path covers that case too.
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(These docs describe 0.2.1; published: unknown offline.)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun missingMarkerPairFails() {
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = null,
            repoVersion = "0.2.1",
            cache = null,
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("missing"), problems[0])
    }

    @Test
    fun unrecognizedFormFails() {
        val problems = PublishedVersionSupport.bannerProblems(
            actualLineRaw = "*(some hand-typed banner)*",
            repoVersion = "0.2.1",
            cache = null,
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("not a recognized"), problems[0])
    }
}
