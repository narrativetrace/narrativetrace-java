/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.RedactionCase;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Replays the hostile redaction corpus through the CAPTURE path, not the renderer.
 *
 * <p>INTENT: The corpus was already curated, multilingual, and carried its own false-positive half
 * — and it had never once entered through the door an application uses. Every consumer of {@code
 * redaction.json} drove {@link ai.narrativetrace.core.render.ValueRenderer} directly; no test in
 * this module referenced a proxy or a narrative context at all. So when the name deny-list turned
 * out never to be consulted for a method PARAMETER, the corpus could not have noticed: it was
 * testing a layer below the broken one.
 *
 * <p><b>@llmNote</b> {@code RedactionCase.payload()} explains why, and says so plainly: "a {@code
 * Map} is the vehicle for name cases because a record component has to be a compile-time identifier
 * and these names are data". That pragmatic choice is the whole reason the gap survived. This class
 * removes the constraint by synthesising an interface per case with ASM, so a corpus name becomes a
 * real parameter name in a real class file — which is the only way the JVM will report it, and
 * therefore the only way the capture path can be asked about it.
 *
 * <p><b>@edgeCase</b> The assertion runs against the captured {@link
 * ai.narrativetrace.api.event.ParameterCapture} as well as the rendered text. Renderer-level
 * assertions are what passed all year: the event also reaches the audit emitter, the buffered
 * consumer and any listener attached through the pipeline SPI, so a secret that is merely masked on
 * its way out of a renderer has already travelled.
 */
class CapturePathRedactionConformanceTest {

  /** An innocuous name for value cases — the point is that the SHAPE is what gets caught. */
  private static final String INNOCUOUS = "data";

