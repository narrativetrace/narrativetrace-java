/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class ClassTransformerTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  record TransformedClass(Class<?> clazz, Object instance) {}

  private TransformedClass transformAndLoad(String sampleClass) throws Exception {
    var internalName = "ai/narrativetrace/agent/sample/" + sampleClass;
    var qualifiedName = "ai.narrativetrace.agent.sample." + sampleClass;
    var originalBytes =
        getClass().getClassLoader().getResourceAsStream(internalName + ".class").readAllBytes();
    return transformAndLoad(originalBytes, internalName, qualifiedName);
  }

  private TransformedClass transformAndLoad(
      byte[] originalBytes, String internalName, String qualifiedName) throws Exception {
    var transformed = ClassTransformer.transform(originalBytes, internalName);
    var loader = new ByteArrayClassLoader(getClass().getClassLoader(), transformed, qualifiedName);
    var clazz = loader.loadClass(qualifiedName);
    return new TransformedClass(clazz, clazz.getDeclaredConstructor().newInstance());
  }

  private TransformedClass transformAndLoad(byte[] originalBytes, String internalName)
      throws Exception {
    return transformAndLoad(originalBytes, internalName, internalName.replace('/', '.'));
  }

  private ClassWriter newClassWithConstructor(String internalName) {
    var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
    var init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(1, 1);
    init.visitEnd();
    return cw;
  }

  private void assertReturnValue(TransformedClass tc, String methodName, String expected)
      throws Exception {
    tc.clazz().getMethod(methodName).invoke(tc.instance());
    var tree = context.captureTrace();
    assertThat(((TraceOutcome.Returned) tree.roots().get(0).outcome()).renderedValue())
        .isEqualTo(expected);
    context.reset();
  }

  @Test
  void transformedClassProducesCorrectTraceForSimpleMethod() throws Exception {
    // Get the original bytecode of the sample class
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    // Transform it
    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");

    // Load transformed class
    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var instance = clazz.getDeclaredConstructor().newInstance();
    var method = clazz.getMethod("add", int.class, int.class);

    var result = method.invoke(instance, 3, 4);

    assertThat(result).isEqualTo(7);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("Calculator");
    assertThat(root.signature().packageName()).isEqualTo("ai.narrativetrace.agent.sample");
    assertThat(root.signature().methodName()).isEqualTo("add");
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("7");
  }

  @Test
  void transformedClassCapturesExceptionInTrace() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var instance = clazz.getDeclaredConstructor().newInstance();
    var method = clazz.getMethod("divide", int.class, int.class);

    try {
      method.invoke(instance, 10, 0);
    } catch (Exception e) {
      // expected
    }

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("divide");
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void transformedClassCapturesParameterNamesAndValues() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("add", int.class, int.class).invoke(instance, 3, 4);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture("a", "3", false, new RenderedValue.LongVal(3), "int"),
            new ParameterCapture("b", "4", false, new RenderedValue.LongVal(4), "int"));
  }

  @Test
  void transformedClassCapturesBakedSourceLocationWhenEnabled() throws Exception {
    var config = new ai.narrativetrace.core.config.NarrativeTraceConfig();
    config.setCaptureSourceLocation(true);
    var srcContext = new ThreadLocalNarrativeContext(config);
    srcContext.reset();
    AgentRuntime.setContext(srcContext);

    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();
    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");
    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("add", int.class, int.class).invoke(instance, 3, 4);

    var source = srcContext.captureTrace().roots().get(0).signature().source();
    assertThat(source).isNotNull();
    assertThat(source.file()).isEqualTo("Calculator.java");
    assertThat(source.line()).isPositive();
  }

  @Test
  void transformedClassResolvesNarratedTemplate() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("calculatePrice", String.class, int.class).invoke(instance, "widget", 5);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().narration()).isEqualTo("Processing widget for quantity 5");
  }

  @Test
  void transformedClassResolvesOnErrorContext() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();

    try {
      clazz
          .getMethod("transfer", String.class, String.class, int.class)
          .invoke(instance, "alice", "bob", -50);
    } catch (Exception e) {
      // expected
    }

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(root.signature().errorContext()).isEqualTo("Transfer failed for amount -50");
  }

  @Test
  void transformedClassRedactsNotTracedParameter() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("login", String.class, String.class).invoke(instance, "alice", "secret123");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture(
                "username",
                "\"alice\"",
                false,
                new RenderedValue.StringVal("alice"),
                "java.lang.String"),
            new ParameterCapture("password", "[REDACTED]", true, null, "java.lang.String"));
  }

  @Test
  void transformedClassRedactsNotTracedParameterInNarratedTemplate() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz
        .getMethod("authenticate", String.class, String.class)
        .invoke(instance, "admin", "correct-horse");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().narration())
        .isEqualTo("Authenticating admin with password [REDACTED]")
        .doesNotContain("correct-horse");
  }

  @Test
  void transformedClassRedactsNotTracedParameterInOnErrorTemplate() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();

    try {
      clazz
          .getMethod("authenticate", String.class, String.class)
          .invoke(instance, "admin", "wrong-password");
    } catch (Exception e) {
      // expected
    }

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().errorContext())
        .isEqualTo("Authentication failed for admin with password [REDACTED]")
        .doesNotContain("wrong-password");
  }

  @Test
  void singleOnErrorAnnotationResolvesContext() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/AnnotatedService.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(
            originalBytes, "ai/narrativetrace/agent/sample/AnnotatedService");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(),
            transformed,
            "ai.narrativetrace.agent.sample.AnnotatedService");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.AnnotatedService");
    var instance = clazz.getDeclaredConstructor().newInstance();

    try {
      clazz.getMethod("lookup", String.class).invoke(instance, (Object) null);
    } catch (Exception e) {
      // expected
    }

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(root.signature().errorContext()).startsWith("Lookup failed for id ");
  }

  @Test
  void methodWithoutParameterNamesUsesSimpleEnterMethod() throws Exception {
    var tc = transformAndLoad(generateClassWithoutParamNames(), "test/NoParams");
    var result = tc.clazz().getMethod("compute", int.class, int.class).invoke(tc.instance(), 5, 3);

    assertThat(result).isEqualTo(8);
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().parameters()).isEmpty();
  }

  private byte[] generateClassWithoutParamNames() {
    var cw = newClassWithConstructor("test/NoParams");
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "compute", "(II)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IADD);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(2, 3);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void untransformedClassesPassThrough() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.test");

    assertThat(config.shouldTransform("java/lang/String")).isFalse();
    assertThat(config.shouldTransform("com/other/Service")).isFalse();
  }

  @Test
  void transformedClassTracesAllReturnTypes() throws Exception {
    var tc = transformAndLoad("TypeVariety");

    assertReturnValue(tc, "isReady", "true");
    assertReturnValue(tc, "timestamp", "1234567890");
    assertReturnValue(tc, "ratio", "3.14");
    assertReturnValue(tc, "weight", "2.5");
    assertReturnValue(tc, "doNothing", null); // void: no rendered value per the Returned contract
    assertReturnValue(tc, "greeting", "\"hello\"");
    assertReturnValue(tc, "initial", "A");
    assertReturnValue(tc, "shortValue", "42");
    assertReturnValue(tc, "byteValue", "7");
  }

  @Test
  void allPrimitiveParameterTypesAreCaptured() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/TypeVariety.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/TypeVariety");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.TypeVariety");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.TypeVariety");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz
        .getMethod(
            "allPrimitiveParams", boolean.class, char.class, byte.class, short.class, float.class)
        .invoke(instance, true, 'X', (byte) 3, (short) 7, 1.5f);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture(
                "flag", "true", false, new RenderedValue.BooleanVal(true), "boolean"),
            new ParameterCapture("letter", "X", false, new RenderedValue.StringVal("X"), "char"),
            new ParameterCapture("b", "3", false, new RenderedValue.LongVal(3), "byte"),
            new ParameterCapture("s", "7", false, new RenderedValue.LongVal(7), "short"),
            new ParameterCapture("f", "1.5", false, new RenderedValue.DoubleVal(1.5), "float"));
  }

  @Test
  void noParamMethodProducesEmptyParameterList() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/TypeVariety.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/TypeVariety");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.TypeVariety");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.TypeVariety");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("doNothing").invoke(instance);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters()).isEmpty();
  }

  @Test
  void wideTypeParametersHaveCorrectSlotCalculation() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz
        .getMethod("wideParams", long.class, double.class, int.class)
        .invoke(instance, 100L, 2.5, 3);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture("x", "100", false, new RenderedValue.LongVal(100), "long"),
            new ParameterCapture("y", "2.5", false, new RenderedValue.DoubleVal(2.5), "double"),
            new ParameterCapture("z", "3", false, new RenderedValue.LongVal(3), "int"));
  }

  @Test
  void staticMethodParametersStartAtSlotZero() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/Calculator");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.Calculator");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    var result = clazz.getMethod("staticAdd", int.class, int.class).invoke(null, 10, 20);

    assertThat(result).isEqualTo(30);
    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture("a", "10", false, new RenderedValue.LongVal(10), "int"),
            new ParameterCapture("b", "20", false, new RenderedValue.LongVal(20), "int"));
  }

  @Test
  void manyParamsUsesBipushForArrayIndex() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/TypeVariety.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/TypeVariety");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.TypeVariety");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.TypeVariety");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz
        .getMethod(
            "manyParams",
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class)
        .invoke(instance, 1, 2, 3, 4, 5, 6, 7);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().parameters()).hasSize(7);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("28");
  }

  @Test
  void partialParamNamesFallBackToArgIndex() throws Exception {
    var tc = transformAndLoad(generatePartialNamesClass(), "test/PartialNames");
    tc.clazz().getMethod("compute", int.class, int.class).invoke(tc.instance(), 5, 3);

    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().parameters()).hasSize(2);
    assertThat(root.signature().parameters().get(0).name()).isEqualTo("named");
    assertThat(root.signature().parameters().get(1).name()).isEqualTo("arg1");
  }

  private byte[] generatePartialNamesClass() {
    var cw = newClassWithConstructor("test/PartialNames");
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "compute", "(II)I", null, null);
    mv.visitCode();
    var start = new org.objectweb.asm.Label();
    var end = new org.objectweb.asm.Label();
    mv.visitLabel(start);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IADD);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitLabel(end);
    mv.visitLocalVariable("this", "Ltest/PartialNames;", null, start, end, 0);
    mv.visitLocalVariable("named", "I", null, start, end, 1);
    mv.visitMaxs(2, 3);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void wideParamsResolvedViaLocalVariableTable() throws Exception {
    var tc = transformAndLoad(generateWideLocalsClass(), "test/WideLocals");
    var result =
        tc.clazz()
            .getMethod("process", long.class, double.class, int.class)
            .invoke(tc.instance(), 100L, 2.5, 3);

    assertThat(result).isEqualTo(100L);
    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().parameters())
        .containsExactly(
            new ParameterCapture("amount", "100", false, new RenderedValue.LongVal(100), "long"),
            new ParameterCapture("rate", "2.5", false, new RenderedValue.DoubleVal(2.5), "double"),
            new ParameterCapture("count", "3", false, new RenderedValue.LongVal(3), "int"));
  }

  private byte[] generateWideLocalsClass() {
    var cw = newClassWithConstructor("test/WideLocals");
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "process", "(JDI)J", null, null);
    mv.visitCode();
    var start = new org.objectweb.asm.Label();
    var end = new org.objectweb.asm.Label();
    mv.visitLabel(start);
    mv.visitVarInsn(Opcodes.LLOAD, 1);
    mv.visitInsn(Opcodes.LRETURN);
    mv.visitLabel(end);
    mv.visitLocalVariable("this", "Ltest/WideLocals;", null, start, end, 0);
    mv.visitLocalVariable("amount", "J", null, start, end, 1);
    mv.visitLocalVariable("rate", "D", null, start, end, 3);
    mv.visitLocalVariable("count", "I", null, start, end, 5);
    mv.visitMaxs(2, 6);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void abstractMethodsAreSkippedSoAbstractClassesRemainLoadable() throws Exception {
    var bytes = generateAbstractClass();
    var transformed = ClassTransformer.transform(bytes, "test/AbstractService");
    var loader =
        new ByteArrayClassLoader(getClass().getClassLoader(), transformed, "test.AbstractService");
    loader.loadClass("test.AbstractService");
    // no exception = abstract class is still loadable after transformation
  }

  private byte[] generateAbstractClass() {
    var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        "test/AbstractService",
        null,
        "java/lang/Object",
        null);
    var init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(1, 1);
    init.visitEnd();
    cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "compute", "()I", null, null)
        .visitEnd();
    var concrete = cw.visitMethod(Opcodes.ACC_PUBLIC, "ping", "()I", null, null);
    concrete.visitCode();
    concrete.visitInsn(Opcodes.ICONST_1);
    concrete.visitInsn(Opcodes.IRETURN);
    concrete.visitMaxs(1, 1);
    concrete.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void privateHelperMethodsAreNotTraced() throws Exception {
    var tc = transformAndLoad(generateClassWithPrivateHelper(), "test/HelperService");
    tc.clazz().getMethod("callHelper").invoke(tc.instance());

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("callHelper");
    assertThat(tree.roots().get(0).children()).isEmpty();
  }

  private byte[] generateClassWithPrivateHelper() {
    var cw = newClassWithConstructor("test/HelperService");
    var callHelper = cw.visitMethod(Opcodes.ACC_PUBLIC, "callHelper", "()I", null, null);
    callHelper.visitCode();
    callHelper.visitVarInsn(Opcodes.ALOAD, 0);
    callHelper.visitMethodInsn(Opcodes.INVOKESPECIAL, "test/HelperService", "helper", "()I", false);
    callHelper.visitInsn(Opcodes.IRETURN);
    callHelper.visitMaxs(1, 1);
    callHelper.visitEnd();
    var helper = cw.visitMethod(Opcodes.ACC_PRIVATE, "helper", "()I", null, null);
    helper.visitCode();
    helper.visitIntInsn(Opcodes.BIPUSH, 7);
    helper.visitInsn(Opcodes.IRETURN);
    helper.visitMaxs(1, 1);
    helper.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void onErrorWithoutParameterNamesDoesNotCrash() throws Exception {
    var tc = transformAndLoad(generateOnErrorNoParamsClass(), "test/OnErrorNoParams");

    try {
      tc.clazz().getMethod("process", int.class).invoke(tc.instance(), 42);
    } catch (Exception e) {
      // expected
    }

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(tree.roots().get(0).signature().parameters()).isEmpty();
  }

  private byte[] generateOnErrorNoParamsClass() {
    var cw = newClassWithConstructor("test/OnErrorNoParams");
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "process", "(I)V", null, null);
    var av = mv.visitAnnotation(Type.getDescriptor(OnError.class), true);
    av.visit("value", "Processing failed for input");
    av.visitEnd();
    mv.visitCode();
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn("boom");
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/lang/RuntimeException",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    mv.visitInsn(Opcodes.ATHROW);
    mv.visitMaxs(3, 2);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void constructorsAndStaticInitializersAreNotInstrumented() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/StaticInit.class")
            .readAllBytes();

    var transformed =
        ClassTransformer.transform(originalBytes, "ai/narrativetrace/agent/sample/StaticInit");

    var loader =
        new ByteArrayClassLoader(
            getClass().getClassLoader(), transformed, "ai.narrativetrace.agent.sample.StaticInit");
    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.StaticInit");
    var instance = clazz.getDeclaredConstructor().newInstance();
    clazz.getMethod("value").invoke(instance);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("value");
  }
}
