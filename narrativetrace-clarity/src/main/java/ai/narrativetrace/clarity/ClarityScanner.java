/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scanner that analyzes compiled classes without running them.
 *
 * <p>INTENT: Use this for CI gates, Gradle tasks, and IDE tooling that needs naming feedback from
 * bytecode alone.
 *
 * <p>It builds synthetic trace nodes from reflection on public methods, then delegates to {@link
 * ClarityAnalyzer} for scoring. Pass a {@link DomainVocabulary} to score the scanned classes in the
 * project's own language.
 *
 * <pre>{@code
 * var scanner = new ClarityScanner();
 * Map<String, ClarityResult> results = scanner.scan(Path.of("build/classes/java/main"));
 * results.forEach((cls, result) ->
 *     System.out.printf("%s: %.2f%n", cls, result.overallScore()));
 * }</pre>
 *
 * <p><b>@edgeCase</b> Synthetic, bridge, private, and {@code Object} methods are skipped, as are
 * private nested, anonymous, local, and synthetic (lambda) classes — implementation details of
 * their enclosing class are not part of the narrative surface. Classes with missing dependencies
 * are silently ignored rather than failing the whole scan.
 *
 * @see ClarityAnalyzer
 * @see ClarityScannerMain
 */
public final class ClarityScanner {

  private final ClarityAnalyzer analyzer;

  /** A scanner with no project vocabulary — the built-in dictionaries alone. */
  public ClarityScanner() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public ClarityScanner(DomainVocabulary vocabulary) {
    this.analyzer = new ClarityAnalyzer(vocabulary);
  }

  /**
   * Scans all loadable classes in one compiled-classes directory.
   *
   * @param classesDir Directory containing compiled class files, typically {@code
   *     build/classes/java/main}.
   * @return Map keyed by simple class name.
   * @throws IOException if the directory cannot be read
   */
  public Map<String, ClarityResult> scan(Path classesDir) throws IOException {
    var classes = new ArrayList<Class<?>>();
    var url = classesDir.toUri().toURL();
    try (var loader = new URLClassLoader(new URL[] {url}, getClass().getClassLoader());
        Stream<Path> walk = Files.walk(classesDir)) {
      walk.filter(p -> p.toString().endsWith(".class"))
          .forEach(
              p -> {
                var relative = classesDir.relativize(p).toString();
                var className =
                    relative.replace('/', '.').replace('\\', '.').replaceAll("\\.class$", "");
                try {
                  classes.add(loader.loadClass(className));
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                  // Skip classes that can't be loaded (missing dependencies)
                }
              });
    }
    return scan(classes);
  }

  /**
   * Scans the given list of already loaded classes.
   *
   * @param classes Classes to analyze.
   * @return Map keyed by simple class name.
   */
  public Map<String, ClarityResult> scan(List<Class<?>> classes) {
    var results = new LinkedHashMap<String, ClarityResult>();
    for (var clazz : classes) {
      if (shouldSkipClass(clazz)) {
        continue;
      }
      var nodes = buildNodes(clazz);
      if (!nodes.isEmpty()) {
        var tree = new DefaultTraceTree(nodes);
        results.put(clazz.getSimpleName(), analyzer.analyze(tree, propertyAccessors(clazz)));
      }
    }
    return results;
  }

  /**
   * Private nested, anonymous, and local types are implementation details of their enclosing class,
   * not part of the narrative surface — scoring them punishes internal helpers nobody traces, and
   * anonymous classes have no simple name to report a result under.
   */
  private boolean shouldSkipClass(Class<?> clazz) {
    return Modifier.isPrivate(clazz.getModifiers())
        || clazz.isAnonymousClass()
        || clazz.isLocalClass()
        || clazz.isSynthetic();
  }

  private List<TraceNode> buildNodes(Class<?> clazz) {
    var nodes = new ArrayList<TraceNode>();
    for (var method : clazz.getDeclaredMethods()) {
      if (shouldSkip(method)) {
        continue;
      }
      var params = buildParams(method);
      var signature = new MethodSignature(clazz.getSimpleName(), method.getName(), params);
      nodes.add(new TraceNode(signature, List.of(), new TraceOutcome.Returned(""), 0L));
    }
    return nodes;
  }

  private boolean shouldSkip(Method method) {
    return method.isSynthetic()
        || method.isBridge()
        || !Modifier.isPublic(method.getModifiers())
        || isObjectMethod(method);
  }

  private boolean isObjectMethod(Method method) {
    try {
      Object.class.getMethod(method.getName(), method.getParameterTypes());
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * A record accessor's name is its component name — a noun the analyzer must score on the noun
   * rubric rather than the verb+noun method standard.
   */
  private Set<String> propertyAccessors(Class<?> clazz) {
    if (!clazz.isRecord()) {
      return Set.of();
    }
    var names = new HashSet<String>();
    for (var component : clazz.getRecordComponents()) {
      names.add(component.getName());
    }
    return names;
  }

  private List<ParameterCapture> buildParams(Method method) {
    var params = new ArrayList<ParameterCapture>();
    for (var param : method.getParameters()) {
      params.add(new ParameterCapture(param.getName(), "", false));
    }
    return params;
  }
}
