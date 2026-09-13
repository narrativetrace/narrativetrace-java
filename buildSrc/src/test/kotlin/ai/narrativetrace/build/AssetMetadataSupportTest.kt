/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Builds real PNGs byte-for-byte (no image library — same discipline as the .NET port's
 * `PngBytes` and the TypeScript/Python ports' own fixture builders) and proves
 * [AssetMetadataSupport] catches an embedded `iTXt` metadata chunk regardless of what it says —
 * the stronger, source-side rule beside `scripts/publish-public.sh`'s own binary re-scan (which
 * only catches a GATED WORD inside the chunk, at publish time; see
 * `PublishPublicScriptTest.BinaryAssetScan`).
 */
class AssetMetadataSupportTest {

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun pngChunk(tag: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeInt32BE(out, data.size.toLong())
        out.writeBytes(tag)
        out.writeBytes(data)
        val crc = CRC32()
        crc.update(tag)
        crc.update(data)
        writeInt32BE(out, crc.value)
        return out.toByteArray()
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(256)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    /** A real, valid 1x1 grayscale PNG. With [itxtText] given, carries it in an `iTXt` chunk. */
    private fun minimalPng(itxtText: String?): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte(), 0x1a, '\n'.code.toByte()))
        val ihdr = ByteArrayOutputStream()
        writeInt32BE(ihdr, 1) // width
        writeInt32BE(ihdr, 1) // height
        ihdr.writeBytes(byteArrayOf(8, 0, 0, 0, 0)) // depth, color type, compression, filter, interlace
        out.writeBytes(pngChunk("IHDR".toByteArray(Charsets.US_ASCII), ihdr.toByteArray()))
        val rawScanline = byteArrayOf(0, 0) // filter-type byte + one gray pixel
        out.writeBytes(pngChunk("IDAT".toByteArray(Charsets.US_ASCII), deflate(rawScanline)))
        if (itxtText != null) {
            val itxt = ByteArrayOutputStream()
            itxt.writeBytes("Comment".toByteArray(Charsets.US_ASCII))
            itxt.write(0) // keyword terminator
            itxt.write(0) // compression flag
            itxt.write(0) // compression method
            itxt.write(0) // language tag terminator (empty language tag)
            itxt.write(0) // translated keyword terminator (empty translated keyword)
            itxt.writeBytes(itxtText.toByteArray(Charsets.UTF_8))
            out.writeBytes(pngChunk("iTXt".toByteArray(Charsets.US_ASCII), itxt.toByteArray()))
        }
        out.writeBytes(pngChunk("IEND".toByteArray(Charsets.US_ASCII), ByteArray(0)))
        return out.toByteArray()
    }

    @TempDir
    lateinit var dir: File

    @Test
    fun aPngCarryingAnITxtChunkIsFlaggedRegardlessOfWhatItSays() {
        val f = dir.resolve("icon.png").apply { writeBytes(minimalPng("built by nobody in particular")) }

        val problems = AssetMetadataSupport.check(dir, listOf(f))

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("icon.png"))
        assertTrue(problems[0].contains("iTXt"))
    }

    @Test
    fun aPngWithNoTextChunkAtAllPasses() {
        val f = dir.resolve("icon.png").apply { writeBytes(minimalPng(null)) }

        assertTrue(AssetMetadataSupport.check(dir, listOf(f)).isEmpty())
    }

    @Test
    fun aGatedWordIsNotRequiredToTripTheCheck() {
        // The publish script's own binary re-scan only fires on a GATED WORD; this check is the
        // stronger rule — ANY text metadata chunk fails it, gated word or not.
        val f = dir.resolve("icon.png").apply { writeBytes(minimalPng("edited in a completely ordinary tool")) }

        val problems = AssetMetadataSupport.check(dir, listOf(f))

        assertEquals(1, problems.size)
    }

    @Test
    fun reportsThePathRelativeToRootDir() {
        val nested = dir.resolve("assets").apply { mkdirs() }.resolve("icon.png")
        nested.writeBytes(minimalPng("edited elsewhere"))

        val problems = AssetMetadataSupport.check(dir, listOf(nested))

        assertTrue(problems[0].startsWith("assets/icon.png:"))
    }

    @Test
    fun carriesMetadataMarkerFindsAllThreePngTextChunkTypes() {
        for (tag in listOf("iTXt", "tEXt", "zTXt")) {
            assertTrue(
                AssetMetadataSupport.carriesMetadataMarker(tag.toByteArray(Charsets.US_ASCII)),
                "expected $tag to be recognized as a metadata marker"
            )
        }
    }

    @Test
    fun isCoveredImageMatchesCaseInsensitivelyOnKnownRasterExtensions() {
        assertTrue(AssetMetadataSupport.isCoveredImage("assets/ICON.PNG"))
        assertTrue(AssetMetadataSupport.isCoveredImage("a.jpg"))
        assertFalse(AssetMetadataSupport.isCoveredImage("a.svg"))
        assertFalse(AssetMetadataSupport.isCoveredImage("a.pdf"))
        assertFalse(AssetMetadataSupport.isCoveredImage("noextension"))
    }
}
