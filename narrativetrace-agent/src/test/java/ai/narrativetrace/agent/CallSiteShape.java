/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The instruction shape of one instrumented method, reduced to what the call-site tests are about.
 *
 * <p>INTENT: Some invariants of the injected sequence are not observable from behaviour. "No call
 * reached the context" cannot tell "the arrays were never built" from "the arrays were built and
 * thrown away", and "the trace is correct" cannot tell a constant that is rebuilt per call from one
 * that is looked up. Those are read here, off the emitted bytecode.
 *
 * <p>The vocabulary is deliberately small: the name of every {@code AgentRuntime} call in order,
 * {@code "array"} for every array creation, {@code "branch"} for every {@code IFEQ}.
 */
final class CallSiteShape {

  private CallSiteShape() {}

  static List<String> of(byte[] transformedClass, String methodName) {
    var shape = new ArrayList<String>();
    new ClassReader(transformedClass)
        .accept(new ShapeReader(methodName, shape), ClassReader.SKIP_FRAMES);
    return shape;
  }

  private static final class ShapeReader extends ClassVisitor {

    private final String methodName;
    private final List<String> shape;

    ShapeReader(String methodName, List<String> shape) {
      super(Opcodes.ASM9);
      this.methodName = methodName;
      this.shape = shape;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return name.equals(methodName) ? new ShapeMethodReader(shape) : null;
    }
  }

  private static final class ShapeMethodReader extends MethodVisitor {

    private final List<String> shape;

    ShapeMethodReader(List<String> shape) {
      super(Opcodes.ASM9);
      this.shape = shape;
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (owner.equals("ai/narrativetrace/agent/AgentRuntime")) {
        shape.add(name);
      }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      if (opcode == Opcodes.ANEWARRAY) {
        shape.add("array");
      }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      if (opcode == Opcodes.NEWARRAY) {
        shape.add("array");
      }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      if (opcode == Opcodes.IFEQ) {
        shape.add("branch");
      }
    }
  }
}
