/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.frameworks;

import static ai.narrativetrace.tooling.frameworks.ManifestPatterns.dependency;
import static ai.narrativetrace.tooling.frameworks.ManifestPatterns.dependencyPrefix;
import static ai.narrativetrace.tooling.frameworks.ManifestPatterns.plugin;

import java.util.List;
import java.util.Optional;

/**
 * The framework table — Phase 6's D1: ONE committed registry of which frameworks NarrativeTrace
 * integrates with, how a project proves it uses one, which module it adds, how that module is
 * wired, and which doctor check watches the wiring.
 *
 * <p>INTENT: the table ships inside the tooling library the doctor runs from, so a project is
 * measured against the rows of the NarrativeTrace version it actually installed — never against the
 * repository or the live docs, which describe the newest release. The {@code add-narrative-tracing}
 * skill lists no framework at all; it runs the doctor and applies every {@code
 * config.<framework>-*} fix the doctor prints. The integration table in {@code llms-full.md} and
 * the covered-frameworks line in {@code llms.txt} render from these rows ({@link
 * FrameworkTableDocs}).
 *
 * <p><b>@llmNote</b> Adding a framework is adding a row here and, when its wiring is source-level,
 * a compiled fixture plus its entry in {@code wiring-snippets.md}: the doctor check, the docs and
 * the skill follow without new code. Row ids and check ids are a cross-port contract — append,
 * never rename.
 */
public final class FrameworkTable {

  private static final String GROUP = "ai.narrativetrace:";
  private static final String TEST_FIXTURES = "/src/test/java/com/example/";
  private static final String IMPLEMENTATION = "implementation";

  public static final List<FrameworkRow> ROWS =
      List.of(
          spring(),
          springWeb(),
          micronaut(),
          servlet(),
          junit4(),
          micrometer(),
          opentelemetry(),
          defaultLogger(),
          agent());

  private FrameworkTable() {}

  /** The rows that earn a doctor wiring check of their own, in table order. */
  public static List<FrameworkRow> rowsWithWiringChecks() {
    return ROWS.stream().filter(row -> row.check() instanceof CheckBinding.WiringCheck).toList();
  }

  /** The ids of those checks — every {@code config.<framework>-*} the doctor runs — in order. */
  public static List<String> wiringCheckIds() {
    return rowsWithWiringChecks().stream()
        .map(row -> ((CheckBinding.WiringCheck) row.check()).id())
        .toList();
  }

