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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An opened skills carrier: the catalogue, every flavour's rendered page, and the coordinate that
 * stamps whatever gets installed from it.
 *
 * <p>INTENT: the installer's read side. A carrier is opened once, validated whole, and then behaves
 * as a value — every page is in memory, so nothing downstream holds a file handle or can see a
 * carrier change under it mid-install.
 *
 * <p>Three ways in, all local, none of them a network call: a jar, an exploded directory, or a
 * coordinate looked up under a local Maven repository.
 *
 * <p><b>@llmNote</b> A carrier whose catalogue is malformed, whose catalogue lists a page the
 * carrier does not have, or whose catalogue names a skill twice is REFUSED here with a message
 * naming the entry. That is deliberate: validation happens before any plan exists, so a broken
 * carrier can never half-install.
 *
 * <p><b>@sideEffects</b> Reads the given path once at open time. Never writes, never resolves
 * anything over a network.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Carrier carrier = Carrier.open(Path.of("narrativetrace-skills-1.2.3.jar"));
 * String page = carrier.body(carrier.skills().get(0), SkillFlavour.AGENTS);
 * }</pre>
 */
public final class Carrier {

  /**
   * Where the carrier's resources live inside a jar — the one well-known path an installer knows.
   */
  static final String CARRIER_ROOT = "META-INF/narrativetrace/skills/";

  static final String CATALOGUE_FILE = "catalogue.json";

  /** The group every NarrativeTrace artifact publishes under; a carrier is always one of ours. */
  private static final String GROUP = "ai.narrativetrace";

  /** Written into a jar by the build that publishes it; absent from an exploded source tree. */
  private static final String VERSION_PROPERTIES = "narrativetrace-version.properties";

  /** The version part of a coordinate that names no version — honest, and visibly stale. */
  private static final String UNKNOWN_VERSION = "unknown";

  private final String coordinate;
  private final SkillCatalogue catalogue;
  private final Map<String, String> pages;

  private Carrier(String coordinate, SkillCatalogue catalogue, Map<String, String> pages) {
    this.coordinate = coordinate;
    this.catalogue = catalogue;
    this.pages = Map.copyOf(pages);
    assert invariant() : "a carrier must be complete the moment it exists";
  }

  /**
   * Opens a carrier from a jar file or from an exploded directory (either the jar root or the
   * carrier root itself).
   *
   * @throws IllegalArgumentException when the path does not exist, carries no catalogue, or carries
   *     a catalogue that does not describe what is there
   */
  public static Carrier open(Path source) {
    if (source == null) {
      throw new IllegalArgumentException("a carrier path must be given");
    }
    if (!Files.exists(source)) {
      throw new IllegalArgumentException("no carrier at " + source);
    }
    return Files.isDirectory(source) ? openDirectory(source) : openJar(source);
  }

  /**
   * Opens the carrier a coordinate names inside a local Maven repository. Nothing is fetched: this
   * is a file lookup under {@code ~/.m2/repository}'s layout, and a coordinate that is not already
   * there is refused with the path it looked for.
   *
   * @param coordinate {@code group:artifact:version}
   * @param localRepository the repository root, typically {@code ~/.m2/repository}
   */
  public static Carrier fromLocalRepository(String coordinate, Path localRepository) {
    if (localRepository == null) {
      throw new IllegalArgumentException("a local repository root must be given");
    }
    String[] parts = coordinateParts(coordinate);
    Path jar =
        localRepository
            .resolve(parts[0].replace('.', '/'))
            .resolve(parts[1])
            .resolve(parts[2])
            .resolve(parts[1] + "-" + parts[2] + ".jar");
    if (!Files.isRegularFile(jar)) {
      throw new IllegalArgumentException(
          "no carrier in the local repository at " + jar + " — install or fetch it first");
    }
    return read(coordinate, name -> readJarEntry(jar, CARRIER_ROOT + name));
  }

  /** The coordinate that stamps everything installed from this carrier. */
  public String coordinate() {
    return coordinate;
  }

  /** The carrier's index, already validated. */
  public SkillCatalogue catalogue() {
    return catalogue;
  }

  /** Every skill the carrier carries, in catalogue order. */
  public List<SkillEntry> skills() {
    return catalogue.skills();
  }

  /**
   * The rendered page for one skill in one flavour, exactly as the carrier carries it.
   *
   * @throws IllegalArgumentException when the skill is not this carrier's
   */
  public String body(SkillEntry skill, SkillFlavour flavour) {
    if (skill == null) {
      throw new IllegalArgumentException("a skill must be given");
    }
    String page = pages.get(skill.pathFor(flavour));
    if (page == null) {
      throw new IllegalArgumentException(
          "this carrier does not carry " + skill.name() + " (" + coordinate + ")");
    }
    return page;
  }

  /**
   * Every page the catalogue lists is present, and the coordinate names all three parts. A carrier
   * that fails this could half-install a project.
   */
  boolean invariant() {
    if (coordinate == null || coordinate.split(":", -1).length != 3) {
      return false;
    }
    for (SkillEntry skill : catalogue.skills()) {
      for (SkillFlavour flavour : SkillFlavour.values()) {
        if (!pages.containsKey(skill.pathFor(flavour))) {
          return false;
        }
      }
    }
    return true;
  }

  // --- opening --------------------------------------------------------------------------------