  private static Stream<RedactionCase> corpus() {
    return HostileCorpus.redactions().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  @DisplayName("every corpus row holds when replayed through a real traced call")
  void corpusHoldsAtCapture(RedactionCase testCase) {
    var parameterName = testCase.isName() ? testCase.name() : INNOCUOUS;

    // A corpus name that cannot be a Java identifier cannot be a parameter, so it cannot be
    // exercised on this path at all. Skipping loudly beats a green test that ran nothing.
    assumeThat(isUsableAsIdentifier(parameterName))
        .as(
            "corpus row '%s' names '%s', which is not a legal Java identifier",
            testCase.id(), parameterName)
        .isTrue();

    var context = new ThreadLocalNarrativeContext();
    var traced = tracedServiceWithParameter(parameterName, context);

    invoke(traced, testCase.secret());

    var tree = context.captureTrace();
    var secret = testCase.secret();

    var capturedParameters =
        tree.roots().stream().flatMap(node -> node.signature().parameters().stream()).toList();
    var capturedValues = capturedParameters.stream().map(ParameterCapture::renderedValue).toList();
    var capturedFlags = capturedParameters.stream().map(ParameterCapture::redacted).toList();

    var rendered =
        List.of(
            new MarkdownRenderer().render(tree),
            new ProseRenderer().render(tree),
            new IndentedTextRenderer().render(tree));

    if (testCase.expectsRedaction()) {
      assertThat(capturedValues)
          .as(
              "[%s] %s — the secret entered the EVENT, so a renderer cannot save it",
              testCase.id(), testCase.description())
          .noneMatch(value -> value.contains(secret));
      assertThat(rendered)
          .as("[%s] %s — the secret reached rendered output", testCase.id(), testCase.description())
          .noneMatch(output -> output.contains(secret));
      // Redaction oracle contract clause 3: BOTH axes — a value caught by shape must flag exactly
      // as a name caught by the deny-list. A masked value whose flag reads false is a metadata lie:
      // the audit emitter, the buffered consumer and any SPI listener all branch on this flag, none
      // of them a renderer.
      assertThat(capturedFlags)
          .as(
              "[%s] %s — the value was withheld but ParameterCapture.redacted() is false",
              testCase.id(), testCase.description())
          .allMatch(Boolean::booleanValue);
    } else {
      assertThat(rendered)
          .as(
              "[%s] %s — over-redaction: this must stay visible, or teams switch the default off",
              testCase.id(), testCase.description())
          .allMatch(output -> output.contains(secret));
      // Redaction oracle contract clause 6: over-flagging is a defect too — it makes the redacted
      // list untrue in the other direction and trains readers to distrust it.
      assertThat(capturedFlags)
          .as(
              "[%s] %s — over-flagging: ParameterCapture.redacted() is true though the value must"
                  + " stay visible",
              testCase.id(), testCase.description())
          .noneMatch(Boolean::booleanValue);
    }
  }

  /**
   * Whether every character of a corpus name can appear in a Java identifier.
   *
   * <p>Accented and CJK names pass — {@code contraseña} and {@code 密码} are legal identifiers, and
   * so are the decomposed spellings, because combining marks are identifier parts.
   */
  private static boolean isUsableAsIdentifier(String name) {
    if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.codePointAt(0))) {
      return false;
    }
    return name.codePoints().allMatch(Character::isJavaIdentifierPart);
  }

  /**
   * Builds, loads and proxies an interface whose single parameter carries {@code parameterName}.
   */
  private static Object tracedServiceWithParameter(
      String parameterName, ThreadLocalNarrativeContext context) {
    return traceAs(SyntheticService.define(parameterName), context);
  }

  /**
   * Captures the generated interface's type so {@code trace(T, Class<T>, …)} can bind.
   *
   * <p>The class is only known as {@code Class<?>} at the call site, because it did not exist when
   * this file was compiled — which is rather the point of the exercise.
   */
  private static <T> Object traceAs(Class<T> iface, ThreadLocalNarrativeContext context) {
    var target =
        Proxy.newProxyInstance(
            iface.getClassLoader(), new Class<?>[] {iface}, (proxy, method, args) -> "ok");
    return NarrativeTraceProxy.trace(iface.cast(target), iface, context);
  }

  private static void invoke(Object traced, String argument) {
    try {
      traced.getClass().getMethod("handle", String.class).invoke(traced, argument);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("could not invoke the synthesised traced method", e);
    }
  }

  /**
   * Generates {@code interface Svc { String handle(String <name>); }} with a real {@code
   * MethodParameters} attribute.
   *
   * <p><b>@llmNote</b> The attribute is the entire point. Without it the JVM answers {@code arg0},
   * the deny-list has nothing to match, and this test would pass while proving nothing — which is
   * precisely the failure mode the corpus is here to detect.
   */
  private static final class SyntheticService {

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
        new java.util.concurrent.atomic.AtomicInteger();

    static Class<?> define(String parameterName) {
      var simpleName = "Svc$" + COUNTER.incrementAndGet();
      var internalName = "ai/narrativetrace/security/generated/" + simpleName;
      var writer = new ClassWriter(0);
      writer.visit(
          Opcodes.V17,
          Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
          internalName,
          null,
          "java/lang/Object",
          null);
      MethodVisitor method =
          writer.visitMethod(
              Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
              "handle",
              "(Ljava/lang/String;)Ljava/lang/String;",
              null,
              null);
      method.visitParameter(parameterName, 0);
      method.visitEnd();
      writer.visitEnd();
      return new SingleClassLoader(internalName.replace('/', '.'), writer.toByteArray())
          .defineGenerated();
    }

    private SyntheticService() {}
  }

  /** Defines exactly one generated interface, parented to the test's own loader. */
  private static final class SingleClassLoader extends ClassLoader {

    private final String qualifiedName;
    private final byte[] bytes;

    SingleClassLoader(String qualifiedName, byte[] bytes) {
      super(CapturePathRedactionConformanceTest.class.getClassLoader());
      this.qualifiedName = qualifiedName;
      this.bytes = bytes.clone();
    }

    Class<?> defineGenerated() {
      return defineClass(qualifiedName, bytes, 0, bytes.length);
    }
  }
}
