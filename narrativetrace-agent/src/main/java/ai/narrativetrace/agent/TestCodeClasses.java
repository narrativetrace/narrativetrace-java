/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Whether a class is test code: list 3 of the agent's exclusion rules, on by default.
 *
 * <p>INTENT: A test method is the caller of the code under test, not part of it. Instrumenting it
 * makes every trace's root the test method and records test helpers as calls, while the proxy route
 * records only production calls. So test code is left alone unless {@code tests=trace} asks for it.
 *
 * <p>Locations are matched against the END of the path: a directory-loaded class's code source is
 * the output root itself, so a project that merely lives under a directory named {@code
 * test-classes} or {@code out/test} is still production.
 *
 * <p>The decision is origin first, content as the fallback. The code source's location is
 * conclusive whenever it is recognised: a build tool's test output directory (Maven {@code
 * test-classes}, Gradle {@code classes/<language>/<sourceSet>} for a test source set, IntelliJ
 * {@code out/test}) or a {@code -tests.jar}/{@code -test-fixtures.jar} is test code; Maven {@code
 * target/classes}, Gradle's {@code main} source set and IntelliJ {@code out/production} are
 * production, and for those the class file is never read (a production class carrying a test
 * annotation is still traced). Only when the location says nothing — no code source, a plain
 * library jar, an unrecognised directory — does the class file decide: a method annotated by JUnit
 * 5, JUnit 4 or TestNG, or a direct superclass {@code junit.framework.TestCase} or {@code
 * spock.lang.Specification}. A nested class follows its outermost class, so fixtures declared
 * inside a test class are left out even when they carry no annotation of their own.
 *
 * <p><b>@llmNote</b> The content rule can only misjudge a class of unknown origin, and a wrong
 * answer drops production code from traces silently — keep it narrow. It must never throw: a class
 * file that cannot be parsed is simply not test code, and the transformer's own failure path
 * reports it.
 *
 * <p><b>@threadSafety</b> Called from the class-loading path of every thread, concurrently; the
 * remembered names and the once-only report are concurrent structures. Loading order is the JVM's:
 * a nested class that arrives before its outer class is judged on its own.
 *
 * <p><b>@sideEffects</b> Prints one line to {@code stderr} the first time a class is left out,
 * never per class.
 */
final class TestCodeClasses {

  /** Where a class came from, as far as its code source location tells. */
  enum Origin {
    TEST,
    PRODUCTION,
    UNKNOWN
  }

  private static final String TEST_CLASSES_SEGMENT = "test-classes";
  private static final String CLASSES_SEGMENT = "classes";
  private static final String MAIN_SOURCE_SET = "main";
  private static final String TEST_SOURCE_SET = "test";
  private static final int SOURCE_SET_OFFSET = 2;

  /**
   * Annotation packages whose methods mark a class as test code (internal form), published in the
   * installation guide and asserted there by {@code TestCodeDocsTest} — never typed twice. JUnit
   * 4's annotations sit directly in {@code org.junit} and are matched separately by {@link
   * #JUNIT4_PACKAGE}.
   */
  static final List<String> TEST_ANNOTATION_PACKAGES =
      List.of("org/junit/jupiter/api/", "org/junit/jupiter/params/", "org/testng/annotations/");

  /** JUnit 4's annotations ({@code Test}, {@code Before}, ...) live directly in this package. */
  static final String JUNIT4_PACKAGE = "org/junit/";

  /** Direct superclasses that mark a class as test code (internal form), published likewise. */
  static final List<String> TEST_SUPERCLASSES =
      List.of("junit/framework/TestCase", "spock/lang/Specification");

  private final Set<String> knownTestClasses = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean reported = new AtomicBoolean();

  /**
   * @param className the internal name of a class that passed the package filters
   * @param domain the protection domain the JVM supplied, possibly {@code null}
   * @param classfileBytes the class file, read only when the origin is unknown
   * @return {@code true} when the class is test code, having reported the first one
   */
  boolean isTestCode(String className, ProtectionDomain domain, byte[] classfileBytes) {
    var reason = reasonForTestCode(className, domain, classfileBytes);
    if (reason == null) {
      return false;
    }
    knownTestClasses.add(className);
    reportOnce(className, reason);
    return true;
  }

  private String reasonForTestCode(
      String className, ProtectionDomain domain, byte[] classfileBytes) {
    var location = locationOf(domain);
    var origin = originOf(location);
    if (origin == Origin.TEST) {
      return "loaded from test output " + location;
    }
    if (origin == Origin.PRODUCTION) {
      return null;
    }
    if (nestedInKnownTestClass(className)) {
      return "nested in a test class";
    }
    return declaresTests(classfileBytes) ? "it declares test annotations" : null;
  }

  private boolean nestedInKnownTestClass(String className) {
    var firstDollar = className.indexOf('$');
    return firstDollar > 0 && knownTestClasses.contains(className.substring(0, firstDollar));
  }

  private static URL locationOf(ProtectionDomain domain) {
    if (domain == null) {
      return null;
    }
    CodeSource source = domain.getCodeSource();
    return source == null ? null : source.getLocation();
  }

