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

/**
 * Emits the {@code @OnError} half of the synthetic catch handler.
 *
 * <p>INTENT: The templates and the exception types they match are constants of the method, held in
 * the instrumentation-site table; only the argument values the narration interpolates travel from
 * the call. Two {@code DUP}s keep the escaping throwable available to both calls and to the {@code
 * ATHROW} that follows.
 */
final class ErrorHandlerBuilder extends BytecodeBuilder {

  private static final String RESOLVE_DESCRIPTOR =
      "(Ljava/lang/Throwable;I[Ljava/lang/Object;)Ljava/lang/String;";

  ErrorHandlerBuilder(MethodVisitor mv) {
    super(mv);
  }

  void emit(int metadataId, int paramValuesLocal) {
    mv.visitInsn(Opcodes.DUP);
    mv.visitInsn(Opcodes.DUP);
    pushInt(metadataId);
    mv.visitVarInsn(Opcodes.ALOAD, paramValuesLocal);
    callAgentRuntime("resolveErrorContext", RESOLVE_DESCRIPTOR);
    callAgentRuntime("exitMethodWithException", "(Ljava/lang/Throwable;Ljava/lang/String;)V");
  }
}
