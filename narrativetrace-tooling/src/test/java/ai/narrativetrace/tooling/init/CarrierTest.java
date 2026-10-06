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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.catdd.ContractVerifiable;
import ai.narrativetrace.tooling.init.catdd.InvariantCheckExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * The carrier is opened from the REAL built jars — the two archives a consumer actually gets — and
 * refused on hand-built broken ones. A fixture jar would prove only that the test can build a jar;
 * the refusals have no real counterpart, so those are built by hand on purpose.
 */
@ExtendWith(InvariantCheckExtension.class)
class CarrierTest implements ContractVerifiable<Carrier> {

  private Carrier subject;

  @BeforeEach
  void aCarrierToCheckAround() {
    subject = Carrier.open(jarOf("narrativetrace-skills"));
  }

  @Override
  public Carrier subject() {
    return subject;
  }

  @Override
  public boolean checkInvariant() {
    return subject == null || subject.invariant();
  }

  private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir"));
  private static final String VERSION = System.getProperty("narrativetrace.buildVersion");

  private static final String CARRIER_ROOT = "META-INF/narrativetrace/skills/";

  private static Path jarOf(String module) {
    return PROJECT_DIR.resolve(module + "/build/libs/" + module + "-" + VERSION + ".jar");
  }

  private static Path realResources() {
    return PROJECT_DIR.resolve("narrativetrace-skills/src/main/resources");
  }

  // --- the real carriers ------------------------------------------------------------------

