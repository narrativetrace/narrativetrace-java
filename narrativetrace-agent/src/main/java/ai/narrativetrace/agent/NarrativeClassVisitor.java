/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.Map;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM class visitor that wraps eligible methods with {@link NarrativeMethodVisitor}.
 *
 * <p>INTENT: Skip constructors, class initializers, abstract methods (no body to instrument),
 * private methods (implementation details not part of the observable API), and bridge/synthetic
 * methods (list 1, structural, never a business boundary — a compiler-generated duplicate of a real
 * method, or an accessor nobody wrote). Instruments all remaining methods using the metadata
 * gathered during the first transformation pass.
 *
 * <p><b>@llmNote</b> Private methods are the one thing {@code deep} changes: with the agent's
 * opt-in {@code depth=internal} they are woven too, flagged internal so their call sites gate on a
 * deep scope rather than on tracing alone. Constructors, class initialisers, abstract methods and
 * bridge/synthetic methods (which includes every lambda body) stay out in both modes. With {@code
 * deep} false this visitor emits exactly the bytes it always did.
 */
public final class NarrativeClassVisitor extends ClassVisitor {

  private final String className;
  private final Map<String, MethodMetadata> metadata;
  private final boolean deep;

  public NarrativeClassVisitor(
      ClassVisitor cv, String className, Map<String, MethodMetadata> metadata) {
    this(cv, className, metadata, false);
  }

  public NarrativeClassVisitor(
      ClassVisitor cv, String className, Map<String, MethodMetadata> metadata, boolean deep) {
    super(Opcodes.ASM9, cv);
    this.className = className;
    this.metadata = metadata;
    this.deep = deep;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    if (name.equals("<init>") || name.equals("<clinit>")) {
      return mv;
    }
    var isPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
    if ((access & Opcodes.ACC_ABSTRACT) != 0 || (isPrivate && !deep)) {
      return mv;
    }
    if ((access & Opcodes.ACC_BRIDGE) != 0 || (access & Opcodes.ACC_SYNTHETIC) != 0) {
      return mv;
    }
    var methodMetadata = metadata.get(MethodMetadataCollector.key(name, descriptor));
    return new NarrativeMethodVisitor(
        mv, access, name, descriptor, className, methodMetadata, isPrivate);
  }
}
