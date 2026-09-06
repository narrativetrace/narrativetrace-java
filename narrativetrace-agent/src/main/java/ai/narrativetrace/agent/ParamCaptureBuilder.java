/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Emits the enter call of an instrumented method.
 *
 * <p>INTENT: Everything constant about the method — its names, its redaction flags, its declared
 * types — was registered once at instrumentation time and is reached through an {@code int} id. All
 * that is left to build per call is the array of argument values, which is the only part that
 * depends on the call.
 */
final class ParamCaptureBuilder extends BytecodeBuilder {

  private static final String ENTER_DESCRIPTOR =
      "(I[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;";

  ParamCaptureBuilder(MethodVisitor mv) {
    super(mv);
  }

  /**
   * Pushes the enter call.
   *
   * @param metadataId the id the instrumentation-site table minted for this method
   * @param argTypes the arguments to box, empty for a method whose parameters cannot be named
   * @param slotOffset first argument slot — 1 past {@code this} for an instance method
   * @param paramValuesLocal slot to keep the value array in for the catch handler, or {@code -1}
   */
  void emit(int metadataId, Type[] argTypes, int slotOffset, int paramValuesLocal) {
    pushInt(metadataId);
    emitBoxedValueArray(argTypes, slotOffset);
    storeIfNeeded(paramValuesLocal);
    emitReceiver(slotOffset == 0);
    callAgentRuntime("enterMethod", ENTER_DESCRIPTOR);
  }

  /** Loads {@code this} for instance methods, {@code null} for static ones. */
  void emitReceiver(boolean isStatic) {
    if (isStatic) {
      mv.visitInsn(Opcodes.ACONST_NULL);
    } else {
      mv.visitVarInsn(Opcodes.ALOAD, 0);
    }
  }

  /** Keeps a copy of the value array just emitted, for the catch handler to read back. */
  private void storeIfNeeded(int local) {
    if (local < 0) return;
    mv.visitInsn(Opcodes.DUP);
    mv.visitVarInsn(Opcodes.ASTORE, local);
  }
}
