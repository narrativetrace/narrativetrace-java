/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * Proves the CARRIER is inside the artifact, not just in the source tree.
 *
 * <p>What an installer opens is a jar, so this test opens the real archives the build produced
 * rather than reading {@code src/main/resources} back — a fixture jar assembled by the test would
 * prove only that the test can build a jar. The build file makes both jars a dependency of this
 * test.
 *
 * <p>Two jars, because the carrier ships twice on purpose (Phase 2 D4): {@code
 * narrativetrace-skills} is the carrier a Gradle plugin resolves as an ordinary dependency, and
 * {@code narrativetrace-cli} bundles the identical resources so {@code java -jar
 * narrativetrace-cli.jar} works offline from the one jar a consumer already fetched. "Identical" is
 * asserted byte-for-byte here: two copies that could drift are two carriers.
 */
class SkillsCarrierJarTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  private static final String BUILD_VERSION = System.getProperty("narrativetrace.buildVersion");

  /** The carrier's root inside either jar — mirrors {@code RenderPaths.CARRIER_JAR_ROOT}. */
  private static final String ROOT = "META-INF/narrativetrace/skills/";

  /**
   * The carrier directory each rendered layout's pages are copied into: {@code .agents} → {@code
   * agents/}, {@code .claude} → {@code claude/}. The pages themselves are the ones {@code
   * RenderDriftTest} already holds byte-for-byte against the typed catalogue, so this test's
   * expectation reaches the catalogue through a gate rather than restating it.
   */
  private static final Map<String, String> CARRIER_FLAVOURS =
      Map.of("agents", ".agents", "claude", ".claude");

  /**
   * Every entry the carrier must hold: {@code catalogue.json} plus one {@code SKILL.md} per skill
   * per flavour, DERIVED from the rendered layouts rather than listed here.
   *
   * <p>Listed, it went stale the moment a fourth skill shipped — and then stayed green through a
   * whole {@code check}, because this test reads {@code build/libs/*.jar}, the jar on disk still
   * held three, and nothing re-ran the test to disagree with either. The same stale-read shape that
   * hid three other drift tests in the milestone before this one. Derived, a fifth skill needs no
   * edit here, and a page that is in the tree and NOT in the jar still fails.
   */
  private static List<String> expectedEntries() {
    List<String> entries = new ArrayList<>(List.of(ROOT + "catalogue.json"));
    CARRIER_FLAVOURS.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            flavour ->
                renderedSkillNames(flavour.getValue())
                    .forEach(
                        skill -> entries.add(ROOT + flavour.getKey() + "/" + skill + "/SKILL.md")));
    return List.copyOf(entries);
  }

  /** The skill directories one rendered layout holds, in the working tree. */
  private static List<String> renderedSkillNames(String layout) {
    File skills = new File(PROJECT_DIR, layout + "/skills");
    assertThat(skills).as("the rendered %s layout", layout).isDirectory();
    String[] names = skills.list((dir, name) -> new File(dir, name + "/SKILL.md").isFile());
    assertThat(names).as("rendered pages under %s", skills).isNotEmpty();
    return Arrays.stream(names).sorted().toList();
  }

  private File jarOf(String module) {
    var jar =
        new File(PROJECT_DIR, module + "/build/libs/" + module + "-" + BUILD_VERSION + ".jar");
    assertThat(jar)
        .as("%s's jar — the test task declares it as a dependency, so it must exist here", module)
        .isFile();
    return jar;
  }

  private List<String> entryNames(File jar) throws IOException {
    try (var zip = new ZipFile(jar)) {
      return zip.stream().map(ZipEntry::getName).filter(name -> !name.endsWith("/")).toList();
    }
  }

  private String entry(File jar, String name) throws IOException {
    try (var zip = new ZipFile(jar)) {
      ZipEntry found = zip.getEntry(name);
      assertThat(found).as("%s in %s", name, jar.getName()).isNotNull();
      try (var stream = zip.getInputStream(found)) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  // --- the carrier jar --------------------------------------------------

  @Test
  void theSkillsJarCarriesOneCarrierEntryPerRenderedPage() throws IOException {
    assertThat(entryNames(jarOf("narrativetrace-skills"))).containsAll(expectedEntries());
  }

  /**
   * The point of the split: the typed catalogue, the renderers and the evals stayed behind in
   * {@code narrativetrace-skills-catalogue}. A {@code .class} entry here means a module's sources
   * came back, and a consumer fetching three Markdown files would get a build tool with them.
   */
  @Test
  void theSkillsJarCarriesNoClassFile() throws IOException {
    assertThat(entryNames(jarOf("narrativetrace-skills")))
        .filteredOn(name -> name.endsWith(".class"))
        .isEmpty();
  }

  @Test
  void theSkillsJarCarriesNothingUnderTheCarrierRootBeyondThoseEntries() throws IOException {
    assertThat(entryNames(jarOf("narrativetrace-skills")))
        .filteredOn(name -> name.startsWith(ROOT))
        .containsExactlyInAnyOrderElementsOf(expectedEntries());
  }

  // --- the CLI jar, bundling the same resources (D4) ---------------------

  @Test
  void theCliJarCarriesTheSameEntriesByteForByte() throws IOException {
    var skillsJar = jarOf("narrativetrace-skills");
    var cliJar = jarOf("narrativetrace-cli");

    assertThat(entryNames(cliJar)).containsAll(expectedEntries());
    for (String name : expectedEntries()) {
      assertThat(entry(cliJar, name))
          .as("%s is the same text in both jars — one carrier, two homes", name)
          .isEqualTo(entry(skillsJar, name));
    }
  }

  @Test
  void theCliJarStillCarriesItsOwnClasses() throws IOException {
    assertThat(entryNames(jarOf("narrativetrace-cli")))
        .contains("ai/narrativetrace/cli/Cli.class", "ai/narrativetrace/cli/Main.class");
  }

  // --- the catalogue itself ---------------------------------------------

  @Test
  void theCatalogueNamesEverySkillExactlyOnceAndPointsAtEntriesThatExist() throws IOException {
    var jar = jarOf("narrativetrace-skills");
    String catalogue = entry(jar, ROOT + "catalogue.json");
    var names = matches(catalogue, "\"name\": \"([^\"]+)\"");

    assertThat(names).containsExactlyInAnyOrderElementsOf(renderedSkillNames(".claude"));
    for (String relative : matches(catalogue, "\"(?:agents|claude)\": \"([^\"]+)\"")) {
      assertThat(entryNames(jar))
          .as("catalogue.json points at %s", relative)
          .contains(ROOT + relative);
    }
  }

  /**
   * D2: the jar's own coordinate is the stamp. A version literal inside {@code catalogue.json}
   * would have to be rewritten on every release, and a stale one would misreport what a project has
   * installed. The {@code SKILL.md} pages are deliberately NOT covered by this: their install
   * coordinate is the one sanctioned version literal, written by {@code snippetSync} and gated by
   * {@code VersionLiteralSupport}.
   */
  @Test
  void theCatalogueCarriesNoVersionLiteral() throws IOException {
    assertThat(entry(jarOf("narrativetrace-skills"), ROOT + "catalogue.json"))
        .doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }

  private static List<String> matches(String text, String regex) {
    return Pattern.compile(regex).matcher(text).results().map(m -> m.group(1)).toList();
  }
}
