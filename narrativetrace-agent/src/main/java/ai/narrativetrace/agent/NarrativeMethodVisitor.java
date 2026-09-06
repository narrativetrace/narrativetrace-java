/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * ASM method visitor that injects NarrativeTrace capture calls.
 *
 * <p>INTENT: For each eligible method, this visitor emits enter logic, normal-return handling,
 * scope restoration, and a synthetic catch path that records exceptions <em>escaping the
 * method</em> before rethrowing them.
 *
 * <p><b>@llmNote</b> The synthetic try-catch entry is registered in {@link #visitMaxs} — after all
 * original exception-table entries have been replayed — so it sits last in the table and never
 * shadows the method's own {@code catch}/{@code finally}/{@code synchronized} handlers. Exceptions
 * handled inside the method are recorded as normal returns.
 *
 * <p><b>@llmNote</b> The gate comes first. {@link AgentRuntime#isActive()} is called before any
 * argument is marshalled, and every injected call — enter, scope restore, both exits — sits behind
 * it: with tracing off the instrumented method allocates nothing and calls nothing. Two things this
 * depends on and an edit must preserve:
 *
 * <ul>
 *   <li>The gate is emitted <em>outside</em> the synthetic try range (like the enter call it
 *       guards), so it must not throw into the host. {@code isActive()} is total for that reason —
 *       keep it a plain static boolean read.
 *   <li>The exits are guarded by a {@code traced} local set only when the enter branch ran, not by
 *       a second {@code isActive()} read. A context swapped mid-call must not produce an exit
 *       without its enter: that would restore a scope nobody began and close a span nobody opened.
 * </ul>
 *
 * <p><b>@llmNote</b> Behind the gate, the only thing the call site builds is the {@code Object[]}
 * of argument values. Names, redaction flags, templates, the split class name and the declared
 * types are registered once here, at instrumentation time, and reached through the {@code int} id
 * this visitor bakes in — see {@link AgentMethodMetadata}. One id belongs to one instrumented
 * method of one transform, which is what keeps two same-named classes in different loaders apart.
 */
public final class NarrativeMethodVisitor extends AdviceAdapter {

  private static final String STRING_TYPE = "Ljava/lang/String;";
  private static final Type[] NO_ARGUMENTS = new Type[0];

  private final String methodName;
  private final String className;
  private final MethodMetadata metadata;
  private final Label tryStart = new Label();
  private final Label tryEnd = new Label();
  private final Label catchHandler = new Label();
  private final Label inactive = new Label();
  private final ParamCaptureBuilder paramCaptureBuilder;
  private final ErrorHandlerBuilder errorHandlerBuilder;
  private int metadataId;
  private int paramValuesLocal = -1;
  private int prevScopeLocal;
  private int tracedLocal;

  protected NarrativeMethodVisitor(
      MethodVisitor mv,
      int access,
      String name,
      String descriptor,
      String className,
      MethodMetadata metadata) {
    super(Opcodes.ASM9, mv, access, name, descriptor);
    this.methodName = name;
    this.className = className.replace('/', '.');
    this.metadata = metadata;
    this.paramCaptureBuilder = new ParamCaptureBuilder(mv);
    this.errorHandlerBuilder = new ErrorHandlerBuilder(mv);
  }

  @Override
  protected void onMethodEnter() {
    prevScopeLocal = newLocal(Type.getType(String.class));
    tracedLocal = newLocal(Type.BOOLEAN_TYPE);
    paramValuesLocal = narratesErrors() ? newLocal(Type.getType("[Ljava/lang/Object;")) : -1;
    metadataId = AgentRuntime.registerMethod(describeMethod());
    initialiseLocals();

    paramCaptureBuilder.callAgentRuntime("isActive", "()Z");
    mv.visitJumpInsn(Opcodes.IFEQ, inactive);
    emitEnter();
    mv.visitVarInsn(ASTORE, prevScopeLocal);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitVarInsn(ISTORE, tracedLocal);
    mv.visitLabel(inactive);

    mv.visitLabel(tryStart);
  }

  /** The constants of this method, as the runtime will read them back through the baked id. */
  private AgentMethodMetadata describeMethod() {
    return capturesParameters()
        ? AgentMethodMetadata.of(className, methodName, methodDesc, metadata)
        : AgentMethodMetadata.bare(className, methodName, methodDesc);
  }

  /** Leaves the previous scoped parent (a {@code String}, possibly null) on the stack. */
  private void emitEnter() {
    boolean isStatic = (methodAccess & Opcodes.ACC_STATIC) != 0;
    var argTypes = capturesParameters() ? Type.getArgumentTypes(methodDesc) : NO_ARGUMENTS;
    paramCaptureBuilder.emit(metadataId, argTypes, isStatic ? 0 : 1, paramValuesLocal);
  }

  /**
   * Whether the class file names this method's parameters. Without names there is nothing to label
   * a captured value with, so no value is marshalled and no template is interpolated.
   */
  private boolean capturesParameters() {
    return metadata != null && metadata.parameterNames() != null;
  }

  /** Whether the catch handler resolves an {@code @OnError} narration — which needs both. */
  private boolean narratesErrors() {
    return capturesParameters() && metadata.onErrors() != null;
  }

  /**
   * Every local the injected code reads is written on the inactive path too: the verifier types a
   * local by every path that reaches the read, and the inactive path assigns none of them.
   */
  private void initialiseLocals() {
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitVarInsn(ASTORE, prevScopeLocal);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(ISTORE, tracedLocal);
    if (paramValuesLocal >= 0) {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitVarInsn(ASTORE, paramValuesLocal);
    }
  }

  @Override
  protected void onMethodExit(int opcode) {
    if (opcode == ATHROW) {
      return;
    }
    var untraced = new Label();
    mv.visitVarInsn(ILOAD, tracedLocal);
    mv.visitJumpInsn(Opcodes.IFEQ, untraced);
    emitEndScope();
    if (opcode == Opcodes.RETURN) {
      // Void methods have no value to box: record completion without a rendered value
      // (Returned.renderedValue == null is the documented void contract).
      paramCaptureBuilder.callAgentRuntime("exitMethodVoid", "()V");
    } else {
      paramCaptureBuilder.boxReturnValue(opcode, methodDesc);
      paramCaptureBuilder.callAgentRuntime("exitMethodWithReturn", "(Ljava/lang/Object;)V");
    }
    mv.visitLabel(untraced);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    // Registered here, after the original method's try-catch blocks have been visited, so this
    // entry is LAST in the exception table: user catch/finally handlers keep dispatch priority,
    // and the synthetic handler only sees exceptions that escape the method.
    mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
    mv.visitLabel(tryEnd);
    mv.visitLabel(catchHandler);

    var untraced = new Label();
    mv.visitVarInsn(ILOAD, tracedLocal);
    mv.visitJumpInsn(Opcodes.IFEQ, untraced);
    emitEndScope();
    if (narratesErrors()) {
      errorHandlerBuilder.emit(metadataId, paramValuesLocal);
    } else {
      mv.visitInsn(Opcodes.DUP);
      errorHandlerBuilder.callAgentRuntime("exitMethodWithException", "(Ljava/lang/Throwable;)V");
    }
    mv.visitLabel(untraced);
    mv.visitInsn(Opcodes.ATHROW);

    super.visitMaxs(maxStack, maxLocals);
  }

  private void emitEndScope() {
    mv.visitVarInsn(ALOAD, prevScopeLocal);
    paramCaptureBuilder.callAgentRuntime("endScope", "(" + STRING_TYPE + ")V");
  }
}
