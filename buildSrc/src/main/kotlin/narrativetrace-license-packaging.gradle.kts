/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
import ai.narrativetrace.build.LicenseTemplateSupport
import ai.narrativetrace.build.LicensingCategory
import ai.narrativetrace.build.LicensingCategorySupport
import java.time.LocalDate
import java.util.zip.ZipFile

// Puts the licence inside the artifact.
//
// A Maven jar is a copy of the Licensed Work handed to someone who may never see this repository.
// Apache-2.0 §4(a) requires that recipient to get a copy of the License with it; BSL 1.1 requires
// the License to be "conspicuously displayed on each original or modified copy of the Licensed
// Work". Neither was true of any artifact this build produced: the licence lived at the repository
// root and stopped there.
//
// Which text a jar gets is not a constant, it is the module's licensing category — the same
// `licensing.properties` fact that drives the POM `<licenses>` block, the publish-time source-header
// stamp and the `licensingCheck` gate. An `open` module ships LICENSE-APACHE, a `free` module ships
// LICENSE. Every jar also ships NOTICE, which is what tells a reader which of the two applies to
// what and states the Apache carve-out for the schemas.
//
// LICENSE is a TEMPLATE, so the copy is not a straight copy: BSL's Licensed Work version and Change
// Date are per-version parameters, filled here on the same arithmetic scripts/publish-public.sh uses
// (four years from the build date). A licence packaged with "{{VERSION}}" still in it names no
// version at all, so anything still matching `{{...}}` after the fill fails the build.
//
// Applied by `narrativetrace-publish` (every library module) and directly by
// narrativetrace-gradle-plugin, which publishes to the Gradle Plugin Portal on its own wiring and
// would otherwise be the one published artifact with no licence in it.

val licensingCategory: LicensingCategory =
    LicensingCategorySupport.read(rootProject.rootDir)[project.path.removePrefix(":")]
        ?: throw GradleException(
            "${project.path} has no line in ${LicensingCategorySupport.FILE_NAME} — " +
                "its jars cannot be given a licence nobody has declared"
        )

// The packaged file keeps the name NOTICE cross-references ("see LICENSE-APACHE", "see LICENSE"),
// so a jar unzipped on its own is internally consistent: the pointer and the file agree.
val licenceTemplate: File =
    when (licensingCategory) {
        LicensingCategory.OPEN -> rootProject.file("LICENSE-APACHE")
        LicensingCategory.FREE -> rootProject.file("LICENSE")
    }
val noticeFile: File = rootProject.file("NOTICE")

// Read at configuration time, not inside doLast: reaching for `project` during execution is
// deprecated and fails outright under the configuration cache. The root build's `subprojects {}`
// block has set the version long before this plugin is applied from a module's build script.
val releaseVersion: String = LicenseTemplateSupport.releaseVersion(project.version.toString())
val changeDate: String = LicenseTemplateSupport.changeDate(LocalDate.now())

val licenceMetaInf =
    tasks.register("licenseMetaInf") {
        description = "Fills the licence template for this version and stages it, with NOTICE, for META-INF"
        group = "build"
        val template = licenceTemplate
        val notice = noticeFile
        val version = releaseVersion
        val conversionDate = changeDate
        val stagingDir = layout.buildDirectory.dir("generated/license-metainf")
        inputs.file(template).withPropertyName("licenceTemplate")
        inputs.file(notice).withPropertyName("notice")
        inputs.property("version", version)
        inputs.property("changeDate", conversionDate)
        outputs.dir(stagingDir).withPropertyName("stagedLicenceFiles")
        doLast {
            val filled = LicenseTemplateSupport.fill(template.readText(), version, conversionDate)
            val unfilled = LicenseTemplateSupport.unfilledPlaceholders(filled)
            if (unfilled.isNotEmpty()) {
                throw GradleException(
                    "${template.name} still has unfilled placeholders after templating — a jar may " +
                        "not ship a licence with a blank where a parameter belongs:\n" +
                        unfilled.joinToString("\n") { "  ${template.name}:$it" }
                )
            }
            val target = stagingDir.get().asFile
            target.mkdirs()
            target.resolve(template.name).writeText(filled)
            target.resolve(notice.name).writeText(notice.readText())
        }
    }

// Every Jar this module produces, not only the main one: the sources and javadoc jars are separately
// downloadable copies of the work, and the agent's shadow and standalone jars are the artifacts a
// legacy host actually attaches. A copy is a copy.
tasks.withType<Jar>().configureEach {
    metaInf { from(licenceMetaInf) }
}

val verifyJarLicence =
    tasks.register("verifyJarLicense") {
        description = "Verifies the built jar carries NOTICE and a fully filled licence in META-INF"
        group = "verification"
        val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        val licenceEntry = "META-INF/${licenceTemplate.name}"
        val noticeEntry = "META-INF/${noticeFile.name}"
        val stamp = layout.buildDirectory.file("tmp/verifyJarLicense/verified.txt")
        inputs.file(jarFile).withPropertyName("jar")
        outputs.file(stamp).withPropertyName("stamp")
        doLast {
            val jar = jarFile.get().asFile
            ZipFile(jar).use { zip ->
                listOf(licenceEntry, noticeEntry).forEach { name ->
                    val entry =
                        zip.getEntry(name)
                            ?: throw GradleException("${jar.name} does not carry $name")
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    if (text.isBlank()) {
                        throw GradleException("${jar.name} carries an empty $name")
                    }
                    val unfilled = LicenseTemplateSupport.unfilledPlaceholders(text)
                    if (unfilled.isNotEmpty()) {
                        throw GradleException(
                            "${jar.name}'s $name has unfilled placeholders:\n" +
                                unfilled.joinToString("\n") { "  $it" }
                        )
                    }
                }
            }
            val stampFile = stamp.get().asFile
            stampFile.parentFile.mkdirs()
            stampFile.writeText("$licenceEntry and $noticeEntry present and filled in ${jar.name}\n")
        }
    }

tasks.named("check") {
    dependsOn(verifyJarLicence)
}
