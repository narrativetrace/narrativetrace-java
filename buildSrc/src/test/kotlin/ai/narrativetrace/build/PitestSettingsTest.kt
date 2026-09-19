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

class PitestSettingsTest {

    @Test
    fun `absent property resolves to the default`() {
        assertEquals(4, PitestSettings.threads(propertyValue = null, default = 4))
    }

    @Test
    fun `blank property resolves to the default`() {
        assertEquals(4, PitestSettings.threads(propertyValue = "   ", default = 4))
    }

    @Test
    fun `a positive integer property overrides the default`() {
        assertEquals(2, PitestSettings.threads(propertyValue = "2", default = 4))
    }

    @Test
    fun `surrounding whitespace on a positive integer is tolerated`() {
        assertEquals(1, PitestSettings.threads(propertyValue = " 1 ", default = 4))
    }

    @Test
    fun `a non-numeric property is rejected, naming the property`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PitestSettings.threads(propertyValue = "many", default = 4)
        }
        assertTrue(error.message!!.contains("narrativetrace.pitest.threads"))
        assertTrue(error.message!!.contains("many"))
    }

    @Test
    fun `zero is rejected, naming the property`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PitestSettings.threads(propertyValue = "0", default = 4)
        }
        assertTrue(error.message!!.contains("narrativetrace.pitest.threads"))
    }

    @Test
    fun `a negative value is rejected, naming the property`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PitestSettings.threads(propertyValue = "-1", default = 4)
        }
        assertTrue(error.message!!.contains("narrativetrace.pitest.threads"))
    }
}
