/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.Map;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class BytecodeBuilder {

  protected final MethodVisitor mv;

  BytecodeBuilder(MethodVisitor mv) {
    this.mv = mv;
  }

  protected void pushInt(int value) {
    if (value >= -1 && value <= 5) {
      mv.visitInsn(Opcodes.ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.BIPUSH, value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  protected void emitBoxedValueArray(Type[] argTypes, int slotOffset) {
    pushInt(argTypes.length);
    mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
    int slot = slotOffset;
    for (int i = 0; i < argTypes.length; i++) {
      mv.visitInsn(Opcodes.DUP);
      pushInt(i);
      loadAndBox(argTypes[i], slot);
      mv.visitInsn(Opcodes.AASTORE);
      slot += argTypes[i].getSize();
    }
  }

  protected void loadAndBox(Type type, int slot) {
    mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
    emitBoxingCall(type.getSort());
  }

  private record BoxingInfo(String wrapperClass, String methodDescriptor) {}

  private static final Map<Integer, BoxingInfo> BOXING =
      Map.of(
          Type.BOOLEAN, new BoxingInfo("java/lang/Boolean", "(Z)Ljava/lang/Boolean;"),
          Type.CHAR, new BoxingInfo("java/lang/Character", "(C)Ljava/lang/Character;"),
          Type.BYTE, new BoxingInfo("java/lang/Byte", "(B)Ljava/lang/Byte;"),
          Type.SHORT, new BoxingInfo("java/lang/Short", "(S)Ljava/lang/Short;"),
          Type.INT, new BoxingInfo("java/lang/Integer", "(I)Ljava/lang/Integer;"),
          Type.LONG, new BoxingInfo("java/lang/Long", "(J)Ljava/lang/Long;"),
          Type.FLOAT, new BoxingInfo("java/lang/Float", "(F)Ljava/lang/Float;"),
          Type.DOUBLE, new BoxingInfo("java/lang/Double", "(D)Ljava/lang/Double;"));

  protected void emitBoxingCall(int typeSort) {
    var info = BOXING.get(typeSort);
    if (info != null) {
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, info.wrapperClass, "valueOf", info.methodDescriptor, false);
    }
  }

  protected void callAgentRuntime(String method, String descriptor) {
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "ai/narrativetrace/agent/AgentRuntime", method, descriptor, false);
  }

  void boxReturnValue(int opcode, String methodDesc) {
    if (opcode == Opcodes.ARETURN) {
      mv.visitInsn(Opcodes.DUP);
      return;
    }
    var returnType = Type.getReturnType(methodDesc);
    mv.visitInsn(returnType.getSize() == 2 ? Opcodes.DUP2 : Opcodes.DUP);
    emitBoxingCall(returnType.getSort());
  }
}
