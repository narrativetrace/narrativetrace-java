/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Maps simple class names to their package names by scanning compiled-classes directories.
 *
 * <p>INTENT: Trace nodes carry only simple class names, but context resolution needs real package
 * metadata (ADR-012). This index supplies the {@code packageOf} function that {@link
 * GlossaryHarvester} takes, built from the class-file layout on disk — no classes are loaded.
 *
 * <p><b>@edgeCase</b> A simple name observed in more than one package is ambiguous and resolves to
 * {@code null} (unknown, hence {@code _unassigned}) rather than guessing. Declaration files ({@code
 * package-info}, {@code module-info}) and anonymous/local classes are never indexed; nested classes
 * index under their inner simple name, matching {@code Class.getSimpleName()} as carried by trace
 * nodes.
 */
public final class ClassPackageIndex implements UnaryOperator<String> {

  /** Sentinel for simple names seen in more than one package — resolved as unknown. */
  private static final String AMBIGUOUS = " ambiguous";

  private final Map<String, String> packageBySimpleName;

  private ClassPackageIndex(Map<String, String> packageBySimpleName) {
    this.packageBySimpleName = packageBySimpleName;
    assert invariant() : "index must be consistent after construction";
  }

  /**
   * Builds an index from a {@code java.class.path}-style string.
   *
   * <p>Only entries that are existing directories are scanned — JAR entries are skipped by design:
   * traced application code lives in class directories under a build tool, while JARs hold
   * third-party classes whose vocabulary is not the repository's to govern.
   *
   * @param classpath path-separator-delimited classpath entries; must not be {@code null}
   * @return index over the class files of every directory entry
   */
  public static ClassPackageIndex fromClasspath(String classpath) {
    if (classpath == null) {
      throw new IllegalArgumentException("classpath must not be null");
    }
    var directories =
        Arrays.stream(classpath.split(File.pathSeparator))
            .filter(entry -> !entry.isBlank())
            .map(Path::of)
            .filter(Files::isDirectory)
            .toList();
    return fromDirectories(directories);
  }

  /**
   * Builds an index from compiled-classes directories.
   *
   * @param classDirs directories laid out as package hierarchies of {@code .class} files; must not
   *     be {@code null}
   * @return index over every class file found
   * @throws UncheckedIOException if a directory cannot be walked
   */
  public static ClassPackageIndex fromDirectories(List<Path> classDirs) {
    if (classDirs == null) {
      throw new IllegalArgumentException("classDirs must not be null");
    }
    var index = new HashMap<String, String>();
    for (var dir : classDirs) {
      scanDirectory(dir, index);
    }
    return new ClassPackageIndex(index);
  }

  private static void scanDirectory(Path dir, Map<String, String> index) {
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.filter(path -> path.toString().endsWith(".class"))
          .forEach(path -> indexClassFile(dir, path, index));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void indexClassFile(Path root, Path classFile, Map<String, String> index) {
    var fileName = classFile.getFileName().toString();
    var binaryName = fileName.substring(0, fileName.length() - ".class".length());
    if (!isIndexableName(binaryName)) {
      return;
    }
    var simpleName = binaryName.substring(binaryName.lastIndexOf('$') + 1);
    var packageName = packageName(root.relativize(classFile).getParent());
    var previous = index.putIfAbsent(simpleName, packageName);
    if (previous != null && !previous.equals(packageName)) {
      index.put(simpleName, AMBIGUOUS);
    }
  }

  /** Declaration files and anonymous/local classes never appear in traces — not indexed. */
  private static boolean isIndexableName(String binaryName) {
    if ("package-info".equals(binaryName) || "module-info".equals(binaryName)) {
      return false;
    }
    var lastNestingSeparator = binaryName.lastIndexOf('$');
    if (lastNestingSeparator < 0) {
      return true;
    }
    var nestedPart = binaryName.substring(lastNestingSeparator + 1);
    return !nestedPart.isEmpty() && !Character.isDigit(nestedPart.charAt(0));
  }

  private static String packageName(Path packagePath) {
    if (packagePath == null) {
      return "";
    }
    var segments = new ArrayList<String>();
    packagePath.forEach(segment -> segments.add(segment.toString()));
    return String.join(".", segments);
  }

  /**
   * Returns the package of the given simple class name, or {@code null} when unknown or ambiguous.
   *
   * @param simpleClassName simple class name as carried by trace nodes; must not be {@code null}
   * @return package name ({@code ""} for the default package), or {@code null} when the name is not
   *     in the index or was seen in more than one package
   */
  @Override
  public String apply(String simpleClassName) {
    if (simpleClassName == null) {
      throw new IllegalArgumentException("simpleClassName must not be null");
    }
    var packageName = packageBySimpleName.get(simpleClassName);
    return AMBIGUOUS.equals(packageName) ? null : packageName;
  }

  /** Returns whether the index is structurally consistent: no null names or packages. */
  boolean invariant() {
    return packageBySimpleName.entrySet().stream()
        .allMatch(e -> e.getKey() != null && !e.getKey().isEmpty() && e.getValue() != null);
  }
}