  @Test
  void opensTheSkillsJarAndStampsItWithThatJarsCoordinate() {
    Carrier carrier = Carrier.open(jarOf("narrativetrace-skills"));

    assertThat(carrier.coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:" + VERSION);
    assertThat(carrier.skills())
        .extracting(SkillEntry::name)
        .containsExactly(
            "narrativetrace-doctor",
            "add-narrative-tracing",
            "add-narrativetrace-clarity",
            "narrativetrace-feedback");
  }

  @Test
  void opensTheCliJarBecauseItBundlesTheSameCarrier() {
    Carrier carrier = Carrier.open(jarOf("narrativetrace-cli"));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-cli:" + VERSION);
    assertThat(carrier.skills())
        .containsExactlyElementsOf(Carrier.open(jarOf("narrativetrace-skills")).skills());
  }

  @Test
  void handsOutEachFlavourBodyExactlyAsTheCarrierCarriesIt() throws IOException {
    Carrier carrier = Carrier.open(jarOf("narrativetrace-skills"));
    SkillEntry doctor = carrier.catalogue().skill("narrativetrace-doctor").orElseThrow();

    for (SkillFlavour flavour : SkillFlavour.values()) {
      String expected =
          Files.readString(realResources().resolve(CARRIER_ROOT + doctor.pathFor(flavour)));
      assertThat(carrier.body(doctor, flavour)).isEqualTo(expected);
    }
  }

  @Test
  void opensAnExplodedDirectoryFromTheJarRootOrFromTheCarrierRoot() {
    Carrier fromJarRoot = Carrier.open(realResources());
    Carrier fromCarrierRoot = Carrier.open(realResources().resolve(CARRIER_ROOT));

    assertThat(fromJarRoot.skills()).isEqualTo(fromCarrierRoot.skills());
    assertThat(fromJarRoot.coordinate()).isEqualTo("ai.narrativetrace:resources:unknown");
  }

  @Test
  void readsTheVersionFromTheCarriersOwnPropertiesWhenItCarriesOne(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills"));
    Files.writeString(exploded.resolve("narrativetrace-version.properties"), "version=9.9.9\n");

    assertThat(Carrier.open(exploded).coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:9.9.9");
  }

  // --- the local repository (no network, ever) ---------------------------------------------

  @Test
  void resolvesACoordinateUnderTheLocalRepository(@TempDir Path m2) throws IOException {
    Path jar = installIntoLocalRepository(m2, "narrativetrace-skills", VERSION);

    Carrier carrier =
        Carrier.fromLocalRepository("ai.narrativetrace:narrativetrace-skills:" + VERSION, m2);

    assertThat(jar).exists();
    assertThat(carrier.coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:" + VERSION);
    assertThat(carrier.skills())
        .extracting(SkillEntry::name)
        .containsExactly(
            "narrativetrace-doctor",
            "add-narrative-tracing",
            "add-narrativetrace-clarity",
            "narrativetrace-feedback");
  }

  @Test
  void refusesACoordinateThatIsNotInTheLocalRepositoryAndNamesThePathItLookedFor(@TempDir Path m2) {
    assertThatThrownBy(
            () -> Carrier.fromLocalRepository("ai.narrativetrace:narrativetrace-skills:0.0.1", m2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ai/narrativetrace/narrativetrace-skills/0.0.1")
        .hasMessageContaining("narrativetrace-skills-0.0.1.jar");
  }

  @Test
  void refusesAMalformedCoordinate(@TempDir Path m2) {
    assertThatThrownBy(() -> Carrier.fromLocalRepository("ai.narrativetrace:skills", m2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("group:artifact:version");
    assertThatThrownBy(() -> Carrier.fromLocalRepository("ai.narrativetrace::0.2.4", m2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("group:artifact:version");
  }

  @Test
  void refusesACoordinateSegmentThatWouldClimbOutOfTheLocalRepository(@TempDir Path m2) {
    assertThatThrownBy(() -> Carrier.fromLocalRepository("ai.narrativetrace:..:0.2.4", m2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain a path segment");
    assertThatThrownBy(() -> Carrier.fromLocalRepository("ai.narrativetrace:skills:../../etc", m2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain a path segment");
  }

  /**
   * The carrier's own file name becomes the coordinate stamped into every page it installs, so a
   * name carrying the coordinate's own separator cannot produce one. Refused at open time rather
   * than half-installed: with assertions off, a five-part "coordinate" would be written into every
   * page and read back by the doctor as a version nobody can resolve.
   */
  @Test
  void refusesACarrierWhoseNameCarriesTheCoordinateSeparator(@TempDir Path dir) throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("a:b:c"));

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a:b:c");
  }

  /**
   * The separator can land in either half, and at either end. Both near misses are here because the
   * artifact check alone would let {@code x-1:2} through, and a check spelled {@code > 0} would let
   * a name that STARTS with the separator through — each producing a four- or five-part stamp that
   * only an assertion would ever have caught.
   */
  @Test
  void refusesACarrierWhoseVersionCarriesTheCoordinateSeparator(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("skills-1:2"));

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1:2");
  }

  @Test
  void refusesACarrierWhoseNameBeginsWithTheCoordinateSeparator(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve(":skills-1.2.3"));

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(":skills");
  }

  @Test
  void refusesACarrierWhoseNameLeavesTheArtifactOrTheVersionEmpty(@TempDir Path dir)
      throws IOException {
    Path noArtifact = copyCarrierInto(dir.resolve("-1.2.3"));

    assertThatThrownBy(() -> Carrier.open(noArtifact))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1.2.3");
  }

  // --- refusals: a broken carrier is refused before any work --------------------------------

  @Test
  void refusesASourceThatDoesNotExist(@TempDir Path dir) {
    assertThatThrownBy(() -> Carrier.open(dir.resolve("nothing-here.jar")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nothing-here.jar");
  }

  @Test
  void refusesADirectoryWithNoCatalogue(@TempDir Path dir) {
    assertThatThrownBy(() -> Carrier.open(dir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries no META-INF/narrativetrace/skills/catalogue.json");
  }

  @Test
  void refusesAJarWithNoCatalogue(@TempDir Path dir) throws IOException {
    Path jar = dir.resolve("empty-0.1.0.jar");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
      zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
      zip.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
    }

    assertThatThrownBy(() -> Carrier.open(jar))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries no META-INF/narrativetrace/skills/catalogue.json");
  }

  @Test
  void refusesACarrierWhoseCatalogueListsAMissingFileAndNamesTheEntry(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills-0.2.0"));
    Files.delete(exploded.resolve(CARRIER_ROOT + "claude/narrativetrace-doctor/SKILL.md"));

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("narrativetrace-doctor")
        .hasMessageContaining("claude/narrativetrace-doctor/SKILL.md");
  }

  @Test
  void refusesACarrierWhoseCatalogueIsMalformedAndNamesTheFile(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills-0.2.0"));
    Files.writeString(exploded.resolve(CARRIER_ROOT + "catalogue.json"), "{\"runtime\":");

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("catalogue.json")
        .hasMessageContaining("malformed JSON");
  }

  @Test
  void refusesToHandOutABodyForASkillTheCarrierDoesNotList(@TempDir Path dir) throws IOException {
    Carrier carrier = Carrier.open(copyCarrierInto(dir.resolve("narrativetrace-skills-0.2.0")));
    SkillEntry stranger = new SkillEntry("stranger", "d", "agents/x/SKILL.md", "claude/x/SKILL.md");

    assertThatThrownBy(() -> carrier.body(stranger, SkillFlavour.AGENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stranger");
  }

  @Test
  void keepsItsSkillListImmutable() {
    Carrier carrier = Carrier.open(jarOf("narrativetrace-skills"));

    assertThatThrownBy(() -> carrier.skills().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void refusesACorruptJar(@TempDir Path dir) throws IOException {
    Path jar = dir.resolve("narrativetrace-skills-0.2.0.jar");
    Files.writeString(jar, "this is not a zip archive");

    assertThatThrownBy(() -> Carrier.open(jar))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot read the carrier jar")
        .hasMessageContaining("narrativetrace-skills-0.2.0.jar");
  }

  @Test
  void refusesACarrierPageThatIsNotUtf8AndNamesTheFile(@TempDir Path dir) throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills-0.2.0"));
    Path page = exploded.resolve(CARRIER_ROOT + "agents/narrativetrace-doctor/SKILL.md");
    Files.write(page, new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd});

    assertThatThrownBy(() -> Carrier.open(exploded))
        .isInstanceOf(java.io.UncheckedIOException.class)
        .hasMessageContaining("cannot read the carrier file")
        .hasMessageContaining("SKILL.md");
  }

  @Test
  void fallsBackToAnUnknownVersionWhenThePropertiesFileNamesNone(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills"));
    Files.writeString(exploded.resolve("narrativetrace-version.properties"), "# no version here\n");

    assertThat(Carrier.open(exploded).coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:unknown");
  }

  @Test
  void fallsBackToAnUnknownVersionWhenThePropertiesLeaveItEmpty(@TempDir Path dir)
      throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills"));
    Files.writeString(exploded.resolve("narrativetrace-version.properties"), "version=\n");

    assertThat(Carrier.open(exploded).coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:unknown");
  }

  @Test
  void readsNoVersionFromANameWhoseTrailingDashStartsNothing(@TempDir Path dir) throws IOException {
    Path exploded = copyCarrierInto(dir.resolve("narrativetrace-skills-"));

    assertThat(Carrier.open(exploded).coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills-:unknown");
  }

  @Test
  void refusesANullPathANullRepositoryAndANullSkill(@TempDir Path dir) throws IOException {
    Carrier carrier = Carrier.open(copyCarrierInto(dir.resolve("narrativetrace-skills-0.2.0")));

    assertThatThrownBy(() -> Carrier.open(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a carrier path must be given");
    assertThatThrownBy(() -> Carrier.fromLocalRepository("ai.narrativetrace:x:1.0.0", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a local repository root must be given");
    assertThatThrownBy(() -> carrier.body(null, SkillFlavour.AGENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a skill must be given");
  }

  @Test
  void holdsItsInvariantOnceOpen() {
    assertThat(Carrier.open(jarOf("narrativetrace-skills")).invariant()).isTrue();
  }

  // --- helpers -----------------------------------------------------------------------------

  private static Path copyCarrierInto(Path target) throws IOException {
    Path source = realResources();
    try (var paths = Files.walk(source)) {
      for (Path path : paths.toList()) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination);
        }
      }
    }
    return target;
  }

  private static Path installIntoLocalRepository(Path m2, String artifact, String version)
      throws IOException {
    Path directory = m2.resolve("ai/narrativetrace/" + artifact + "/" + version);
    Files.createDirectories(directory);
    Path jar = directory.resolve(artifact + "-" + version + ".jar");
    Files.copy(jarOf(artifact), jar);
    return jar;
  }
}
