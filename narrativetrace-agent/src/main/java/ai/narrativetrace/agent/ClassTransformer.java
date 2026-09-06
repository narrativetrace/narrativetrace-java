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

  public static byte[] transform(byte[] classfileBuffer, String className, ClassLoader loader) {
    var reader = new ClassReader(classfileBuffer);

    // Pass 1: collect metadata (parameter names, annotations)
    var collector = new MethodMetadataCollector();
    reader.accept(collector, 0);

    // Pass 2: transform with metadata
    var writer =
        new DefiningLoaderClassWriter(
            reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, loader);
    var visitor = new NarrativeClassVisitor(writer, className, collector.getMetadata());
    reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }

  /**
   * COMPUTE_FRAMES resolves types through {@link ClassWriter#getClassLoader()}, which defaults to
   * the writer's own (the agent's) loader. Under an app server the class being transformed
   * references types the agent's loader cannot see — its own deployment neighbours, container APIs
   * like {@code jakarta.servlet} — so any method needing a frame merge over such types failed to
   * instrument. The class's defining loader, handed to every {@code transform} call, sees them all.
   */
  private static final class DefiningLoaderClassWriter extends ClassWriter {

    private final ClassLoader definingLoader;

    DefiningLoaderClassWriter(ClassReader reader, int flags, ClassLoader definingLoader) {
      super(reader, flags);
      this.definingLoader = definingLoader;
    }

    @Override
    protected ClassLoader getClassLoader() {
      // null = bootstrap-defined class; the agent's own loader is the best remaining guess.
      return definingLoader != null ? definingLoader : ClassTransformer.class.getClassLoader();
    }
  }
}