  /** The row with this id, if the table has one. */
  public static Optional<FrameworkRow> row(String id) {
    return ROWS.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  /**
   * Whether a manifest shows the row's framework: one of its marker patterns matches, and no row it
   * defers to is detected as well.
   *
   * @throws IllegalStateException when the row defers to an id the table has no row for — a typo
   *     there would otherwise make the row silently never stand down
   */
  public static boolean detected(FrameworkRow row, String manifestText) {
    boolean marked = row.marker().manifest().stream().anyMatch(p -> p.matcher(manifestText).find());
    return marked
        && row.marker().deferTo().stream()
            .map(
                id ->
                    row(id)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    row.id() + " defers to " + id + ", which is not a row")))
            .noneMatch(other -> detected(other, manifestText));
  }

  private static FrameworkRow spring() {
    return new FrameworkRow(
        "spring",
        "Spring",
        new Marker(
            "the org.springframework.boot plugin, a spring-boot-starter* dependency or"
                + " spring-context",
            List.of(
                plugin("org.springframework.boot"),
                dependencyPrefix("org.springframework.boot:spring-boot-starter"),
                dependency("org.springframework:spring-context")),
            List.of()),
        module("narrativetrace-spring", IMPLEMENTATION, new PluginDsl("mode", "\"spring\"", false)),
        new Wiring.Snippet(
            "@EnableNarrativeTrace(basePackages = …) on a @Configuration class",
            "narrativetrace-spring" + TEST_FIXTURES + "NarrativeTraceConfig.java",
            "wiring",
            "java",
            List.of(Evidence.matching("@EnableNarrativeTrace(?!\\w)"))),
        new CheckBinding.WiringCheck("config.spring-enabled"),
        "add-narrative-tracing/init-prompt-spring-boot-project");
  }

  private static FrameworkRow springWeb() {
    return new FrameworkRow(
        "spring-web",
        "Spring Web",
        new Marker(
            "a spring-boot-starter-web dependency",
            List.of(dependency("org.springframework.boot:spring-boot-starter-web")),
            List.of()),
        module(
            "narrativetrace-spring-web", IMPLEMENTATION, new PluginDsl("springWeb", "true", true)),
        new Wiring.Snippet(
            "@Import(NarrativeTraceWebConfiguration.class) next to @EnableNarrativeTrace",
            "narrativetrace-spring-web" + TEST_FIXTURES + "NarrativeTraceWebConfig.java",
            "wiring",
            "java",
            List.of(Evidence.matching("\\bNarrativeTraceWebConfiguration\\b"))),
        new CheckBinding.WiringCheck("config.spring-web-filter"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow micronaut() {
    return new FrameworkRow(
        "micronaut",
        "Micronaut",
        new Marker(
            "the io.micronaut.application plugin or a micronaut-inject dependency",
            List.of(
                plugin("io.micronaut.application"), dependency("io.micronaut:micronaut-inject")),
            List.of()),
        new IntegrationModule(
            List.of(GROUP + "narrativetrace-micronaut"),
            IMPLEMENTATION,
            List.of(GROUP + "narrativetrace-micronaut", GROUP + "narrativetrace-micronaut-http"),
            new PluginDsl("micronaut", "true", true)),
        new Wiring.Snippet(
            "narrativetrace.base-packages in application.properties or application.yml",
            "narrativetrace-micronaut/src/test/resources/wiring/application.properties",
            null,
            "properties",
            List.of(
                // application.properties, or a flattened YAML key, with a non-empty value.
                Evidence.matching(
                    "(?m)^[ \\t]*narrativetrace\\.base-packages[ \\t]*[=:][ \\t]*(?!\\[[ \\t]*\\])"
                        + "[^\\s#]"),
                // Nested YAML: base-packages under the top-level narrativetrace key.
                new Evidence.YamlKey("narrativetrace", "base-packages"))),
        new CheckBinding.WiringCheck("config.micronaut-base-packages"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow servlet() {
    return new FrameworkRow(
        "servlet",
        "Jakarta Servlet",
        new Marker(
            "a jakarta.servlet-api dependency without Spring or Micronaut",
            List.of(dependency("jakarta.servlet:jakarta.servlet-api")),
            List.of("spring", "micronaut")),
        module("narrativetrace-servlet", IMPLEMENTATION, new PluginDsl("servlet", "true", true)),
        new Wiring.Snippet(
            "NarrativeTraceFilter registered with the servlet container",
            "narrativetrace-servlet" + TEST_FIXTURES + "NarrativeTraceFilterSetup.java",
            "wiring",
            "java",
            List.of(Evidence.matching("\\bNarrativeTraceFilter\\b"))),
        new CheckBinding.WiringCheck("config.servlet-filter"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow junit4() {
    return new FrameworkRow(
        "junit4",
        "JUnit 4",
        new Marker("a junit:junit dependency", List.of(dependency("junit:junit")), List.of()),
        module(
            "narrativetrace-junit4",
            "testImplementation",
            new PluginDsl("testFramework", "\"junit4\"", false)),
        new Wiring.Snippet(
            "NarrativeTraceClassRule and the NarrativeTraceRule it creates, on the test class",
            "narrativetrace-junit4-example/src/test/java/ai/narrativetrace/examples/junit4/"
                + "GreetingServiceTest.java",
            null,
            "java",
            List.of(
                Evidence.matching(
                    "\\b(?:NarrativeTraceClassRule|NarrativeTraceRule|NarrativeTestCase)\\b"))),
        new CheckBinding.WiringCheck("config.junit4-rule"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow micrometer() {
    return new FrameworkRow(
        "micrometer",
        "Micrometer context propagation",
        new Marker(
            "an io.micrometer:context-propagation dependency",
            List.of(dependency("io.micrometer:context-propagation")),
            List.of()),
        module(
            "narrativetrace-micrometer", IMPLEMENTATION, new PluginDsl("micrometer", "true", true)),
        new Wiring.Snippet(
            "NarrativeTraceThreadLocalAccessor registered with the ContextRegistry",
            "narrativetrace-micrometer" + TEST_FIXTURES + "NarrativeTracePropagationSetup.java",
            "wiring",
            "java",
            List.of(Evidence.matching("\\bNarrativeTraceThreadLocalAccessor\\b"))),
        new CheckBinding.WiringCheck("config.micrometer-accessor"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow opentelemetry() {
    return new FrameworkRow(
        "opentelemetry",
        "OpenTelemetry",
        new Marker(
            "an io.opentelemetry:opentelemetry-api or -sdk dependency",
            List.of(
                dependency("io.opentelemetry:opentelemetry-api"),
                dependency("io.opentelemetry:opentelemetry-sdk")),
            List.of()),
        module(
            "narrativetrace-opentelemetry",
            IMPLEMENTATION,
            new PluginDsl("opentelemetry", "true", true)),
        new Wiring.Snippet(
            "OtelTraceEventListener attached to the context's pipeline",
            "narrativetrace-opentelemetry" + TEST_FIXTURES + "NarrativeTraceOtelSetup.java",
            "wiring",
            "java",
            List.of(Evidence.matching("\\b(?:OtelTraceEventListener|TraceSpanExporter)\\b"))),
        new CheckBinding.WiringCheck("config.otel-listener"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow defaultLogger() {
    return new FrameworkRow(
        "default-logger",
        "SLF4J / Logback",
        new Marker(
            "the default logger: no SLF4J binding on the runtime classpath", List.of(), List.of()),
        new IntegrationModule(
            List.of(GROUP + "narrativetrace-slf4j", "ch.qos.logback:logback-classic:1.5.38"),
            "runtimeOnly",
            List.of(GROUP + "narrativetrace-slf4j"),
            new PluginDsl("slf4j", "true", true)),
        new Wiring.DependenciesOnly(
            "the two runtimeOnly dependencies — narrativetrace-slf4j attaches itself"),
        new CheckBinding.ExistingCheck("trap.silent-sink"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static FrameworkRow agent() {
    return new FrameworkRow(
        "agent",
        "Java agent",
        new Marker("-javaagent wanted (the owner's opt-in, never automatic)", List.of(), List.of()),
        module("narrativetrace-agent", null, new PluginDsl("mode", "\"agent\"", false)),
        new Wiring.OwnerOptIn("a -javaagent JVM flag the owner adds"),
        new CheckBinding.NoCheck("runtime-only"),
        FrameworkRow.NO_TIER_B_CASE);
  }

  private static IntegrationModule module(String artifact, String configuration, PluginDsl plugin) {
    return new IntegrationModule(
        List.of(GROUP + artifact), configuration, List.of(GROUP + artifact), plugin);
  }
}
