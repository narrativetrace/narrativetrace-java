/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

/**
 * The supertype relation a frame merge needs, read from class FILES instead of loaded classes.
 *
 * <p>INTENT: {@code COMPUTE_FRAMES} asks for the common supertype of two merged types, and ASM's
 * default answer is {@code Class.forName} on both. A transform runs <em>inside</em> the JVM's own
 * {@code defineClass} for the class being woven — that is where the class-file load hook is posted
 * from — so a load request there re-enters the host's loader for a name whose definition is still
 * in flight: {@code findLoadedClass} answers "not loaded", the loader reads the class file and
 * defines the class, and the define the host started then fails with {@code LinkageError: loader
 * 'app' attempted duplicate … definition for} its own class (B-213). This class answers the same
 * question by reading class files, so weaving can never define anything.
 *
 * <p><b>@llmNote</b> THE INVARIANT: nothing on the transform path may ask a loader to load a class.
 * Resolution here goes through {@link ClassLoader#getResourceAsStream} only — a resource read,
 * never a class definition — and the class being transformed is seeded from the very bytes being
 * rewritten, which are authoritative where its class file on disk may not be. The JVM does not
 * re-post the class-file load hook for a load started from inside one, so a class defined that way
 * is also silently <em>uninstrumented</em>: the duplicate-definition error is the loud half of this
 * bug, and a class that quietly loses its narration is the other.
 *
 * <p><b>@edgeCase</b> A merge participant with no readable class file — a type generated at runtime
 * and never written anywhere — raises {@link TypeNotPresentException}, exactly as ASM's default
 * does for a name it cannot resolve. {@link NarrativeClassFileTransformer} then reports the class
 * as uninstrumented and the host loads it untouched. Answering {@code java/lang/Object} instead
 * would be a guess that writes a frame the verifier rejects, turning an agent's blind spot into a
 * {@code VerifyError} inside the host's own call.
 *
 * <p><b>@edgeCase</b> Answers match {@link org.objectweb.asm.ClassWriter#getCommonSuperClass}'s
 * contract, including its two non-obvious cases: {@code java/lang/Object} whenever either side is
 * an interface (ASM never searches for a common interface), and {@code java/lang/Object} for two
 * distinct array types, which is what the default implementation's superclass walk arrives at.
 *
 * <p><b>@threadSafety</b> One instance per transformed class, confined to the thread performing
 * that transform — the thread the JVM is defining the class on. The cache is therefore a plain
 * {@link HashMap} on purpose; sharing one across loaders would need weak keys and a lock held
 * across a host loader's resource lookup, which is how an agent deadlocks its host.
 */
final class ClassFileTypeHierarchy {

  private static final String OBJECT = "java/lang/Object";

  /** The root: no superclass, no interfaces, not an interface itself. */
  private static final Supertypes OBJECT_SUPERTYPES = new Supertypes(null, List.of(), false);

  /**
   * An array type's superclass is {@code Object}; its {@code Cloneable}/{@code Serializable} marks
   * are left out because ASM's own answer for two distinct array types is {@code Object} too.
   */
  private static final Supertypes ARRAY_SUPERTYPES = new Supertypes(OBJECT, List.of(), false);

  /** The source of class FILES — never of classes. */
  private final ClassLoader classFiles;

  private final Map<String, Supertypes> resolved = new HashMap<>();

  /**
   * @param classFiles the loader whose resources are read, normally the class's defining loader
   * @param transformed a reader over the bytes being rewritten, seeding the class's own supertypes
   */
  ClassFileTypeHierarchy(ClassLoader classFiles, ClassReader transformed) {
    if (classFiles == null || transformed == null) {
      throw new IllegalArgumentException(
          "a class-file source and the transformed class are needed");
    }
    this.classFiles = classFiles;
    resolved.put(OBJECT, OBJECT_SUPERTYPES);
    resolved.put(transformed.getClassName(), supertypesOf(transformed));
    assert invariant() : "invariant violated after construction";
  }

