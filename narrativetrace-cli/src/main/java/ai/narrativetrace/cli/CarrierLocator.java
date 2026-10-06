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

import ai.narrativetrace.tooling.init.Carrier;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Finds the skills carrier {@code narrativetrace init} installs from — locally, always.
 *
 * <p>INTENT: this launcher makes NO network call of its own (D4 as ruled). The carrier is bundled
 * in the CLI's own jar, so {@code java -jar narrativetrace-cli-<v>.jar init} works from the one
 * archive a consumer already fetched; {@code --from} overrides it with a jar, an exploded
 * directory, or a coordinate looked up under a local Maven repository.
 *
 * <p><b>@llmNote</b> The bundled carrier is found through the CLASSLOADER rather than through the
 * running jar's code-source location: the two are the same file for {@code java -jar}, but when the
 * launcher runs from compiled classes the resources sit in a sibling directory that the code source
 * knows nothing about.
 *
 * <p><b>@llmNote</b> A carrier opened from an exploded classpath honestly reports an {@code
 * unknown} version — there is no jar whose name or properties could carry one. Installing from one
 * stamps the pages with that, and the next real run rewrites them, which is what should happen.
 *
 * <p><b>@sideEffects</b> Reads the classpath and the filesystem. Never writes, never resolves
 * anything remotely.
 */
final class CarrierLocator {

  /** The catalogue entry every carrier has, and the thing worth looking for on a classpath. */
  private static final String CATALOGUE_RESOURCE = "META-INF/narrativetrace/skills/catalogue.json";

  private final ClassLoader bundled;
  private final Path localRepository;

  CarrierLocator(ClassLoader bundled, Path localRepository) {
    if (bundled == null || localRepository == null) {
      throw new IllegalArgumentException("a locator needs a classpath and a local repository root");
    }
    this.bundled = bundled;
    this.localRepository = localRepository;
  }

  /** The real environment: this launcher's own classpath and {@code ~/.m2/repository}. */
  static CarrierLocator standard() {
    return new CarrierLocator(
        CarrierLocator.class.getClassLoader(),
        Path.of(System.getProperty("user.home", "")).resolve(".m2").resolve("repository"));
  }

  /**
   * The carrier a run should install from.
   *
   * @param from a jar, a directory or a {@code group:artifact:version} coordinate; {@code null} or
   *     blank for the carrier bundled with this launcher
   * @throws IllegalArgumentException when there is no carrier there, with a message naming what was
   *     looked for
   */
  Carrier open(String from) {
    return from == null || from.isBlank() ? bundled() : named(from.trim());
  }

  /** The carrier that shipped inside this launcher. */
  private Carrier bundled() {
    URL catalogue = bundled.getResource(CATALOGUE_RESOURCE);
    if (catalogue == null) {
      throw new IllegalArgumentException(
          "this build of narrativetrace carries no skills — name one with --from");
    }
    return Carrier.open(
        "jar".equals(catalogue.getProtocol()) ? archiveOf(catalogue) : carrierRootOf(catalogue));
  }

  /** A jar-scheme URL reads {@code jar:<inner URL>!/<entry>}; the inner URL is the archive. */
  private static Path archiveOf(URL catalogue) {
    String spec = catalogue.getPath();
    int separator = spec.indexOf("!/");
    return pathOf(separator < 0 ? spec : spec.substring(0, separator), catalogue);
  }

  /** A file-scheme URL points straight at the catalogue; its directory IS the carrier root. */
  private static Path carrierRootOf(URL catalogue) {
    return pathOf(catalogue.toString(), catalogue).getParent();
  }

  /**
   * One conversion, one refusal. A classpath entry that is not a readable local file — a URL with
   * an unescaped space in it, an archive inside an archive — is refused by name rather than
   * reaching the carrier as a half-formed path.
   */
  private static Path pathOf(String uri, URL source) {
    try {
      return Path.of(URI.create(uri));
    } catch (IllegalArgumentException | FileSystemNotFoundException e) {
      throw new IllegalArgumentException("cannot read the bundled carrier at " + source, e);
    }
  }

  /**
   * A path when something is there, a coordinate when the argument reads like one. A path is tried
   * first: a file that exists is never a coordinate, whatever its name looks like.
   */
  private Carrier named(String from) {
    Path path = pathOrNull(from);
    if (path != null) {
      return Carrier.open(path);
    }
    if (from.split(":", -1).length == 3) {
      return Carrier.fromLocalRepository(from, localRepository);
    }
    throw new IllegalArgumentException(
        "no carrier at "
            + from
            + " — --from takes a jar, a directory, or a group:artifact:version coordinate already"
            + " in the local Maven repository");
  }

  private static Path pathOrNull(String from) {
    try {
      Path path = Path.of(from);
      return Files.exists(path) ? path : null;
    } catch (InvalidPathException e) {
      return null;
    }
  }
}
