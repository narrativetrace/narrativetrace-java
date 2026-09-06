/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.annotation.OnErrors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class MethodMetadataCollector extends ClassVisitor {

  /**
   * Annotation descriptors, derived from the classes rather than written out.
   *
   * <p><b>@llmNote</b> These were string literals until the annotations moved to {@code
   * ai.narrativetrace.api.annotation}; a literal turns the next package move into a silent
   * behaviour loss — the agent would simply stop seeing the annotation, with nothing failing to
   * compile. {@link Type#getDescriptor} cannot go stale.
   */
  private static final String NARRATED = Type.getDescriptor(Narrated.class);

  private static final String ON_ERROR = Type.getDescriptor(OnError.class);
  private static final String ON_ERRORS = Type.getDescriptor(OnErrors.class);
  private static final String NOT_TRACED = Type.getDescriptor(NotTraced.class);

  private final Map<String, MethodMetadata> metadata = new HashMap<>();
  private String sourceFile;

  MethodMetadataCollector() {
    super(Opcodes.ASM9);
  }

  /** ASM delivers the class-level SourceFile attribute before any method visit. */
  @Override
  public void visitSource(String source, String debug) {
    this.sourceFile = source;
  }

  Map<String, MethodMetadata> getMetadata() {
    return metadata;
  }

  static String key(String name, String descriptor) {
    return name + descriptor;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    if (name.equals("<init>") || name.equals("<clinit>")) {
      return null;
    }
    int paramCount = Type.getArgumentTypes(descriptor).length;
    return new MethodMetadataVisitor(name, descriptor, access, paramCount);
  }

  private final class MethodMetadataVisitor extends MethodVisitor {

    private final String name;
    private final String descriptor;
    private final int access;
    private final String[] parameterNames;
    private final boolean[] redacted;
    private boolean hasMethodParameters;
    private String narratedTemplate;
    private Integer firstLineNumber;
    private final List<MethodMetadata.OnErrorEntry> onErrors = new ArrayList<>();

    MethodMetadataVisitor(String name, String descriptor, int access, int paramCount) {
      super(Opcodes.ASM9);
      this.name = name;
      this.descriptor = descriptor;
      this.access = access;
      this.parameterNames = new String[paramCount];
      this.redacted = new boolean[paramCount];
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      if (NARRATED.equals(descriptor)) {
        return narratedVisitor();
      }
      if (ON_ERROR.equals(descriptor)) {
        return onErrorVisitor();
      }
      if (ON_ERRORS.equals(descriptor)) {
        return onErrorsContainerVisitor();
      }
      return null;
    }

    private AnnotationVisitor narratedVisitor() {
      return new AnnotationVisitor(Opcodes.ASM9) {
        @Override
        public void visit(String name, Object value) {
          if ("value".equals(name)) {
            narratedTemplate = (String) value;
          }
        }
      };
    }

    private AnnotationVisitor onErrorsContainerVisitor() {
      return new AnnotationVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitArray(String name) {
          if ("value".equals(name)) {
            return new AnnotationVisitor(Opcodes.ASM9) {
              @Override
              public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                return onErrorVisitor();
              }
            };
          }
          return null;
        }
      };
    }

    private AnnotationVisitor onErrorVisitor() {
      return new AnnotationVisitor(Opcodes.ASM9) {
        private String template;
        private String exceptionDescriptor = "Ljava/lang/Throwable;";

        @Override
        public void visit(String name, Object value) {
          if ("value".equals(name)) {
            template = (String) value;
          } else if ("exception".equals(name) && value instanceof Type type) {
            exceptionDescriptor = type.getDescriptor();
          }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
          // not relevant for @OnError
        }

        @Override
        public void visitEnd() {
          if (template != null) {
            onErrors.add(new MethodMetadata.OnErrorEntry(template, exceptionDescriptor));
          }
        }
      };
    }

    @Override
    public void visitLineNumber(int line, org.objectweb.asm.Label start) {
      if (firstLineNumber == null) {
        firstLineNumber = line;
      }
    }

    @Override
    public void visitParameter(String name, int access) {
      // Called once per parameter when MethodParameters attribute is present (-parameters flag)
      hasMethodParameters = true;
      // Find the next unfilled slot
      for (int i = 0; i < parameterNames.length; i++) {
        if (parameterNames[i] == null) {
          parameterNames[i] = name;
          break;
        }
      }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(
        int parameter, String descriptor, boolean visible) {
      if (NOT_TRACED.equals(descriptor) && parameter >= 0 && parameter < redacted.length) {
        redacted[parameter] = true;
      }
      return null;
    }

    @Override
    public void visitLocalVariable(
        String name,
        String lvDescriptor,
        String lvSignature,
        org.objectweb.asm.Label start,
        org.objectweb.asm.Label end,
        int index) {
      if (hasMethodParameters) {
        return; // MethodParameters takes priority
      }
      // Skip "this" for instance methods
      int offset = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
      int paramIndex = slotToParamIndex(index, offset);
      if (paramIndex >= 0
          && paramIndex < parameterNames.length
          && parameterNames[paramIndex] == null) {
        parameterNames[paramIndex] = name;
      }
    }

    private int slotToParamIndex(int slot, int offset) {
      if (slot < offset) {
        return -1; // "this"
      }
      var argTypes = Type.getArgumentTypes(descriptor);
      int currentSlot = offset;
      for (int i = 0; i < argTypes.length; i++) {
        if (currentSlot == slot) {
          return i;
        }
        currentSlot += argTypes[i].getSize();
      }
      return -1; // local variable, not a parameter
    }

    @Override
    public void visitEnd() {
      boolean allNull = true;
      for (var pn : parameterNames) {
        if (pn != null) {
          allNull = false;
          break;
        }
      }
      String[] names = allNull && parameterNames.length > 0 ? null : parameterNames;
      var errors = onErrors.isEmpty() ? null : onErrors.toArray(new MethodMetadata.OnErrorEntry[0]);
      metadata.put(
          key(name, descriptor),
          new MethodMetadata(
              names, redacted, narratedTemplate, errors, sourceFile, firstLineNumber));
    }
  }
}
