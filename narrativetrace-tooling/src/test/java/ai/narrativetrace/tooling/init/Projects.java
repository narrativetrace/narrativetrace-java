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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A real temporary project for a property to run in, and the readings a property takes of one.
 *
 * <p>The generative properties need a filesystem — "apply" means nothing without one — and they
 * need a clean one per try, so this owns the create-run-delete shape and the two whole-tree
 * readings both property classes compare against: every file with its exact bytes, and every
 * symbolic link left behind.
 *
 * <p><b>@llmNote</b> The walk never DESCENDS through a link ({@link Files#walk}'s default) and the
 * clean-up deletes the link itself, so a project holding a link to a directory outside the temp
 * directory never costs that tree a file. A link to a FILE is a different matter: {@code
 * isRegularFile} and {@code readString} follow one, so {@link #snapshotOf} reports the target's
 * bytes under the link's own path. That is deliberate — a property asserting no page was written
 * through a link wants to see what the link resolves to — and it is why {@link #linksUnder} exists
 * separately: it is the only reading here that distinguishes a link from a real file.
 */
final class Projects {

  /** What a property does with one project. */
  interface Case {

    void run(Path project) throws IOException;
  }

  private Projects() {}

  /** Runs one case in a fresh temp directory, and deletes it whatever happened. */
  static void inATemporaryOne(String prefix, Case body) {
    Path project = null;
    try {
      project = Files.createTempDirectory(prefix);
      body.run(project);
    } catch (IOException e) {
      throw new UncheckedIOException("the property could not use a temp directory", e);
    } finally {
      deleteRecursively(project);
    }
  }

  /** Every file under the project, by relative path, with its exact bytes. */
  static Map<String, String> snapshotOf(Path project) {
    Map<String, String> files = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(project)) {
      List<Path> sorted = new ArrayList<>(walk.filter(Files::isRegularFile).toList());
      sorted.sort(Comparator.comparing(Path::toString));
      for (Path file : sorted) {
        files.put(project.relativize(file).toString(), Files.readString(file));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot snapshot " + project, e);
    }
    return files;
  }

  /** Every symbolic link left anywhere under the project. */
  static List<String> linksUnder(Path project) {
    try (Stream<Path> walk = Files.walk(project)) {
      return walk.filter(Files::isSymbolicLink).map(Path::toString).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot walk " + project, e);
    }
  }

  private static void deleteRecursively(Path directory) {
    if (directory == null) {
      return;
    }
    try (Stream<Path> walk = Files.walk(directory)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot clean up " + directory, e);
    }
  }
}
