/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
subprojects {
    dependencies {
        "implementation"("org.slf4j:slf4j-api:2.0.16")
        "testImplementation"(project(":narrativetrace-junit5"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "runtimeOnly"("ch.qos.logback:logback-classic:1.5.38")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("network", "docker")
        }
    }

    fun Test.configureTraceOutput(format: String) {
        useJUnitPlatform {
            excludeTags("network", "docker")
        }
        val traceDir = layout.buildDirectory.dir("narrativetrace").get().asFile
        systemProperty("narrativetrace.output", "true")
        systemProperty("narrativetrace.outputDir", traceDir.absolutePath)
        systemProperty("narrativetrace.format", format)
        exclude("**/MarkdownDocumentTest.class")
        exclude("**/unrefactored/**")
        testLogging {
            showStandardStreams = true
        }
        doFirst {
            if (traceDir.exists()) {
                traceDir.deleteRecursively()
            }
        }
        doLast {
            if (traceDir.exists()) {
                val files = traceDir.walkTopDown().filter { it.isFile }.toList()
                if (files.isNotEmpty()) {
                    println("\n--- Trace files written (${files.size}) ---")
                    files.forEach { println("  ${it.absolutePath}") }
                    println("---")
                }
            }
        }
    }

    tasks.register<Test>("traceTests") {
        description = "Run tests and write Markdown trace files to build/narrativetrace/"
        configureTraceOutput("markdown")
    }

    tasks.register<Test>("traceTexts") {
        description = "Run tests and write indented text trace files to build/narrativetrace/"
        configureTraceOutput("text")
    }

    tasks.register<Test>("traceMermaid") {
        description = "Run tests and write Mermaid sequence diagram files to build/narrativetrace/"
        configureTraceOutput("mermaid")
    }

    tasks.register<Test>("tracePlantUml") {
        description = "Run tests and write PlantUML sequence diagram files to build/narrativetrace/"
        configureTraceOutput("plantuml")
    }

    tasks.register<JavaExec>("renderDiagrams") {
        description = "Run tracePlantUml then render .puml files to .svg images"
        dependsOn("tracePlantUml", ":narrativetrace-examples:common:classes")
        mainClass.set("ai.narrativetrace.examples.PlantUmlImageRenderer")
        classpath =
            project(":narrativetrace-examples:common")
                .extensions
                .getByType<SourceSetContainer>()["main"]
                .runtimeClasspath
        args = listOf(layout.buildDirectory.dir("narrativetrace").get().asFile.absolutePath)
    }
}

tasks.register<Exec>("demoRecording") {
    description = "Record the demo GIF with charmbracelet vhs (see demo/demo.tape)"
    workingDir = rootDir
    commandLine("vhs", "narrativetrace-examples/demo/demo.tape")
    doFirst {
        // Exec would fail with an opaque ENOENT; make the missing prerequisite readable.
        val vhsOnPath = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "vhs").canExecute() }
        if (!vhsOnPath) {
            throw GradleException("`vhs` is not installed (brew install vhs) — cannot record the demo")
        }
    }
}

tasks.register("runExamples") {
    description = "Run all example applications"
    dependsOn(
        ":narrativetrace-examples:ecommerce:run",
        ":narrativetrace-examples:minecraft:run",
        ":narrativetrace-examples:library:run",
        ":narrativetrace-examples:clarity:run"
    )
}

tasks.register("traceExamples") {
    description = "Run Markdown trace output for all example test suites"
    dependsOn(
        ":narrativetrace-examples:ecommerce:traceTests",
        ":narrativetrace-examples:minecraft:traceTests",
        ":narrativetrace-examples:library:traceTests",
        ":narrativetrace-examples:clarity:traceTests"
    )
}
