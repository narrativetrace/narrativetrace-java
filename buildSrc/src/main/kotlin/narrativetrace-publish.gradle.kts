/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    `maven-publish`
    signing
}

// A published module's jars carry their own licence and NOTICE in META-INF. Applied here rather
// than left to each module: "publishes an artifact" and "must ship the licence with it" are the
// same fact, and a module that could do the first while opting out of the second is a bug waiting
// for a release. See `narrativetrace-license-packaging` for which text goes where, and why.
apply(plugin = "narrativetrace-license-packaging")

extensions.configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(provider { project.extra["publishName"] as String })
                description.set(provider { project.extra["publishDescription"] as String })
                url.set("https://narrativetrace.ai")
                // The licence a module publishes under is a build fact, not a constant:
                // licensing.properties says which category each module is in, and the same
                // file drives the source-header stamp and the `licensingCheck` gate. A POM
                // that disagreed with the headers inside the jar would be the worst kind of
                // licence bug — the one a consumer only finds after they have shipped.
                val licensingCategory = providers.provider {
                    ai.narrativetrace.build.LicensingCategorySupport
                        .read(project.rootDir)[project.path.removePrefix(":")]
                        ?: throw GradleException(
                            "${project.path} has no line in " +
                                ai.narrativetrace.build.LicensingCategorySupport.FILE_NAME
                        )
                }
                licenses {
                    license {
                        name.set(
                            licensingCategory.map {
                                when (it) {
                                    ai.narrativetrace.build.LicensingCategory.OPEN ->
                                        "The Apache License, Version 2.0"
                                    ai.narrativetrace.build.LicensingCategory.FREE ->
                                        "Business Source License 1.1"
                                }
                            }
                        )
                        url.set(
                            licensingCategory.map {
                                when (it) {
                                    ai.narrativetrace.build.LicensingCategory.OPEN ->
                                        "https://www.apache.org/licenses/LICENSE-2.0.txt"
                                    // Until the licence text is hosted on narrativetrace.ai
                                    // (Phase 28), point at the canonical BSL 1.1 text.
                                    ai.narrativetrace.build.LicensingCategory.FREE ->
                                        "https://mariadb.com/bsl11/"
                                }
                            }
                        )
                        comments.set(
                            licensingCategory.map {
                                when (it) {
                                    ai.narrativetrace.build.LicensingCategory.OPEN ->
                                        "The API and output-format contract, licensed as an open " +
                                            "standard so any implementation can target it."
                                    ai.narrativetrace.build.LicensingCategory.FREE ->
                                        "Free to use in production, source-available. Change " +
                                            "Date: four years from the date this version is " +
                                            "published. Change License: Apache License, " +
                                            "Version 2.0."
                                }
                            }
                        )
                    }
                }
                developers {
                    developer {
                        id.set("danijel")
                        name.set("Danijel Arsenovski")
                        email.set("danijel.arsenovski@empoweragile.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/narrativetrace/narrativetrace-java.git")
                    developerConnection.set("scm:git:ssh://github.com/narrativetrace/narrativetrace-java.git")
                    url.set("https://github.com/narrativetrace/narrativetrace-java")
                }
            }
        }
    }
}

// Two signing modes, chosen by environment:
//  - CI (public release workflow): the ASCII-armored private key and its passphrase
//    arrive as GPG_PRIVATE_KEY / GPG_PASSPHRASE from the repository's Actions
//    secrets — no gpg agent, no keyring, nothing on disk.
//  - Maintainer machine: the local gpg command and keyring, exactly as before.
val inMemoryKey: String? = System.getenv("GPG_PRIVATE_KEY")?.takeIf { it.isNotBlank() }
signing {
    if (inMemoryKey != null) {
        useInMemoryPgpKeys(inMemoryKey, System.getenv("GPG_PASSPHRASE") ?: "")
    } else {
        useGpgCmd()
    }
    sign(publishing.publications["mavenJava"])
}

// Sign only when credentials are present: the in-memory key in CI, or the maintainer's
// `secret.properties` locally. `-PskipSigning` cleanly disables signing for a local
// `publishToMavenLocal` when `secret.properties` exists but GPG cannot prompt (interactive
// passphrase). Skipping the Sign tasks via `onlyIf` is the proven path and, unlike
// `-x signMavenJavaPublication`, it does not leave the publication expecting `.asc` artifacts.
val skipSigning = providers.gradleProperty("skipSigning").isPresent
val signingCredentialsPresent = inMemoryKey != null || rootProject.file("secret.properties").exists()
tasks.withType<Sign>().configureEach {
    onlyIf { !skipSigning && signingCredentialsPresent }
}