  /** Origin of a code source location; {@code null} is {@link Origin#UNKNOWN}. */
  static Origin originOf(URL location) {
    return location == null ? Origin.UNKNOWN : originOfPath(location.getPath());
  }

  /** Origin of a location path; separators are normalised, so Windows paths work. */
  static Origin originOfPath(String path) {
    if (path == null) {
      return Origin.UNKNOWN;
    }
    var normalised = path.replace('\\', '/');
    var segments = segmentsOf(normalised);
    if (normalised.endsWith(".jar")) {
      return isTestJar(segments) ? Origin.TEST : Origin.UNKNOWN;
    }
    if (isTestDirectory(segments)) {
      return Origin.TEST;
    }
    return isProductionDirectory(segments) ? Origin.PRODUCTION : Origin.UNKNOWN;
  }

  private static String[] segmentsOf(String normalised) {
    return Arrays.stream(normalised.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }

  private static boolean isTestJar(String[] segments) {
    if (segments.length == 0) {
      return false;
    }
    var name = segments[segments.length - 1];
    return name.endsWith("-tests.jar") || name.endsWith("-test-fixtures.jar");
  }

  /**
   * Anchored at the end of the path: the location must BE an output root, not merely sit under one.
   */
  private static boolean isTestDirectory(String[] segments) {
    var last = segments.length - 1;
    if (last >= 0 && TEST_CLASSES_SEGMENT.equals(segments[last])) {
      return true;
    }
    if (endsWithGradleLayout(segments)) {
      return isTestSourceSet(segments[last]);
    }
    return endsWithIdeaLayout(segments, TEST_SOURCE_SET);
  }

  private static boolean isProductionDirectory(String[] segments) {
    var last = segments.length - 1;
    if (last >= 0 && CLASSES_SEGMENT.equals(segments[last])) {
      return true;
    }
    if (endsWithGradleLayout(segments) && MAIN_SOURCE_SET.equals(segments[last])) {
      return true;
    }
    return endsWithIdeaLayout(segments, "production");
  }

  /** The last three segments are {@code classes/<language>/<sourceSet>}. */
  private static boolean endsWithGradleLayout(String[] segments) {
    return segments.length >= SOURCE_SET_OFFSET + 1
        && CLASSES_SEGMENT.equals(segments[segments.length - SOURCE_SET_OFFSET - 1]);
  }

  /** The last three segments are {@code out/<kind>/<module>}. */
  private static boolean endsWithIdeaLayout(String[] segments, String kind) {
    return segments.length >= SOURCE_SET_OFFSET + 1
        && "out".equals(segments[segments.length - SOURCE_SET_OFFSET - 1])
        && kind.equals(segments[segments.length - SOURCE_SET_OFFSET]);
  }

  private static boolean isTestSourceSet(String sourceSet) {
    return TEST_SOURCE_SET.equals(sourceSet)
        || (sourceSet.length() > TEST_SOURCE_SET.length()
            && sourceSet.startsWith(TEST_SOURCE_SET)
            && Character.isUpperCase(sourceSet.charAt(TEST_SOURCE_SET.length())))
        || sourceSet.endsWith("Test")
        || sourceSet.endsWith("Tests");
  }

  /** Whether the class file itself says it is a test class; never throws. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // a class file ASM cannot read is not ours
  private static boolean declaresTests(byte[] classfileBytes) {
    if (classfileBytes == null) {
      return false;
    }
    try {
      var probe = new TestProbe();
      new ClassReader(classfileBytes)
          .accept(probe, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return probe.isTestClass;
    } catch (RuntimeException unreadable) { // NOPMD - the transformer's failure path reports it
      return false;
    }
  }

  private static boolean isTestAnnotation(String descriptor) {
    if (!descriptor.startsWith("L")) {
      return false;
    }
    var internalName = descriptor.substring(1);
    for (var annotationPackage : TEST_ANNOTATION_PACKAGES) {
      if (internalName.startsWith(annotationPackage)) {
        return true;
      }
    }
    return internalName.startsWith(JUNIT4_PACKAGE)
        && internalName.length() > JUNIT4_PACKAGE.length()
        && Character.isUpperCase(internalName.charAt(JUNIT4_PACKAGE.length()));
  }

  private void reportOnce(String className, String reason) {
    if (reported.compareAndSet(false, true)) {
      System.err.println( // NOPMD - the agent's only channel from inside a class transformer
          "narrativetrace-agent: not instrumenting "
              + className
              + " (test code: "
              + reason
              + "); test code loads uninstrumented, set tests=trace to instrument it too");
    }
  }

  /** Reads superclass and method annotations only. */
  private static final class TestProbe extends ClassVisitor {
    private boolean isTestClass;

    TestProbe() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version, int access, String name, String signature, String superName, String[] ifs) {
      if (superName != null && TEST_SUPERCLASSES.contains(superName)) {
        isTestClass = true;
      }
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
          if (isTestAnnotation(annotation)) {
            isTestClass = true;
          }
          return null;
        }
      };
    }
  }
}
