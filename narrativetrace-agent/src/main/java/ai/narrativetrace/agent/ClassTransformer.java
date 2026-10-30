/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Two-pass ASM transformer for one class.
 *
 * <p>INTENT: Keep metadata collection separate from bytecode rewriting so parameter names and
 * annotation templates are available before instrumentation code is emitted.
 */
public final class ClassTransformer {

  private ClassTransformer() {}

  public static byte[] transform(byte[] classfileBuffer, String className) {
    return transform(classfileBuffer, className, null);
  }

  public static byte[] transform(
      byte[] classfileBuffer,
      String className,
      ClassLoader loader,
      ai.narrativetrace.api.config.TraceDepth depth) {
    return transform(
        classfileBuffer,
        className,
        loader,
        depth == ai.narrativetrace.api.config.TraceDepth.INTERNAL);
  }

  public static byte[] transform(byte[] classfileBuffer, String className, ClassLoader loader) {
    return transform(classfileBuffer, className, loader, false);
  }

  private static byte[] transform(
      byte[] classfileBuffer, String className, ClassLoader loader, boolean deep) {
    var reader = new ClassReader(classfileBuffer);

    // Pass 1: collect metadata (parameter names, annotations)
    var collector = new MethodMetadataCollector();
    reader.accept(collector, 0);

    // Pass 2: transform with metadata
    var writer =
        new DefiningLoaderClassWriter(
            reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, loader);
    var visitor = new NarrativeClassVisitor(writer, className, collector.getMetadata(), deep);
    reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }

  /**
   * COMPUTE_FRAMES resolves types through {@link ClassWriter#getClassLoader()}, which defaults to
   * the writer's own (the agent's) loader. Under an app server the class being transformed
   * references types the agent's loader cannot see — its own deployment neighbours, container APIs
   * like {@code jakarta.servlet} — so any method needing a frame merge over such types failed to
   * instrument. The class's defining loader, handed to every {@code transform} call, sees them all.
   *
   * <p><b>@llmNote</b> That loader supplies class FILES, and {@link ClassWriter#getClassLoader()}
   * is deliberately left alone: ASM consults it only from the default {@code getCommonSuperClass},
   * which is overridden here precisely so no part of a transform can ask a loader to LOAD a class.
   * A transform runs inside the host's own {@code defineClass}, where one load request defines the
   * woven class a second time and the host's define then fails as a duplicate (B-213) — see {@link
   * ClassFileTypeHierarchy} for the mechanism and the invariant.
   */
  private static final class DefiningLoaderClassWriter extends ClassWriter {

    private final ClassFileTypeHierarchy hierarchy;

    DefiningLoaderClassWriter(ClassReader reader, int flags, ClassLoader definingLoader) {
      super(reader, flags);
      // null = bootstrap-defined class; the agent's own loader is the best remaining guess.
      // LoaderVisibility refuses to weave those, so only direct callers reach this arm.
      var classFiles =
          definingLoader != null ? definingLoader : ClassTransformer.class.getClassLoader();
      this.hierarchy = new ClassFileTypeHierarchy(classFiles, reader);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
      return hierarchy.commonSuperClass(type1, type2);
    }
  }
}
