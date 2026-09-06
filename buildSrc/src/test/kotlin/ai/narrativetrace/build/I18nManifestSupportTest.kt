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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class I18nManifestSupportTest {

    @TempDir
    lateinit var repo: File

    private fun writeManifest(json: String) {
        val file = repo.resolve(I18nManifestSupport.MANIFEST_RELATIVE_PATH)
        file.parentFile.mkdirs()
        file.writeText(json)
    }

    @Test
    fun loadOrNullReturnsNullWhenManifestIsAbsent() {
        assertNull(I18nManifestSupport.loadOrNull(repo))
    }

    @Test
    fun loadOrNullParsesLanguagesInDeclaredOrder() {
        writeManifest(
            """
            {
              "sourceLanguage": "en",
              "languages": [
                {"code": "es", "displayName": "Español", "directory": "documentation/es",
                 "index": "documentation/LEAME.md", "rootReadme": "LEAME.md", "status": "complete"},
                {"code": "zh-CN", "displayName": "简体中文", "directory": "documentation/zh-CN",
                 "index": "documentation/自述文件.md", "rootReadme": "自述文件.md", "status": "in-progress"}
              ],
              "documents": [
                {"source": "documentation/guide.md", "translations": {"es": "guia.md"}}
              ]
            }
            """.trimIndent()
        )

        val manifest = I18nManifestSupport.loadOrNull(repo)

        assertEquals(listOf("es", "zh-CN"), manifest?.languages?.map { it.code })
        assertEquals("Español", manifest?.languages?.get(0)?.displayName)
        assertEquals(I18nStatus.COMPLETE, manifest?.languages?.get(0)?.status)
        assertEquals(I18nStatus.IN_PROGRESS, manifest?.languages?.get(1)?.status)
        assertEquals("guia.md", manifest?.documents?.get(0)?.translations?.get("es"))
    }

    @Test
    fun loadOrNullRejectsAnUnknownStatusValue() {
        writeManifest(
            """
            {
              "sourceLanguage": "en",
              "languages": [
                {"code": "es", "displayName": "Español", "directory": "documentation/es",
                 "index": "documentation/LEAME.md", "rootReadme": "LEAME.md", "status": "done"}
              ],
              "documents": []
            }
            """.trimIndent()
        )

        val ex = assertThrows(IllegalArgumentException::class.java) { I18nManifestSupport.loadOrNull(repo) }
        assertTrue(ex.message!!.contains("done"))
    }

    @Test
    fun languageLooksUpByCode() {
        writeManifest(
            """
            {
              "sourceLanguage": "en",
              "languages": [
                {"code": "es", "displayName": "Español", "directory": "documentation/es",
                 "index": "documentation/LEAME.md", "rootReadme": "LEAME.md", "status": "complete"}
              ],
              "documents": []
            }
            """.trimIndent()
        )

        val manifest = I18nManifestSupport.loadOrNull(repo)!!

        assertEquals("es", manifest.language("es")?.code)
        assertNull(manifest.language("fr"))
    }
}
