/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * INTENT: Backs the `llms.txt` "docs vs published" line — the family-wide docs-vs-published-gate
 * design, part (a). The line states, in one sentence under the H1, whether the docs in this
 * checkout describe behaviour Maven Central has actually shipped, so an AI agent (or a human)
 * reading `llms.txt` never has to guess which side of a release it is looking at.
 *
 * <p>Four inputs, one line:
 * <ul>
 *   <li>the repo's own version ([SnippetSupport]'s caller reads it from
 *       {@code gradle.properties#narrativetraceVersion}, the same property
 *       {@code scripts/publish-public.sh}'s {@code detect_version} reads);</li>
 *   <li>the latest version Maven Central actually serves for the bellwether artifact,
 *       {@code narrativetrace-core} — the module every other published module depends on, so its
 *       presence on Central implies the release as a whole landed (multi-artifact repos use one
 *       bellwether rather than polling all eighteen for one banner line);</li>
 *   <li>a git-ignored cache under {@code build/}, refreshed at most once an hour, so repeated local
 *       builds do not hammer Central;</li>
 *   <li>the count of {@code *(since X.Y.Z, unreleased)*} markers across the runtime's English docs
 *       ([countUnreleasedMarkers]) — the repo version stays at the last published number until
 *       {@code --tag} bumps it (docs-vs-published-gate design note, item 8), so without this the
 *       "both at X" shape would wrongly imply nothing is ahead of the published release.</li>
 * </ul>
 *
 * <p>Network access happens in exactly one place, [fetchLatestPublishedVersion], called only by
 * [refreshCache] — which only `snippetSync` (or an explicit refresh task) ever invokes. [llmsTxtLine]
 * itself is pure and network-free, so `snippetCheck` computes the exact same line `snippetSync` would
 * from whatever is already on disk, and never fails merely because the current run is offline
 * (thin-CI convention: no network in the per-commit gate). Reuses
 * {@code scripts/verify-publication.sh}'s `MAVEN_CENTRAL_BASE` constant and its "no answer" sentinel
 * (there, curl's `000`; here, a null return) — any I/O failure, non-200 status or unparsable body
 * counts as "no answer", never a guess presented as fact.
 */
object PublishedVersionSupport {

    /** Same value as `scripts/verify-publication.sh`'s `MAVEN_CENTRAL_BASE`. */
    const val MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2"

    // The bellwether: every published module either IS narrativetrace-core or depends on it, so
    // its presence on Central at a given version implies the whole release landed (see class doc).
    private const val BELLWETHER_GROUP_PATH = "ai/narrativetrace"
    private const val BELLWETHER_ARTIFACT = "narrativetrace-core"

    private const val CACHE_MAX_AGE_MILLIS = 60L * 60L * 1000L
    private const val CONNECT_TIMEOUT_MILLIS = 5_000
    private const val READ_TIMEOUT_MILLIS = 5_000

    private val RELEASE_TAG = Regex("""<release>\s*([^<\s]+)\s*</release>""")
    private val LATEST_TAG = Regex("""<latest>\s*([^<\s]+)\s*</latest>""")
    private val TRAILING_COMMENT = Regex("""\s*<!--.*-->\s*$""")

    // The three shapes llmsTxtLine ever emits, each with an optional "; N behaviour(s) marked
    // unreleased" clause (design note item 8) — parseBannerLine is llmsTxtLine's inverse, so
    // bannerProblems can read back whatever is actually committed instead of demanding a byte-exact
    // match against a line only a live fetch could have produced.
    private val UNRELEASED_CLAUSE = """(?:; (\d+) behaviours? marked unreleased)?"""
    private val BOTH_AT = Regex("""^\*\(Docs and published both at (.+?)$UNRELEASED_CLAUSE\.\)\*$""")
    private val DESCRIBE_PUBLISHED =
        Regex("""^\*\(These docs describe (.+?); published is (.+?)$UNRELEASED_CLAUSE\.\)\*$""")
    private val DESCRIBE_UNKNOWN =
        Regex("""^\*\(These docs describe (.+?); published: unknown offline$UNRELEASED_CLAUSE\.\)\*$""")

    // The marker `llmsTxtLine`'s "N behaviours marked unreleased" clause is counting —
    // documentation-*.md's inline `*(since X.Y.Z, unreleased)*` — kept identical to
    // `scripts/publish-public.sh`'s own matching regex so the two can never silently disagree.
    private val UNRELEASED_MARKER = Regex("""\(since \d+\.\d+\.\d+, unreleased\)\*""")

    /** One successful lookup: the version Central served, and when this cache entry was written. */
    data class CacheEntry(val version: String, val fetchedAtEpochMillis: Long)

    /** A banner line, decomposed back into the facts it cites. [citedPublishedVersion] is null for
     * the "unknown offline" shape, which cites no published version at all. [citedUnreleasedCount]
     * is 0 when the line carries no "marked unreleased" clause at all — indistinguishable from a
     * line that cites a genuine zero, which is exactly right: zero is the one count that is never
     * written with a clause (see [unreleasedClause]). */
    private data class ParsedBanner(
        val citedRepoVersion: String,
        val citedPublishedVersion: String?,
        val citedUnreleasedCount: Int,
    )

    private fun parseBannerLine(line: String): ParsedBanner? =
        BOTH_AT.find(line)?.let {
            ParsedBanner(it.groupValues[1], it.groupValues[1], it.groupValues[2].toIntOrNull() ?: 0)
        } ?: DESCRIBE_PUBLISHED.find(line)?.let {
            ParsedBanner(it.groupValues[1], it.groupValues[2], it.groupValues[3].toIntOrNull() ?: 0)
        } ?: DESCRIBE_UNKNOWN.find(line)?.let {
            ParsedBanner(it.groupValues[1], null, it.groupValues[2].toIntOrNull() ?: 0)
        }

    /**
     * Asks Maven Central for `narrativetrace-core`'s `maven-metadata.xml` and reads its
     * `<release>` (falling back to `<latest>` when a metadata document carries only that). Null on
     * any failure — connection refused, timeout, non-200 status, or a body neither tag parses from —
     * the "no answer" sentinel `scripts/verify-publication.sh` also uses (there, curl's `000`).
     * Never throws: a registry hiccup must not fail a build that merely wants to know its own
     * docs-vs-published state.
     */
    fun fetchLatestPublishedVersion(mavenCentralBase: String = MAVEN_CENTRAL_BASE): String? =
        try {
            val url = URL("$mavenCentralBase/$BELLWETHER_GROUP_PATH/$BELLWETHER_ARTIFACT/maven-metadata.xml")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            readVersionFromMetadata(connection)
        } catch (e: Exception) {
            null
        }

    private fun readVersionFromMetadata(connection: HttpURLConnection): String? {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return null
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return (RELEASE_TAG.find(body) ?: LATEST_TAG.find(body))?.groupValues?.get(1)
    }

    /**
     * Reads [cacheFile] as a fresh lookup (no older than one hour as of [now]); null when the file
     * is absent, malformed, or stale. Pure and network-free — this is what keeps [llmsTxtLine]
     * network-free too, since both `snippetCheck` and `snippetSync` route the published-version half
     * of the line through this before ever considering a fetch.
     */
    fun readCache(cacheFile: File, now: Long = System.currentTimeMillis()): CacheEntry? {
        if (!cacheFile.isFile) {
            return null
        }
        val parts = cacheFile.readText().trim().split("|")
        if (parts.size != 2) {
            return null
        }
        val version = parts[0]
        val fetchedAt = parts[1].toLongOrNull() ?: return null
        if (version.isBlank() || now - fetchedAt > CACHE_MAX_AGE_MILLIS) {
            return null
        }
        return CacheEntry(version, fetchedAt)
    }

    /**
     * Best-effort network refresh, called only from `snippetSync`-family tasks — never from
     * `snippetCheck`, which stays network-free by design (thin-CI: no network in the per-commit
     * gate). A successful fetch overwrites [cacheFile] with a fresh entry and is returned directly;
     * a failed fetch leaves the file untouched (a bad lookup never clobbers a good one) and this
     * falls back to [readCache] — so callers see the old entry if it is still fresh, or null if it
     * has since gone stale.
     */
    fun refreshCache(
        cacheFile: File,
        mavenCentralBase: String = MAVEN_CENTRAL_BASE,
        now: Long = System.currentTimeMillis(),
    ): CacheEntry? {
        val fetched = fetchLatestPublishedVersion(mavenCentralBase) ?: return readCache(cacheFile, now)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("$fetched|$now")
        return CacheEntry(fetched, now)
    }

    /**
     * The `llms.txt` line for [repoVersion] given whatever [cache] state is on hand (from
     * [readCache], never from a fresh fetch here — this function is pure and makes no network
     * call). Three shapes, matching the design note exactly:
     * <ul>
     *   <li>no fresh cache at all (the standing case for an offline nightly run): "published:
     *       unknown offline" — never a guess, never a stale cached value presented as current;</li>
     *   <li>versions equal: "Docs and published both at X";</li>
     *   <li>versions differ: "These docs describe X; published is Y".</li>
     * </ul>
     * Each shape gets [unreleasedClause] appended when [unreleasedCount] is positive — "; N
     * behaviour(s) marked unreleased" — right before the closing `.)*` (design note item 8: the
     * repo version sits at the last published number until `--tag` bumps it, so "both at X" alone
     * would wrongly read as "nothing ahead of the published release"). Zero is never written as a
     * clause at all, so today's line is byte-identical to before this feature existed.
     *
     * <p>A trailing HTML comment records the cache's age at generation time so staleness is visible
     * without ever being silent — strip it with [stripCacheAgeComment] before comparing two
     * renderings of this line, or the comparison flakes on wall-clock.
     */
    fun llmsTxtLine(
        repoVersion: String,
        cache: CacheEntry?,
        unreleasedCount: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): String {
        val clause = unreleasedClause(unreleasedCount)
        if (cache == null) {
            return "*(These docs describe $repoVersion; published: unknown offline$clause.)*"
        }
        val body =
            if (cache.version == repoVersion) {
                "*(Docs and published both at $repoVersion$clause.)*"
            } else {
                "*(These docs describe $repoVersion; published is ${cache.version}$clause.)*"
            }
        val ageMinutes = ((now - cache.fetchedAtEpochMillis).coerceAtLeast(0)) / 60_000
        return "$body <!-- published-version cache age: ${ageMinutes}m -->"
    }

    /** [line] with any trailing cache-age HTML comment removed — what a fresh rendering and one
     * read back off disk minutes or hours later must compare equal as. */
    fun stripCacheAgeComment(line: String): String = line.replace(TRAILING_COMMENT, "").trim()

    /**
     * Counts every {@code *(since X.Y.Z, unreleased)*} marker across the runtime's English docs:
     * everything under {@code documentation/} (translated mirrors — any
     * {@code documentation/<lang>/} directory, plus {@code i18n} — excluded, same scope
     * [SnippetSupport.englishMarkdownFiles] uses) and {@code README.md} at the repo root.
     * {@code documentation/llms.txt} and {@code documentation/llms-full.md} fall out of that walk
     * with no special-casing needed.
     *
     * <p>Pure file scan, no network — unlike the published-version half of the banner, this is
     * always available and so, per the design note (item 8), always checked by `snippetCheck`
     * regardless of cache freshness.
     */
    fun countUnreleasedMarkers(repoRoot: File): Int {
        val documentation = repoRoot.resolve("documentation")
        val languageDirs = documentation.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "i18n" }
            .map { it.canonicalFile }
            .toSet()
        val docFiles = documentation.walkTopDown()
            .onEnter { dir -> dir == documentation || dir.canonicalFile !in languageDirs }
            .filter { it.isFile && (it.extension == "md" || it.name == "llms.txt") }
        val readme = repoRoot.resolve("README.md").takeIf { it.isFile }
        return (docFiles.toList() + listOfNotNull(readme))
            .sumOf { UNRELEASED_MARKER.findAll(it.readText()).count() }
    }

    /** The clause `llmsTxtLine` appends for [count] behaviours marked unreleased — empty for zero,
     * the one count [countUnreleasedMarkers] returns that is never written as a clause at all
     * (design note item 8: "count 0 ... the line is unchanged"). Singular "behaviour" for one. */
    private fun unreleasedClause(count: Int): String =
        if (count <= 0) "" else "; $count behaviour${if (count == 1) "" else "s"} marked unreleased"

    /**
     * `snippetCheck`'s verdict on the committed `llms.txt` banner — the read-only half of the
     * docs-vs-published-gate design (note 1.1, item 2). Three independent facts, each checked only
     * when it can be checked without a network call:
     * <ul>
     *   <li>the repo-version half is deterministic ({@code gradle.properties#narrativetraceVersion})
     *       and is always checked, regardless of cache state;</li>
     *   <li>the unreleased-marker-count half ([unreleasedCount], from [countUnreleasedMarkers]) is
     *       equally deterministic (a pure file scan, no network) and is likewise always checked
     *       (design note item 8) — never gated on cache freshness;</li>
     *   <li>the published-version half is checked only when [cache] is a fresh (&lt;1h) entry —
     *       [PublishedVersionSupport.readCache] already returns null for a stale or absent file, so
     *       a caller that always passes its result through here cannot tell "never fetched" from
     *       "gone stale" apart, by design. With no fresh cache, any of the three well-formed banner
     *       shapes [llmsTxtLine] can produce is accepted for that half — there is nothing on hand
     *       to verify it against, and the check must never fail merely because this run is offline.
     * </ul>
     *
     * @param actualLineRaw whatever [LlmsTxtBannerSupport.currentLine] returned for the committed
     *   file — a trailing cache-age comment, if present, is stripped before any comparison here.
     * @param unreleasedCount the current, real count from [countUnreleasedMarkers] — never a value
     *   read back off the banner itself.
     * @return one ready-to-print problem line per issue found, each already naming the file and
     *   the `snippetSync` remedy; the empty list when the banner is fine.
     */
    fun bannerProblems(
        actualLineRaw: String?,
        repoVersion: String,
        cache: CacheEntry?,
        unreleasedCount: Int = 0,
    ): List<String> {
        val prefix = "documentation/llms.txt: docs-vs-published banner"
        val actual = actualLineRaw?.let(::stripCacheAgeComment)
            ?: return listOf("$prefix marker pair is missing; run snippetSync")
        val parsed = parseBannerLine(actual)
            ?: return listOf("$prefix '$actual' is not a recognized docs-vs-published form; run snippetSync")

        val problems = mutableListOf<String>()
        if (parsed.citedRepoVersion != repoVersion) {
            problems += "$prefix cites repo version '${parsed.citedRepoVersion}' but " +
                "gradle.properties#narrativetraceVersion is '$repoVersion'; run snippetSync"
        }
        if (parsed.citedUnreleasedCount != unreleasedCount) {
            problems += "$prefix cites ${parsed.citedUnreleasedCount} behaviour(s) marked unreleased " +
                "but the docs currently mark $unreleasedCount; run snippetSync"
        }
        if (cache != null) {
            val expected = stripCacheAgeComment(llmsTxtLine(repoVersion, cache, unreleasedCount))
            if (actual != expected) {
                problems += "$prefix is stale (expected '$expected', found '$actual'); run snippetSync"
            }
        }
        return problems
    }
}
