/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * The licence a module ships under, as a build property.
 *
 * INTENT: Backs the root `licensingCheck` task. NarrativeTrace publishes two licences from one
 * tree — Apache 2.0 for the contract, BSL 1.1 for the runtime — and three separate mechanisms have
 * to agree about which is which: the POM's `<licenses>` block, the source-header stamp in
 * `scripts/publish-public.sh`, and this check. `licensing.properties` is the single fact all three
 * read, so a module cannot be published under a licence its build never agreed to.
 *
 * The second half is the part a human cannot police by review: **dependency direction**. A licence
 * is only as strong as the graph beneath it. An `open` module that took a `free` dependency would
 * hand every consumer of the open contract a BSL jar they never agreed to; the check makes that a
 * build failure instead of a discovery.
 */
enum class LicensingCategory(val propertyValue: String) {

    /** Apache License 2.0 — the contract third parties compile against. Depends on nothing. */
    OPEN("open"),

    /** Business Source License 1.1 — the runtime. May depend on `open` and `free` only. */
    FREE("free");

    /** Categories a module of this category is permitted to depend on. */
    fun mayDependOn(): Set<LicensingCategory> =
        when (this) {
            OPEN -> emptySet()
            FREE -> setOf(OPEN, FREE)
        }

    companion object {
        fun parse(value: String): LicensingCategory =
            values().firstOrNull { it.propertyValue == value.trim() }
                ?: throw IllegalArgumentException(
                    "unknown licensing category '$value' (expected one of " +
                        values().joinToString(", ") { it.propertyValue } + ")"
                )
    }
}

/** One module's declared category, plus the modules of this build it depends on. */
data class ModuleLicensing(val module: String, val dependencies: List<String>)

object LicensingCategorySupport {

    const val FILE_NAME = "licensing.properties"

    private const val KEY_PREFIX = "module."

    /**
     * Reads `licensing.properties` into module name → category.
     *
     * @throws IllegalArgumentException when the file is missing, a key is malformed, a module is
     *     declared twice, or a value is not a known category
     */
    fun read(repoRoot: File): Map<String, LicensingCategory> = readText(licensingFile(repoRoot))

    /** Same as [read], for a file whose location the caller already resolved. */
    fun readText(file: File): Map<String, LicensingCategory> {
        if (!file.isFile) {
            throw IllegalArgumentException("${file.name} is missing at ${file.path}")
        }
        val categories = LinkedHashMap<String, LicensingCategory>()
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val separator = line.indexOf('=')
            if (!line.startsWith(KEY_PREFIX) || separator < 0) {
                throw IllegalArgumentException(
                    "${file.name}:${index + 1} is not a `$KEY_PREFIX<module>=<category>` line: $line"
                )
            }
            val module = line.substring(KEY_PREFIX.length, separator).trim()
            if (module.isEmpty()) {
                throw IllegalArgumentException("${file.name}:${index + 1} declares an empty module name")
            }
            if (categories.containsKey(module)) {
                throw IllegalArgumentException("${file.name}:${index + 1} declares '$module' twice")
            }
            categories[module] = LicensingCategory.parse(line.substring(separator + 1))
        }
        return categories
    }

    /**
     * Verifies coverage and dependency direction; returns problems sorted, empty when the graph is
     * licensable as declared.
     *
     * @param declared module → category, from [read]
     * @param modules every subproject of the build, with the in-build modules each depends on
     */
    fun check(
        declared: Map<String, LicensingCategory>,
        modules: List<ModuleLicensing>
    ): List<String> {
        val problems = mutableListOf<String>()
        problems += missingDeclarations(declared, modules)
        problems += unknownDeclarations(declared, modules)
        problems += forbiddenDependencies(declared, modules)
        return problems.sorted()
    }

    /** Every subproject needs a line: an undeclared module is one nobody chose a licence for. */
    private fun missingDeclarations(
        declared: Map<String, LicensingCategory>,
        modules: List<ModuleLicensing>
    ): List<String> =
        modules.map { it.module }.filterNot { declared.containsKey(it) }
            .map { "$it has no line in $FILE_NAME" }

    /** A line for a module that no longer exists is a licence decision about nothing. */
    private fun unknownDeclarations(
        declared: Map<String, LicensingCategory>,
        modules: List<ModuleLicensing>
    ): List<String> {
        val present = modules.map { it.module }.toSet()
        return declared.keys.filterNot { present.contains(it) }
            .map { "$FILE_NAME declares '$it', which is not a module of this build" }
    }

    private fun forbiddenDependencies(
        declared: Map<String, LicensingCategory>,
        modules: List<ModuleLicensing>
    ): List<String> {
        val problems = mutableListOf<String>()
        for (module in modules) {
            val category = declared[module.module] ?: continue
            val allowed = category.mayDependOn()
            for (dependency in module.dependencies.distinct()) {
                val dependencyCategory = declared[dependency] ?: continue
                if (!allowed.contains(dependencyCategory)) {
                    problems += "${module.module} (${category.propertyValue}) depends on " +
                        "$dependency (${dependencyCategory.propertyValue}), which " +
                        forbiddenReason(category)
                }
            }
        }
        return problems
    }

    private fun forbiddenReason(category: LicensingCategory): String =
        when (category) {
            LicensingCategory.OPEN ->
                "an `open` module may never do — the contract must depend on nothing"
            LicensingCategory.FREE -> "a `free` module may not depend on"
        }

    private fun licensingFile(repoRoot: File) = repoRoot.resolve(FILE_NAME)
}
