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
 * <p>INTENT: Skip constructors, class initializers, abstract methods (no body to instrument), and
 * private methods (implementation details not part of the observable API). Instruments all
 * remaining methods using the metadata gathered during the first transformation pass.
 */
public final class NarrativeClassVisitor extends ClassVisitor {

  private final String className;
  private final Map<String, MethodMetadata> metadata;

  public NarrativeClassVisitor(
      ClassVisitor cv, String className, Map<String, MethodMetadata> metadata) {
    super(Opcodes.ASM9, cv);
    this.className = className;
    this.metadata = metadata;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    if (name.equals("<init>") || name.equals("<clinit>")) {
      return mv;
    }
    if ((access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_PRIVATE) != 0) {
      return mv;
    }
    var methodMetadata = metadata.get(MethodMetadataCollector.key(name, descriptor));
    return new NarrativeMethodVisitor(mv, access, name, descriptor, className, methodMetadata);
  }
}
