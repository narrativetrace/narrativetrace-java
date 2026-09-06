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
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LicensingCategorySupportTest {

    @TempDir
    lateinit var repo: File

    private fun writeLicensing(content: String): File {
        val file = repo.resolve(LicensingCategorySupport.FILE_NAME)
        file.writeText(content)
        return file
    }

    // --- reading -----------------------------------------------------------

    @Test
    fun readsCategoriesAndIgnoresCommentsAndBlankLines() {
        writeLicensing(
            """
            # the contract
            module.narrativetrace-api=open

            module.narrativetrace-core=free
            """.trimIndent()
        )

        assertEquals(
            mapOf(
                "narrativetrace-api" to LicensingCategory.OPEN,
                "narrativetrace-core" to LicensingCategory.FREE
            ),
            LicensingCategorySupport.read(repo)
        )
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        writeLicensing("module.narrativetrace-api =  open  ")

        assertEquals(
            mapOf("narrativetrace-api" to LicensingCategory.OPEN),
            LicensingCategorySupport.read(repo)
        )
    }

    @Test
    fun readsAnExamplesSubprojectPath() {
        writeLicensing("module.narrativetrace-examples:ecommerce=free")

        assertEquals(
            mapOf("narrativetrace-examples:ecommerce" to LicensingCategory.FREE),
            LicensingCategorySupport.read(repo)
        )
    }

    @Test
    fun rejectsAMissingFile() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            LicensingCategorySupport.read(repo)
        }
        assertTrue(failure.message!!.contains("is missing"), failure.message)
    }

    @Test
    fun rejectsALineThatIsNotAModuleDeclaration() {
        writeLicensing("narrativetrace-api=open")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            LicensingCategorySupport.read(repo)
        }
        assertTrue(failure.message!!.contains(":1"), failure.message)
    }

    @Test
    fun rejectsAnEmptyModuleName() {
        writeLicensing("module.=open")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            LicensingCategorySupport.read(repo)
        }
        assertTrue(failure.message!!.contains("empty module name"), failure.message)
    }

    @Test
    fun rejectsADuplicateDeclaration() {
        writeLicensing(
            """
            module.narrativetrace-core=free
            module.narrativetrace-core=open
            """.trimIndent()
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            LicensingCategorySupport.read(repo)
        }
        assertTrue(failure.message!!.contains("twice"), failure.message)
    }

    @Test
    fun rejectsAnUnknownCategoryRatherThanGuessing() {
        writeLicensing("module.narrativetrace-core=pro")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            LicensingCategorySupport.read(repo)
        }
        assertTrue(failure.message!!.contains("unknown licensing category 'pro'"), failure.message)
    }

    // --- checking ----------------------------------------------------------

    private val declared = mapOf(
        "api" to LicensingCategory.OPEN,
        "core" to LicensingCategory.FREE,
        "servlet" to LicensingCategory.FREE
    )

    @Test
    fun acceptsAGraphThatRespectsTheDirection() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(
                ModuleLicensing("api", emptyList()),
                ModuleLicensing("core", listOf("api")),
                ModuleLicensing("servlet", listOf("core", "api"))
            )
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun rejectsAnOpenModuleThatDependsOnAFreeOne() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(
                ModuleLicensing("api", listOf("core")),
                ModuleLicensing("core", emptyList()),
                ModuleLicensing("servlet", emptyList())
            )
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("api (open) depends on core (free)"), problems[0])
        assertTrue(problems[0].contains("must depend on nothing"), problems[0])
    }

    @Test
    fun rejectsAnOpenModuleThatDependsOnAnotherOpenOneToo() {
        val twoOpen = mapOf("api" to LicensingCategory.OPEN, "spec" to LicensingCategory.OPEN)

        val problems = LicensingCategorySupport.check(
            twoOpen,
            listOf(ModuleLicensing("api", listOf("spec")), ModuleLicensing("spec", emptyList()))
        )

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].contains("api (open) depends on spec (open)"), problems[0])
    }

    @Test
    fun reportsAModuleWithNoDeclaration() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(
                ModuleLicensing("api", emptyList()),
                ModuleLicensing("core", emptyList()),
                ModuleLicensing("servlet", emptyList()),
                ModuleLicensing("uplift", emptyList())
            )
        )

        assertEquals(listOf("uplift has no line in licensing.properties"), problems)
    }

    @Test
    fun reportsADeclarationForAModuleThatNoLongerExists() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(ModuleLicensing("api", emptyList()), ModuleLicensing("core", emptyList()))
        )

        assertEquals(
            listOf("licensing.properties declares 'servlet', which is not a module of this build"),
            problems
        )
    }

    @Test
    fun ignoresDependenciesOnModulesOutsideThisBuild() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(
                ModuleLicensing("api", listOf("org.assertj:assertj-core")),
                ModuleLicensing("core", listOf("api")),
                ModuleLicensing("servlet", emptyList())
            )
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun reportsEachForbiddenEdgeOnceEvenWhenDeclaredTwice() {
        val problems = LicensingCategorySupport.check(
            declared,
            listOf(
                ModuleLicensing("api", listOf("core", "core")),
                ModuleLicensing("core", emptyList()),
                ModuleLicensing("servlet", emptyList())
            )
        )

        assertEquals(1, problems.size, problems.toString())
    }

    @Test
    fun problemsAreSortedSoTheReportIsStable() {
        val problems = LicensingCategorySupport.check(
            emptyMap(),
            listOf(ModuleLicensing("zulu", emptyList()), ModuleLicensing("alpha", emptyList()))
        )

        assertEquals(
            listOf(
                "alpha has no line in licensing.properties",
                "zulu has no line in licensing.properties"
            ),
            problems
        )
    }

    @Test
    fun openMayDependOnNothingAndFreeOnOpenAndFree() {
        assertEquals(emptySet<LicensingCategory>(), LicensingCategory.OPEN.mayDependOn())
        assertEquals(
            setOf(LicensingCategory.OPEN, LicensingCategory.FREE),
            LicensingCategory.FREE.mayDependOn()
        )
    }
}
