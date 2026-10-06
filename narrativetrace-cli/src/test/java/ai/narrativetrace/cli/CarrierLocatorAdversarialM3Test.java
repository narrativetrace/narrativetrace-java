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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boundary coverage for {@link CarrierLocator} beyond {@code CarrierLocatorTest}: a {@code --from}
 * that names something the filesystem HAS but that is not a usable carrier, a coordinate-shaped
 * argument with a blank segment, and a trailing path separator.
 */
class CarrierLocatorAdversarialM3Test {

  @Test
  void refusesADirectoryNamedWithFromThatExistsButCarriesNoCatalogue(@TempDir Path dir)
      throws IOException {
    Path empty = Files.createDirectories(dir.resolve("carrier"));
    var locator = new CarrierLocator(getClass().getClassLoader(), dir.resolve("m2"));

    assertThatThrownBy(() -> locator.open(empty.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("catalogue.json");
  }

  @Test
  void refusesAFromNamingAJarThatExistsButIsNotAZipArchive(@TempDir Path dir) throws IOException {
    Path notAZip = Files.writeString(dir.resolve("narrativetrace-skills-1.0.0.jar"), "garbage");
    var locator = new CarrierLocator(getClass().getClassLoader(), dir.resolve("m2"));

    assertThatThrownBy(() -> locator.open(notAZip.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot read the carrier jar");
  }

  /**
   * The shape check ({@code split(":", -1).length == 3}) passes with a BLANK middle segment; the
   * deeper refusal — naming the same shape again — comes from {@code Carrier.fromLocalRepository},
   * not from the locator itself. This proves the seam from the locator's side.
   */
  @Test
  void aCoordinateShapedArgumentWithABlankSegmentReachesTheLocalRepositoryRefusal(
      @TempDir Path m2) {
    var locator = new CarrierLocator(getClass().getClassLoader(), m2);

    assertThatThrownBy(() -> locator.open("ai.narrativetrace::9.9.9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("group:artifact:version");
  }

  @Test
  void aTrailingSeparatorOnAnExplodedDirectoryReadsTheSameCarrier(@TempDir Path dir)
      throws IOException {
    Path exploded = TestCarriers.directory(dir.resolve("narrativetrace-skills-9.9.9"));
    var locator = new CarrierLocator(getClass().getClassLoader(), dir.resolve("m2"));

    var carrier = locator.open(exploded + File.separator);

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-skills:9.9.9");
  }
}
