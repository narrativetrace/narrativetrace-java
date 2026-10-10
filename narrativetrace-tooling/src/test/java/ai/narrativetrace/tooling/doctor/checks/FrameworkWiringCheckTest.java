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
package ai.narrativetrace.tooling.doctor.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import ai.narrativetrace.tooling.frameworks.Wiring;
import ai.narrativetrace.tooling.frameworks.WiringSnippets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FrameworkWiringCheckTest {

  private static final String CORE =
      "implementation(\"ai.narrativetrace:narrativetrace-core:0.2.5\")";

  private final FrameworkWiringCheck spring = check("spring");

  @Test
  void passesWhenTheFrameworkIsAbsent() {
    Finding f = spring.run(DoctorSnapshot.builder().build());
    assertThat(f.id()).isEqualTo("config.spring-enabled");
    assertThat(f.isFailing()).isFalse();
    assertThat(f.message()).contains("Spring").contains("not detected");
  }

  @Test
  void failsWhenTheFrameworkIsPresentButTheModuleIsNotReferenced() {
    Finding f =
        spring.run(manifest("plugins { id(\"org.springframework.boot\") version \"3.4.1\" }"));
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("Spring detected").contains("narrativetrace-spring");
    assertThat(f.fix())
        .contains("narrativeTrace { scope.set(\"production\"); mode.set(\"spring\") }")
        .contains("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")")
        .contains("@EnableNarrativeTrace(basePackages");
  }

  @Test
  void failsWhenTheModuleIsReferencedButTheWiringWasNeverApplied() {
    Finding f =
        spring.run(manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")"));
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("is referenced").contains("never applied");
    assertThat(f.fix()).startsWith("Apply the wiring").contains(WiringSnippets.text("spring"));
  }

  @Test
  void passesOnceTheWiringIsInASourceFile() {
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")").toBuilder()
            .putSourceFile(
                "src/main/java/com/acme/TraceConfig.java",
                "@Configuration @EnableNarrativeTrace(basePackages = \"com.acme\") class T {}")
            .build();
    Finding f = spring.run(s);
    assertThat(f.isFailing()).isFalse();
    assertThat(f.message()).contains("is wired");
  }

  @Test
  void anAnnotationThatOnlySharesThePrefixIsNotTheWiring() {
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")").toBuilder()
            .putSourceFile("src/main/java/com/acme/T.java", "@EnableNarrativeTraceLater class T {}")
            .build();
    assertThat(spring.run(s).isFailing()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "// @EnableNarrativeTrace(basePackages = \"com.acme\")\nclass T {}",
        "/* @EnableNarrativeTrace(basePackages = \"com.acme\") */ class T {}",
        "/**\n * Add @EnableNarrativeTrace(basePackages = \"com.acme\") here.\n */\nclass T {}",
        "class T {} // TODO @EnableNarrativeTrace",
      })
  void aCommentThatNamesTheAnnotationIsNotTheWiring(String source) {
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")").toBuilder()
            .putSourceFile("src/main/java/com/acme/T.java", source)
            .build();
    Finding f = spring.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("never applied");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/* the config */ @EnableNarrativeTrace(basePackages = \"com.acme\") class T {}",
        "// the config\n@EnableNarrativeTrace(basePackages = \"com.acme\") class T {}",
        "String u = \"http://x\"; @EnableNarrativeTrace class T {}",
        "char c = '\"'; /* \" */ @EnableNarrativeTrace class T {}",
      })
  void theAnnotationNextToACommentOrAQuotedSlashStillCounts(String source) {
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")").toBuilder()
            .putSourceFile("src/main/java/com/acme/T.java", source)
            .build();
    assertThat(spring.run(s).isFailing()).isFalse();
  }

  @Test
  void aCommentNamingTheFilterIsNotTheServletWiringEither() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent(
                CORE
                    + "\nimplementation(\"jakarta.servlet:jakarta.servlet-api:6.0.0\")\n"
                    + "implementation(\"ai.narrativetrace:narrativetrace-servlet:0.2.5\")")
            .putSourceFile(
                "src/main/java/com/acme/T.java", "// register NarrativeTraceFilter\nclass T {}")
            .build();
    assertThat(check("servlet").run(s).isFailing()).isTrue();
  }

  @Test
  void aModuleThatOnlySharesThePrefixIsNotTheModule() {
    Finding f =
        spring.run(
            manifest(
                "plugins { id(\"org.springframework.boot\") }\n"
                    + "implementation(\"ai.narrativetrace:narrativetrace-spring-extras:0.2.5\")"));
    assertThat(f.message()).contains("is not referenced");
  }

  @Test
  void aMarkerOutsideQuotesIsNotAMarker() {
    Finding f = spring.run(manifest("// we used org.springframework.boot once, not any more"));
    assertThat(f.message()).contains("not detected");
  }

  @ParameterizedTest
  @ValueSource(strings = {"mode.set(\"spring\")", "mode = 'spring'", "mode = \"spring\""})
  void thePluginSettingCountsAsReferencingTheModule(String setting) {
    Finding f = spring.run(manifest("narrativeTrace { " + setting + " }"));
    assertThat(f.message()).contains("never applied");
  }

  @Test
  void aPluginSettingForADifferentModeIsNotAReference() {
    Finding f =
        spring.run(
            manifest(
                "plugins { id(\"org.springframework.boot\") }\n"
                    + "narrativeTrace { mode.set(\"springy\") }"));
    assertThat(f.message()).contains("is not referenced");
  }

  @Test
  void theFixPinsTheModuleToTheVersionThePluginDeclares() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent(
                "plugins { id(\"ai.narrativetrace\") version \"9.8.7\"\n"
                    + " id(\"org.springframework.boot\") }")
            .build();
    assertThat(spring.run(s).fix())
        .contains("implementation(\"ai.narrativetrace:narrativetrace-spring:9.8.7\")");
  }

  @Test
  void theFixNamesAPlaceholderWhenTheProjectDeclaresNoNarrativeTraceVersion() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent("plugins { id(\"org.springframework.boot\") }")
            .build();
    assertThat(spring.run(s).fix())
        .contains("ai.narrativetrace:narrativetrace-spring:<your NarrativeTrace version>");
  }

  @Test
  void aMarkerInAModuleBuildFileCounts() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putManifestFile(
                "app/build.gradle.kts",
                "dependencies { implementation(\"org.springframework.boot:spring-boot-starter\") }")
            .build();
    assertThat(spring.run(s).message()).contains("Spring detected");
  }

  @Test
  void wiringInAKotlinSourceCounts() {
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.5\")").toBuilder()
            .putResourceFile(
                "src/main/kotlin/com/acme/TraceConfig.kt",
                "@Configuration\n@EnableNarrativeTrace(basePackages = [\"com.acme\"])\nclass T")
            .build();
    assertThat(spring.run(s).isFailing()).isFalse();
  }

  @Test
  void theServletRowStandsDownForASpringApplication() {
    FrameworkWiringCheck servlet = check("servlet");
    DoctorSnapshot s =
        manifest(
            "implementation(\"jakarta.servlet:jakarta.servlet-api:6.0.0\")\n"
                + "implementation(\"org.springframework.boot:spring-boot-starter-web\")");
    assertThat(servlet.run(s).message()).contains("not detected");
  }

  @Test
  void theServletRowStillChecksAModuleTheProjectChoseExplicitly() {
    FrameworkWiringCheck servlet = check("servlet");
    DoctorSnapshot s =
        manifest(
            "implementation(\"org.springframework.boot:spring-boot-starter-web\")\n"
                + "implementation(\"ai.narrativetrace:narrativetrace-servlet:0.2.5\")");
    assertThat(servlet.run(s).message()).contains("never applied");
  }

  @Test
  void aJunit4SuiteThePluginConfiguredStillNeedsItsRules() {
    FrameworkWiringCheck junit4 = check("junit4");
    DoctorSnapshot s = manifest("narrativeTrace {\n    testFramework.set(\"junit4\")\n}");
    Finding f = junit4.run(s);
    assertThat(f.id()).isEqualTo("config.junit4-rule");
    assertThat(f.message()).contains("never applied");
    assertThat(f.fix()).contains("NarrativeTraceClassRule");
  }

  @Test
  void theMicronautBasePackagesKeyWithAnEmptyListIsNotTheWiring() {
    FrameworkWiringCheck micronaut = check("micronaut");
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-micronaut:0.2.5\")").toBuilder()
            .putResourceFile(
                "src/main/resources/application.yml", "narrativetrace:\n  base-packages: []\n")
            .build();
    assertThat(micronaut.run(s).isFailing()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativetrace.base-packages=com.acme\n",
        "narrativetrace:\n  base-packages:\n    - com.acme\n",
        "narrativetrace:\n  logger-name: x\n  base-packages: com.acme\n",
      })
  void theMicronautBasePackagesKeyInEitherFormatIsTheWiring(String config) {
    FrameworkWiringCheck micronaut = check("micronaut");
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-micronaut-http:0.2.5\")")
            .toBuilder()
            .putResourceFile("src/main/resources/application.cfg", config)
            .build();
    assertThat(micronaut.run(s).isFailing()).isFalse();
  }

  @Test
  void aBasePackagesKeyUnderAnotherTopLevelKeyIsNotTheWiring() {
    FrameworkWiringCheck micronaut = check("micronaut");
    DoctorSnapshot s =
        manifest("implementation(\"ai.narrativetrace:narrativetrace-micronaut:0.2.5\")").toBuilder()
            .putResourceFile(
                "src/main/resources/application.yml",
                "narrativetrace:\n  logger-name: x\nother:\n  base-packages:\n    - com.acme\n")
            .build();
    assertThat(micronaut.run(s).isFailing()).isTrue();
  }

  /**
   * Every row that earns a check, both failure modes and the cure: a manifest with only the
   * framework's marker fails "not referenced"; the module alone fails "never applied" with the
   * row's snippet in the fix; and the snippet's own text, dropped into the project, satisfies the
   * check — the fix the doctor prints is the fix the doctor accepts.
   */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "spring        | implementation(\"org.springframework:spring-context:6.2.0\")",
        "spring-web    | implementation(\"org.springframework.boot:spring-boot-starter-web\")",
        "micronaut     | plugins { id(\"io.micronaut.application\") version \"4.5.0\" }",
        "servlet       | compileOnly(\"jakarta.servlet:jakarta.servlet-api:6.0.0\")",
        "junit4        | testImplementation(\"junit:junit:4.13.2\")",
        "micrometer    | implementation(\"io.micrometer:context-propagation:1.1.2\")",
        "opentelemetry | implementation(\"io.opentelemetry:opentelemetry-api:1.62.0\")",
      })
  void everyCheckedRowFailsBothWaysAndAcceptsItsOwnSnippet(String rowId, String marker) {
    FrameworkRow row = FrameworkTable.row(rowId).orElseThrow();
    FrameworkWiringCheck check = new FrameworkWiringCheck(row);
    String moduleLine = row.module().dependencyLines("0.2.5").get(0);

    Finding unreferenced = check.run(manifest(marker));
    assertThat(unreferenced.message()).contains("detected").contains("is not referenced");
    assertThat(unreferenced.fix()).contains(moduleLine).contains(WiringSnippets.text(rowId));

    Finding unwired = check.run(manifest(marker + "\n" + moduleLine));
    assertThat(unwired.message()).contains("never applied");

    String fixture = ((Wiring.Snippet) row.wiring()).fixture();
    DoctorSnapshot cured =
        manifest(marker + "\n" + moduleLine).toBuilder()
            .putResourceFile(fixture, WiringSnippets.text(rowId))
            .build();
    assertThat(check.run(cured).isFailing()).as(rowId).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"default-logger", "agent"})
  void aRowWithoutSourceWiringEarnsNoCheckOfItsOwn(String rowId) {
    FrameworkRow row = FrameworkTable.row(rowId).orElseThrow();
    assertThatThrownBy(() -> new FrameworkWiringCheck(row))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(rowId);
  }

  private static FrameworkWiringCheck check(String rowId) {
    return new FrameworkWiringCheck(FrameworkTable.row(rowId).orElseThrow());
  }

  private static DoctorSnapshot manifest(String lines) {
    return DoctorSnapshot.builder().buildFileContent(CORE + "\n" + lines).build();
  }
}