  /**
   * The nearest common supertype of two merged reference types, in internal form.
   *
   * @throws TypeNotPresentException when a type's class file cannot be read through {@link
   *     #classFiles}
   */
  String commonSuperClass(String type1, String type2) {
    if (type1 == null || type2 == null) {
      throw new IllegalArgumentException("both merged types are required");
    }
    if (isSupertypeOf(type1, type2)) {
      return type1;
    }
    if (isSupertypeOf(type2, type1)) {
      return type2;
    }
    if (isInterface(type1) || isInterface(type2)) {
      return OBJECT;
    }
    var candidate = supertypes(type1).superName();
    while (candidate != null && !isSupertypeOf(candidate, type2)) {
      candidate = supertypes(candidate).superName();
    }
    assert invariant() : "invariant violated after commonSuperClass";
    return candidate == null ? OBJECT : candidate;
  }

  /**
   * Whether {@code supertype} is {@code subtype} itself, one of its superclasses, or one of the
   * interfaces either implements — the assignability ASM's default reads off loaded classes.
   */
  private boolean isSupertypeOf(String supertype, String subtype) {
    if (OBJECT.equals(supertype)) {
      return true;
    }
    var pending = new ArrayDeque<String>();
    var visited = new HashSet<String>();
    pending.add(subtype);
    while (!pending.isEmpty()) {
      var next = pending.remove();
      if (visited.add(next)) {
        if (supertype.equals(next)) {
          return true;
        }
        enqueueSupertypes(next, pending);
      }
    }
    return false;
  }

  /**
   * <b>@llmNote</b> The visited set above is not an optimisation: a diamond of interfaces reaches
   * the same name twice, and bytes claiming a cyclic hierarchy would otherwise walk forever inside
   * a class definition the host is waiting on.
   */
  private void enqueueSupertypes(String internalName, ArrayDeque<String> pending) {
    var above = supertypes(internalName);
    if (above.superName() != null) {
      pending.add(above.superName());
    }
    pending.addAll(above.interfaces());
  }

  private boolean isInterface(String internalName) {
    return supertypes(internalName).isInterface();
  }

  private Supertypes supertypes(String internalName) {
    var known = resolved.get(internalName);
    if (known != null) {
      return known;
    }
    var read = read(internalName);
    resolved.put(internalName, read);
    return read;
  }

  /**
   * The supertypes declared by a name's class file.
   *
   * <p><b>@sideEffects</b> One resource read through {@link #classFiles}. No class is loaded,
   * linked or initialised — that is the whole point of this type.
   */
  private Supertypes read(String internalName) {
    if (internalName.startsWith("[")) {
      return ARRAY_SUPERTYPES;
    }
    try (var classFile = classFiles.getResourceAsStream(internalName + ".class")) {
      if (classFile == null) {
        throw new TypeNotPresentException(internalName, null);
      }
      return supertypesOf(new ClassReader(classFile));
    } catch (IOException | IllegalArgumentException unreadable) {
      // Unreachable bytes, or a class file ASM's version range does not cover: both mean this
      // merge cannot be answered honestly, and a guess here becomes the host's VerifyError.
      throw new TypeNotPresentException(internalName, unreadable);
    }
  }

  private static Supertypes supertypesOf(ClassReader classFile) {
    return new Supertypes(
        classFile.getSuperName(),
        List.of(classFile.getInterfaces()),
        (classFile.getAccess() & Opcodes.ACC_INTERFACE) != 0);
  }

  /** The root is always known and no name ever maps to a missing answer. */
  boolean invariant() {
    return OBJECT_SUPERTYPES.equals(resolved.get(OBJECT)) && !resolved.containsValue(null);
  }

  /** What one class file says about everything above it. */
  private record Supertypes(String superName, List<String> interfaces, boolean isInterface) {}
}