  private static Carrier openJar(Path jar) {
    String version =
        readJarEntry(jar, VERSION_PROPERTIES)
            .map(Carrier::versionIn)
            .orElseGet(() -> versionOf(jar));
    return read(
        coordinateOf(artifactOf(jar), version), name -> readJarEntry(jar, CARRIER_ROOT + name));
  }

  private static Carrier openDirectory(Path directory) {
    Path root =
        Files.isRegularFile(directory.resolve(CATALOGUE_FILE))
            ? directory
            : directory.resolve(CARRIER_ROOT);
    String version =
        readFile(directory.resolve(VERSION_PROPERTIES))
            .map(Carrier::versionIn)
            .orElseGet(() -> versionOf(directory));
    return read(coordinateOf(artifactOf(directory), version), name -> readFile(root.resolve(name)));
  }

  /** Validates the whole carrier, then freezes it: every listed page is read here or nowhere. */
  private static Carrier read(String coordinate, CarrierSource source) {
    String text =
        source
            .read(CATALOGUE_FILE)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "the carrier "
                            + coordinate
                            + " carries no "
                            + CARRIER_ROOT
                            + CATALOGUE_FILE));
    SkillCatalogue catalogue = readCatalogue(coordinate, text);
    Map<String, String> pages = new LinkedHashMap<>();
    for (SkillEntry skill : catalogue.skills()) {
      for (SkillFlavour flavour : SkillFlavour.values()) {
        String path = skill.pathFor(flavour);
        pages.put(path, source.read(path).orElseThrow(() -> missing(coordinate, skill, path)));
      }
    }
    return new Carrier(coordinate, catalogue, pages);
  }

  private static SkillCatalogue readCatalogue(String coordinate, String text) {
    try {
      return CatalogueReader.read(text);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "the carrier " + coordinate + "'s " + CATALOGUE_FILE + " is unusable: " + e.getMessage(),
          e);
    }
  }

  private static IllegalArgumentException missing(
      String coordinate, SkillEntry skill, String path) {
    return new IllegalArgumentException(
        "the carrier "
            + coordinate
            + " lists "
            + skill.name()
            + " at "
            + path
            + ", which it does not carry");
  }

  private static Optional<String> readJarEntry(Path jar, String name) {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      ZipEntry entry = zip.getEntry(name);
      if (entry == null) {
        return Optional.empty();
      }
      try (var stream = zip.getInputStream(entry)) {
        return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot read the carrier jar " + jar + ": " + e, e);
    }
  }

  private static Optional<String> readFile(Path path) {
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the carrier file " + path, e);
    }
  }

  /**
   * The {@code version=} line of a carrier's own properties file. Read line-wise rather than
   * through {@link java.util.Properties}: the file has exactly one key, written by the build, and a
   * reader that cannot fail is a reader with no unreachable failure branch to pretend to test.
   */
  private static String versionIn(String properties) {
    return properties
        .lines()
        .map(String::trim)
        .filter(line -> line.startsWith("version="))
        .map(line -> line.substring("version=".length()).trim())
        .filter(version -> !version.isEmpty())
        .findFirst()
        .orElse(UNKNOWN_VERSION);
  }

  /**
   * The coordinate a carrier's own file name yields. Validated here rather than trusted: this
   * string is STAMPED into every page the carrier installs and read back by the doctor, so a name
   * carrying the separator, or one leaving either part empty, is refused before any page is
   * written. The invariant would catch it too — but only where assertions are on, which is not
   * where installs happen.
   */
  private static String coordinateOf(String artifact, String version) {
    if (artifact.isBlank()
        || version.isBlank()
        || artifact.indexOf(':') >= 0
        || version.indexOf(':') >= 0) {
      throw new IllegalArgumentException(
          "a carrier is stamped with its own coordinate, and \""
              + artifact
              + "-"
              + version
              + "\" does not name one — a carrier reads <artifact>-<version>, without a \":\"");
    }
    return GROUP + ":" + artifact + ":" + version;
  }

  private static String[] coordinateParts(String coordinate) {
    String[] parts = coordinate == null ? new String[0] : coordinate.split(":", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "a coordinate reads group:artifact:version, got \"" + coordinate + "\"");
    }
    for (String part : parts) {
      if (part.isBlank()) {
        throw new IllegalArgumentException(
            "a coordinate reads group:artifact:version, got \"" + coordinate + "\"");
      }
      if (part.contains("/") || part.contains("\\") || part.equals("..")) {
        throw new IllegalArgumentException(
            "a coordinate must not contain a path segment, got \"" + coordinate + "\"");
      }
    }
    return parts;
  }

  private static String artifactOf(Path source) {
    String name = stem(source);
    int dash = versionDash(name);
    return dash < 0 ? name : name.substring(0, dash);
  }

  private static String versionOf(Path source) {
    String name = stem(source);
    int dash = versionDash(name);
    return dash < 0 ? UNKNOWN_VERSION : name.substring(dash + 1);
  }

  /** The index of the {@code -} that starts a version, i.e. one followed by a digit. */
  private static int versionDash(String name) {
    for (int i = 0; i < name.length() - 1; i++) {
      if (name.charAt(i) == '-' && Character.isDigit(name.charAt(i + 1))) {
        return i;
      }
    }
    return -1;
  }

  private static String stem(Path source) {
    String name = source.getFileName().toString();
    return name.endsWith(".jar") ? name.substring(0, name.length() - ".jar".length()) : name;
  }
}
