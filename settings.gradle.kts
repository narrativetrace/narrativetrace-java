/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("com.gradleup.nmcp.settings").version("1.4.4")
}

// Central Portal credentials: a maintainer's local `secret.properties` (git-secret,
// never committed in clear) or, in the public release workflow, environment
// variables populated from the repository's Actions secrets. Env wins when set so
// CI never depends on a file that does not exist there.
val secrets = java.util.Properties().apply {
    val file = file("secret.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: secrets.getProperty(propertyName)

nmcpSettings {
    centralPortal {
        username = secret("MAVEN_CENTRAL_USERNAME", "mavenCentralUsername")
        password = secret("MAVEN_CENTRAL_PASSWORD", "mavenCentralPassword")
        // AUTOMATIC: a validated bundle is released without a Portal click. The
        // human gate lives in front — the GitHub `release` environment's required
        // reviewer approves the publish job before any credential is exposed.
        publishingType = "AUTOMATIC"
    }
}

rootProject.name = "narrativetrace-java"

include("narrativetrace-api")
include("narrativetrace-core")
include("narrativetrace-proxy")
include("narrativetrace-junit5")
include("narrativetrace-examples")
include("narrativetrace-examples:clarity")
include("narrativetrace-examples:common")
include("narrativetrace-examples:ecommerce")
include("narrativetrace-examples:ejb4")
include("narrativetrace-examples:library")
include("narrativetrace-examples:minecraft")
include("narrativetrace-clarity")
include("narrativetrace-glossary")
include("narrativetrace-diagrams")
include("narrativetrace-slf4j")
include("narrativetrace-agent")
include("narrativetrace-spring")
include("narrativetrace-benchmarks")
include("narrativetrace-micrometer")
include("narrativetrace-junit4")
include("narrativetrace-junit4-example")
include("narrativetrace-agent-example")
include("narrativetrace-build-tests")
include("narrativetrace-security-tests")
include("narrativetrace-gradle-plugin")
include("narrativetrace-servlet")
include("narrativetrace-spring-web")
include("narrativetrace-opentelemetry")
include("narrativetrace-micronaut")
include("narrativetrace-micronaut-http")
include("narrativetrace-jcstress")
