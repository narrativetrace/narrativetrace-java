/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds synthetic trace trees from compiled classes, without running them.
 *
 * <p>INTENT: The static half of ADR-012 harvesting. Its trees feed {@link
 * GlossaryHarvester#harvestStatic(List)}, which is the only path allowed to harvest
 * {@code @Narrated} / {@code @OnError} text: here the annotation value is the <strong>raw</strong>
 * template, placeholders intact, whereas a captured trace carries it with values interpolated.
 *
 * <p><b>@edgeCase</b> Synthetic, bridge, non-public, {@code Object}, data-class-synthesized and
 * enum-compiler methods are skipped, and a class contributing no methods contributes no tree.
 * Parameter names are real only when the scanned code was compiled with {@code -parameters};
 * otherwise they read {@code arg0}, which the harvester's normalizer discards as unusable
 * vocabulary.
 *
 * @see GlossaryHarvester#harvestStatic(List)
 * @see GlossaryScannerMain
 */
public final class GlossaryStaticScanner {

  private static final java.util.regex.Pattern COMPONENT_ACCESSOR =
      java.util.regex.Pattern.compile("component\\d+");

  /**
   * Scans every loadable class under a compiled-classes directory.
   *
   * <p>Classes are loaded in a throwaway {@link URLClassLoader}; one that cannot be loaded (a
   * missing transitive dependency) is skipped rather than failing the scan, matching the
   * best-effort contract of the clarity scanner.
   *
   * @param classesDir directory of compiled classes, typically {@code build/classes/java/main};
   *     must not be {@code null}
   * @return one tree per class that contributed at least one method; empty when the directory holds
   *     no loadable classes
   * @throws IOException if the directory cannot be read
   */
  public List<TraceTree> scan(Path classesDir) throws IOException {
    if (classesDir == null) {
      throw new IllegalArgumentException("classesDir must not be null");
    }
    var classes = new ArrayList<Class<?>>();
    var url = classesDir.toUri().toURL();
    try (var loader = new URLClassLoader(new URL[] {url}, getClass().getClassLoader());
        Stream<Path> walk = Files.walk(classesDir)) {
      walk.filter(path -> path.toString().endsWith(".class"))
          .forEach(path -> loadInto(classes, loader, classesDir, path));
    }
    return scan(classes);
  }

  private static void loadInto(
      List<Class<?>> classes, URLClassLoader loader, Path classesDir, Path classFile) {
    var relative = classesDir.relativize(classFile).toString();
    var className = relative.replace('/', '.').replace('\\', '.').replaceAll("\\.class$", "");
    try {
      classes.add(loader.loadClass(className));
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // Best-effort: a class whose dependencies are absent contributes no vocabulary.
    }
  }

  /**
   * Scans already-loaded classes.
   *
   * @param classes classes to scan; must not be {@code null}
   * @return one tree per class that contributed at least one method
   */
  public List<TraceTree> scan(List<Class<?>> classes) {
    if (classes == null) {
      throw new IllegalArgumentException("classes must not be null");
    }
    var trees = new ArrayList<TraceTree>();
    for (var type : classes) {
      var nodes = buildNodes(type);
      if (!nodes.isEmpty()) {
        trees.add(new DefaultTraceTree(nodes));
      }
    }
    return List.copyOf(trees);
  }

  private static List<TraceNode> buildNodes(Class<?> type) {
    var nodes = new ArrayList<TraceNode>();
    for (var method : type.getDeclaredMethods()) {
      if (shouldSkip(method)) {
        continue;
      }
      nodes.add(new TraceNode(signatureOf(type, method), List.of(), null));
    }
    return nodes;
  }

  private static MethodSignature signatureOf(Class<?> type, Method method) {
    var parameters = new ArrayList<ParameterCapture>();
    for (var parameter : method.getParameters()) {
      parameters.add(new ParameterCapture(parameter.getName(), "", false));
    }
    var narrated = method.getAnnotation(Narrated.class);
    var onError = method.getAnnotation(OnError.class);
    return new MethodSignature(
        type.getSimpleName(),
        method.getName(),
        List.copyOf(parameters),
        narrated == null ? null : narrated.value(),
        onError == null ? null : onError.value());
  }

  private static boolean shouldSkip(Method method) {
    return method.isSynthetic()
        || method.isBridge()
        || !Modifier.isPublic(method.getModifiers())
        || isObjectMethod(method)
        || isDataClassMember(method)
        || isEnumStatic(method);
  }

  /**
   * Whether the method is one the compiler writes into every enum.
   *
   * <p>{@code values()} and {@code valueOf(String)} exist on every enum and are not marked
   * synthetic, so they reached the harvest as the terms {@code value} and {@code value of}. The
   * declaring type is the whole test: a class that declares a {@code values()} of its own named it
   * deliberately, and it stays vocabulary.
   */
  private static boolean isEnumStatic(Method method) {
    if (!method.getDeclaringClass().isEnum() || !Modifier.isStatic(method.getModifiers())) {
      return false;
    }
    return "values".equals(method.getName()) && method.getParameterCount() == 0
        || "valueOf".equals(method.getName())
            && method.getParameterCount() == 1
            && method.getParameterTypes()[0] == String.class;
  }

  /**
   * Whether the method is one a data class had written for it rather than one someone named.
   *
   * <p>A Kotlin data class synthesizes {@code copy} and one {@code componentN} per property. The
   * JVM does not mark either synthetic, so they reach the harvest and become the terms {@code copy}
   * and {@code component 1} — language plumbing a reader never says aloud.
   *
   * <p><b>@edgeCase</b> Recognized by shape, not by name alone: {@code componentN} takes no
   * arguments, and {@code copy} returns its own declaring type and takes the properties it copies.
   * A method whose name merely begins with {@code copy}, or one called {@code copy} that does
   * neither, is ordinary vocabulary and stays.
   */
  private static boolean isDataClassMember(Method method) {
    if (COMPONENT_ACCESSOR.matcher(method.getName()).matches()) {
      return method.getParameterCount() == 0;
    }
    return "copy".equals(method.getName())
        && method.getParameterCount() > 0
        && method.getReturnType() == method.getDeclaringClass();
  }

  private static boolean isObjectMethod(Method method) {
    try {
      Object.class.getMethod(method.getName(), method.getParameterTypes());
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}
