/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * What the instrumented call site still builds per call, and what it looks up instead.
 *
 * <p>INTENT: Parameter names, redaction flags and the declared types are constants of the method,
 * not of the call. They are registered once while the method is being instrumented and reached
 * through an {@code int} id, so the only array an active call builds is the one holding its own
 * argument values.
 *
 * <p><b>@edgeCase</b> The cross-classloader case is the one that decides whether the hoist is
 * sound. The proxy's equivalent cache is keyed by {@code Method}, which compares by {@code equals},
 * and that aliasing once made a hoist there return another proxy's state. Here two classes with the
 * same qualified name, the same descriptor and different parameter names are instrumented and
 * defined in two loaders — if either could read the other's constants, the capture would say so.
 */
class CallSiteConstantsTest {

  private static final String TWIN_INTERNAL = "ai/narrativetrace/agent/sample/Twin";
  private static final String TWIN_QUALIFIED = "ai.narrativetrace.agent.sample.Twin";

  private NarrativeContext original;
  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    original = AgentRuntime.getContext();
    context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    context.reset();
    AgentRuntime.setContext(context);
  }

  @AfterEach
  void tearDown() {
    context.reset();
    AgentRuntime.setContext(original);
  }

  private static byte[] originalBytes(String internalName) throws Exception {
    return CallSiteConstantsTest.class
        .getClassLoader()
        .getResourceAsStream(internalName + ".class")
        .readAllBytes();
  }

  @Test
  void anActiveCallBuildsOneArrayForItsArgumentValuesAndNoOther() throws Exception {
    var internal = "ai/narrativetrace/agent/sample/Calculator";
    var transformed = ClassTransformer.transform(originalBytes(internal), internal);

    var shape = CallSiteShape.of(transformed, "add");

    assertThat(shape).filteredOn("array"::equals).hasSize(1);
    assertThat(shape).startsWith("isActive", "branch", "array", "enterMethod");
  }

  @Test
  void aMethodWithAnErrorNarrationStillBuildsOnlyTheValueArray() throws Exception {
    var internal = "ai/narrativetrace/agent/sample/AnnotatedService";
    var transformed = ClassTransformer.transform(originalBytes(internal), internal);

    var shape = CallSiteShape.of(transformed, "transfer");

    assertThat(shape).filteredOn("array"::equals).hasSize(1);
    assertThat(shape).contains("resolveErrorContext");
  }

  /** A class with one method, {@code String describe(String <name>)}, that returns its argument. */
  private static byte[] twinClass(String parameterName) {
    var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TWIN_INTERNAL, null, "java/lang/Object", null);
    emitDefaultConstructor(cw);
    var mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC, "describe", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
    mv.visitParameter(parameterName, 0);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void emitDefaultConstructor(ClassWriter cw) {
    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();
  }

  private static Object invokeTwin(String parameterName, String argument) throws Exception {
    var transformed = ClassTransformer.transform(twinClass(parameterName), TWIN_INTERNAL);
    var loader =
        new ByteArrayClassLoader(
            CallSiteConstantsTest.class.getClassLoader(), transformed, TWIN_QUALIFIED);
    var clazz = loader.loadClass(TWIN_QUALIFIED);
    var instance = clazz.getDeclaredConstructor().newInstance();
    return clazz.getMethod("describe", String.class).invoke(instance, argument);
  }

  private List<ParameterCapture> capturedParametersOfOneCall() {
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    return tree.roots().get(0).signature().parameters();
  }

  @Test
  void twoSameNamedClassesInDifferentLoadersKeepTheirOwnParameterNames() throws Exception {
    assertThat(invokeTwin("alpha", "A")).isEqualTo("A");
    var first = capturedParametersOfOneCall();
    context.reset();

    assertThat(invokeTwin("beta", "B")).isEqualTo("B");
    var second = capturedParametersOfOneCall();

    assertThat(first).extracting(ParameterCapture::name).containsExactly("alpha");
    assertThat(second).extracting(ParameterCapture::name).containsExactly("beta");
  }

  @Test
  void theFirstClassKeepsItsNamesAfterTheSecondIsInstrumented() throws Exception {
    var alpha = ClassTransformer.transform(twinClass("alpha"), TWIN_INTERNAL);
    ClassTransformer.transform(twinClass("beta"), TWIN_INTERNAL);
    var loader = new ByteArrayClassLoader(getClass().getClassLoader(), alpha, TWIN_QUALIFIED);
    var clazz = loader.loadClass(TWIN_QUALIFIED);
    clazz
        .getMethod("describe", String.class)
        .invoke(clazz.getDeclaredConstructor().newInstance(), "A");

    assertThat(capturedParametersOfOneCall())
        .extracting(ParameterCapture::name)
        .containsExactly("alpha");
  }

  @Test
  void anIdThisRuntimeNeverMintedIsNotATraceAndNotAThrow() {
    assertThat(AgentRuntime.enterMethod(Integer.MAX_VALUE, new Object[] {"x"}, null)).isNull();
    assertThat(AgentRuntime.enterMethod(-1, new Object[0], null)).isNull();
    assertThat(
            AgentRuntime.resolveErrorContext(
                new IllegalStateException("no"), Integer.MAX_VALUE, new Object[0]))
        .isNull();
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void registeredConstantsAreTheOnesTheCallSiteReadsBack() {
    int id =
        AgentRuntime.registerMethod(
            AgentMethodMetadata.bare("com.acme.billing.OrderService", "place", "()I"));
    var metadata = AgentRuntime.methodMetadata(id);

    assertThat(metadata.simpleClassName()).isEqualTo("OrderService");
    assertThat(metadata.packageName()).isEqualTo("com.acme.billing");
    assertThat(metadata.returnType()).isEqualTo("int");
    assertThat(metadata.parameterNames()).isEmpty();
  }
}
