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
import java.time.LocalDate

class LicenseTemplateSupportTest {

    // --- the version a licence may name ------------------------------------

    @Test
    fun stripsSnapshotSoTheLicenceNamesTheVersionTheJarIsAPreReleaseOf() {
        assertEquals("0.2.0", LicenseTemplateSupport.releaseVersion("0.2.0-SNAPSHOT"))
    }

    @Test
    fun leavesAReleasedVersionAlone() {
        assertEquals("0.2.0", LicenseTemplateSupport.releaseVersion("0.2.0"))
    }

    @Test
    fun stripsOnlyTheSuffix() {
        assertEquals("1.0.0-RC1", LicenseTemplateSupport.releaseVersion("1.0.0-RC1"))
    }

    @Test
    fun rejectsAVersionTheBuildDoesNotHave() {
        // Gradle's placeholder for "nobody set a version". A licence naming it would be a licence
        // parameter that means nothing.
        assertThrows(IllegalArgumentException::class.java) {
            LicenseTemplateSupport.releaseVersion("unspecified")
        }
        assertThrows(IllegalArgumentException::class.java) { LicenseTemplateSupport.releaseVersion("  ") }
    }

    // --- the Change Date ---------------------------------------------------

    @Test
    fun changeDateIsFourYearsFromTheBuildDate() {
        assertEquals("2030-09-04", LicenseTemplateSupport.changeDate(LocalDate.of(2026, 9, 4)))
    }

    @Test
    fun changeDateSurvivesTheLeapDay() {
        // +4 years from 29 February normally lands on another leap year...
        assertEquals("2028-02-29", LicenseTemplateSupport.changeDate(LocalDate.of(2024, 2, 29)))
        // ...except across a non-leap century, where LocalDate clamps to the 28th rather than
        // throwing. Recorded because a build that threw here would be a build that cannot run on
        // one particular day.
        assertEquals("2100-02-28", LicenseTemplateSupport.changeDate(LocalDate.of(2096, 2, 29)))
    }

    // --- filling -----------------------------------------------------------

    @Test
    fun fillsBothParametersEverywhereTheyAppear() {
        val filled =
            LicenseTemplateSupport.fill(
                "Licensed Work: NarrativeTrace version {{VERSION}}.\n" +
                    "Change Date: ({{CHANGE_DATE}} for this version)\n" +
                    "Second mention of {{VERSION}}.",
                "0.2.0",
                "2030-09-04"
            )

        assertEquals(
            "Licensed Work: NarrativeTrace version 0.2.0.\n" +
                "Change Date: (2030-09-04 for this version)\n" +
                "Second mention of 0.2.0.",
            filled
        )
    }

    @Test
    fun aParameterlessLicenceComesBackUnchanged() {
        // LICENSE-APACHE has no placeholders; the same code path packages it.
        val apache = "Apache License\nVersion 2.0, January 2004\n"
        assertEquals(apache, LicenseTemplateSupport.fill(apache, "0.2.0", "2030-09-04"))
    }

    // --- the gate ----------------------------------------------------------

    @Test
    fun aFinishedLicenceHasNoPlaceholders() {
        assertTrue(LicenseTemplateSupport.unfilledPlaceholders("Licensed Work version 0.2.0.").isEmpty())
    }

    @Test
    fun reportsEveryUnfilledPlaceholderWithItsLineAndText() {
        val problems =
            LicenseTemplateSupport.unfilledPlaceholders(
                "Business Source License 1.1\n" +
                    "  Licensed Work: version {{VERSION}}\n" +
                    "  fine line\n" +
                    "  Change Date: {{CHANGE_DATE}}\n"
            )

        assertEquals(
            listOf("2: Licensed Work: version {{VERSION}}", "4: Change Date: {{CHANGE_DATE}}"),
            problems
        )
    }

    @Test
    fun catchesAPlaceholderTheFillDoesNotKnowAbout() {
        // The pairing that matters: a licence change adding a third parameter must stop the build,
        // not ship a jar whose licence has a blank in it.
        assertEquals(
            listOf("1: Licensor: {{LICENSOR}}"),
            LicenseTemplateSupport.unfilledPlaceholders(
                LicenseTemplateSupport.fill("Licensor: {{LICENSOR}}", "0.2.0", "2030-09-04")
            )
        )
    }
}
