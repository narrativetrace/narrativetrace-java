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
package ai.narrativetrace.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where {@code narrativetrace init} finds the skills it installs. Every case here is local: the
 * bundled carrier on this launcher's own classpath, a jar or directory a person named, and a
 * coordinate looked up under a local Maven repository. Nothing in this class may reach a network.
 *
 * <p>The fixture carrier is hand-built rather than taken from this repository's own build output:
 * the point here is WHERE a carrier is found, and a hand-built one keeps the cases independent of
 * which of this repo's jars happens to have been assembled.
 */
class CarrierLocatorTest {

  @Test
  void opensTheCarrierBundledOnItsOwnClasspath(@TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    var carrier = locator.open(null);

    assertThat(carrier.skills()).isNotEmpty();
    assertThat(carrier.coordinate()).startsWith("ai.narrativetrace:");
  }

  @Test
  void opensTheCarrierBundledInsideAJarOnTheClasspath(@TempDir Path dir) throws IOException {
    Path jar = TestCarriers.jar(dir.resolve("narrativetrace-cli-4.5.6.jar"));
    try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      var carrier = new CarrierLocator(loader, dir.resolve("m2")).open(null);

      assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-cli:4.5.6");
      assertThat(carrier.skills()).hasSize(1);
    }
  }

  @Test
  void refusesWhenTheClasspathCarriesNoCarrierAtAll(@TempDir Path dir) throws IOException {
    try (var empty = new URLClassLoader(new URL[0], null)) {
      var locator = new CarrierLocator(empty, dir);

      assertThatThrownBy(() -> locator.open(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("--from");
    }
  }

  @Test
  void blankFromMeansTheBundledCarrier(@TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    assertThat(locator.open("   ").coordinate()).isEqualTo(locator.open(null).coordinate());
  }

  @Test
  void opensAJarNamedWithFrom(@TempDir Path dir) throws IOException {
    Path jar = TestCarriers.jar(dir.resolve("narrativetrace-skills-9.9.9.jar"));

    var carrier = new CarrierLocator(getClass().getClassLoader(), dir).open(jar.toString());

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-skills:9.9.9");
  }

  @Test
  void opensAnExplodedDirectoryNamedWithFrom(@TempDir Path dir) throws IOException {
    Path exploded = TestCarriers.directory(dir.resolve("narrativetrace-skills-9.9.9"));

    var carrier = new CarrierLocator(getClass().getClassLoader(), dir).open(exploded.toString());

    assertThat(carrier.skills()).hasSize(1);
  }

  @Test
  void resolvesACoordinateUnderTheLocalRepositoryOnly(@TempDir Path dir) throws IOException {
    Path m2 = dir.resolve("m2");
    Path installed =
        m2.resolve("ai/narrativetrace/narrativetrace-skills/9.9.9")
            .resolve("narrativetrace-skills-9.9.9.jar");
    Files.createDirectories(installed.getParent());
    TestCarriers.jar(installed);

    var carrier =
        new CarrierLocator(getClass().getClassLoader(), m2)
            .open("ai.narrativetrace:narrativetrace-skills:9.9.9");

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-skills:9.9.9");
  }

  @Test
  void refusesACoordinateThatIsNotInTheLocalRepository(@TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    assertThatThrownBy(() -> locator.open("ai.narrativetrace:narrativetrace-skills:9.9.9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("local repository");
  }

  @Test
  void refusesSomethingThatIsNeitherAPathNorACoordinate(@TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    assertThatThrownBy(() -> locator.open("not-a-carrier"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("group:artifact:version");
  }

  /**
   * A path that exists wins over a coordinate reading, even when it is spelled like one: the file
   * is right there, and reading it as a coordinate would silently look somewhere else instead. The
   * proof is the refusal it earns — a carrier's own name is stamped into every page it installs, so
   * one carrying the coordinate separator cannot be opened at all, and THAT is the error a path
   * reading produces rather than "not in the local repository".
   */
  @Test
  void prefersAnExistingPathOverACoordinateReading(@TempDir Path dir) throws IOException {
    Path named = TestCarriers.directory(dir.resolve("a:b:c"));
    var locator = new CarrierLocator(getClass().getClassLoader(), dir);

    assertThatThrownBy(() -> locator.open(named.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("<artifact>-<version>")
        .hasMessageNotContaining("local repository");
  }

  /**
   * A classpath entry a URL cannot be read back from — here a directory name with an unescaped
   * space, the way a hand-built URL carries one. Refused by name: a half-formed path handed to the
   * carrier would read a directory nobody asked for, or none at all.
   */
  @Test
  void refusesAClasspathEntryThatIsNotAReadableLocalPath(@TempDir Path dir) throws IOException {
    Path spaced = TestCarriers.directory(dir.resolve("with a space"));
    try (var loader = new URLClassLoader(new URL[] {new URL("file:" + spaced + "/")}, null)) {
      var locator = new CarrierLocator(loader, dir.resolve("m2"));

      assertThatThrownBy(() -> locator.open(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot read the bundled carrier");
    }
  }

  /** A NUL byte is not a path on any platform, and it is not a coordinate either. */
  @Test
  void refusesAFromThatIsNotEvenAPath(@TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    assertThatThrownBy(() -> locator.open("a\u0000b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("group:artifact:version");
  }

  @Test
  void refusesToBeBuiltWithoutAClasspathOrARepository(@TempDir Path dir) {
    assertThatThrownBy(() -> new CarrierLocator(null, dir))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CarrierLocator(getClass().getClassLoader(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theStandardLocatorReadsTheLaunchersOwnClasspath() {
    assertThat(CarrierLocator.standard().open(null).skills()).isNotEmpty();
  }
}
