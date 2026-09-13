/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * INTENT: Backs the root `assetMetadataCheck` task — the private tree's own source-side half of
 * the 2026-09-13 family finding, alongside `scripts/publish-public.sh`'s new binary-asset re-scan
 * (see `PublishPublicScriptTest.BinaryAssetScan`).
 *
 * The .NET port's NuGet package icon shipped with an embedded C2PA provenance chunk naming the AI
 * vendor in every published package, invisible for the icon's whole public life: the publish
 * gates' content scan is `grep -riIE`, and `-I` skips binary files outright. The publish script's
 * own binary re-scan (`grep -a`) closes that gap AT PUBLISH TIME, but only for a GATED WORD inside
 * the metadata — a chunk that is clean today and an unreviewed identity/provenance leak tomorrow
 * would still ship undetected until the next publish. This check is the stronger, structural rule:
 * no committed image ANYWHERE in the repo may carry a text metadata chunk of any kind, so nothing
 * has to be "gated-word"-clean to pass — it has to carry no metadata text at all. Cheap and
 * build-free (a handful of files, no compile dependency), so it rides every commit rather than
 * waiting for a publish dry run to surface it.
 */
object AssetMetadataSupport {

    /** Extensions this check treats as "an image" — the raster formats a provenance/EXIF/XMP
     * stamping tool actually writes into (an `.svg` is XML text already covered by the existing
     * text-content gates; it carries no opaque byte range for a marker to hide in). */
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "ico", "webp")

    /** PNG text-chunk type tags plus the XMP/C2PA provenance markers that ride inside them (or
     * inside a JPEG APP1 segment) — same set `scripts/publish-public.sh`'s TRACE/SECRETS binary
     * re-scan would eventually catch a GATED WORD inside, this check refuses the chunk outright. */
    private val METADATA_MARKER =
        Regex("""iTXt|tEXt|zTXt|XML:com\.adobe\.xmp|c2pa""", RegexOption.IGNORE_CASE)

    /**
     * True iff raw image [bytes] carry a text metadata / XMP / C2PA marker anywhere in the byte
     * stream. Decoded as Latin-1 (one byte -> one char, lossless round trip, never throws on
     * arbitrary binary content) so the regex can match an ASCII marker wherever it sits — the
     * same "binary content, treated as text" idea as `grep -a`, done in-process.
     */
    fun carriesMetadataMarker(bytes: ByteArray): Boolean =
        METADATA_MARKER.containsMatchIn(String(bytes, StandardCharsets.ISO_8859_1))

    /** True iff [path]'s extension (case-insensitive, no leading dot) is one this check covers. */
    fun isCoveredImage(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * Checks each of [imageFiles] and returns one problem line per file that carries a marker,
     * sorted for a stable report. [imageFiles] is caller-resolved (the task narrows it to the
     * repo's git-tracked images) so this stays a pure function over bytes already on disk.
     */
    fun check(rootDir: File, imageFiles: List<File>): List<String> =
        imageFiles
            .filter { it.isFile && carriesMetadataMarker(it.readBytes()) }
            .map {
                "${it.relativeTo(rootDir).invariantSeparatorsPath}: carries an embedded text " +
                    "metadata chunk (iTXt/tEXt/zTXt/XMP/C2PA) — committed assets must ship with " +
                    "none; strip it before committing (e.g. `exiftool -all=`)"
            }
            .sorted()
}
