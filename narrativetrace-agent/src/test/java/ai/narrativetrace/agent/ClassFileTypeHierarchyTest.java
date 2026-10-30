/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

/**
 * The frame-merge answers ASM would get from loaded classes, read from class files instead.
 *
 * <p>Every expectation here is ASM's own: {@code ClassWriter.getCommonSuperClass} returns an
 * assignable side unchanged, {@code java/lang/Object} as soon as an interface is involved, and
 * otherwise the first superclass of the first type that the second is assignable to. The point of
 * this class is that the same answers arrive without a single class being loaded — see {@link
 * ClassFileTypeHierarchy} and {@code DefiningLoaderFrameComputationTest} for why (B-213).
 *
 * <p><b>@llmNote</b> Four mutants of {@link ClassFileTypeHierarchy} survive on purpose and are not
 * worth hunting again: the interface rule, the {@code ACC_INTERFACE} read behind it, the
 * null-stream guard and the invariant's {@code true} are all answer-equivalent to their fallbacks —
 * an interface's class file declares {@code Object} as its superclass, so the superclass walk
 * arrives at {@code Object} anyway; ASM's {@code ClassReader} turns a null stream into an {@code
 * IOException} this class would wrap identically; and an invariant that holds cannot be observed
 * failing. They are kept because each states a rule the next reader needs to see.
 */
class ClassFileTypeHierarchyTest {

  private static final String CALCULATOR = "ai/narrativetrace/agent/sample/Calculator";
  private static final String OBJECT = "java/lang/Object";

  private ClassFileTypeHierarchy hierarchy;

  @BeforeEach
  void seedWithARealTransformedClass() throws IOException {
    hierarchy = new ClassFileTypeHierarchy(classFiles(), readerFor(CALCULATOR));
  }

  @Test
  void aSupertypeOnEitherSideIsTheAnswer() {
    assertThat(hierarchy.commonSuperClass("java/lang/Number", "java/lang/Integer"))
        .isEqualTo("java/lang/Number");
    assertThat(hierarchy.commonSuperClass("java/lang/Integer", "java/lang/Number"))
        .isEqualTo("java/lang/Number");
  }

  @Test
  void anImplementedInterfaceIsASupertypeToo() {
    assertThat(hierarchy.commonSuperClass("java/util/List", "java/util/ArrayList"))
        .isEqualTo("java/util/List");
  }

  @Test
  void anImplementedInterfaceIsASupertypeOnTheSecondSideAsWell() {
    // Order matters here: the interface rule below would otherwise answer Object for this pair.
    assertThat(hierarchy.commonSuperClass("java/util/ArrayList", "java/util/List"))
        .isEqualTo("java/util/List");
  }

  @Test
  void theRootIsASupertypeOfATypeWhoseClassFileIsNotEvenThere() throws IOException {
    var nothing = new NoClassFilesClassLoader();
    var overNothing = new ClassFileTypeHierarchy(nothing, readerFor(CALCULATOR));

    assertThat(overNothing.commonSuperClass(OBJECT, "a/A")).isEqualTo(OBJECT);
  }

  @Test
  void twoSiblingsMergeToTheNearestSharedSuperclass() {
    assertThat(hierarchy.commonSuperClass("java/lang/Integer", "java/lang/Double"))
        .isEqualTo("java/lang/Number");
  }

  @Test
  void theSuperclassWalkSkipsOverASuperclassTheOtherSideIsNotAssignableTo() {
    // ArrayList -> AbstractList (HashSet is not one) -> AbstractCollection (it is).
    assertThat(hierarchy.commonSuperClass("java/util/ArrayList", "java/util/HashSet"))
        .isEqualTo("java/util/AbstractCollection");
  }

  @Test
  void anInterfaceOnEitherSideMergesToObjectRatherThanToASharedInterface() {
    assertThat(hierarchy.commonSuperClass("java/lang/Comparable", "java/lang/Number"))
        .isEqualTo(OBJECT);
    assertThat(hierarchy.commonSuperClass("java/lang/Number", "java/lang/Comparable"))
        .isEqualTo(OBJECT);
  }

  @Test
  void twoClassesSharingOnlyInterfacesMergeToObject() {
    // Both are Comparable and Serializable; ASM never answers with an interface here.
    assertThat(hierarchy.commonSuperClass("java/lang/String", "java/lang/Integer"))
        .isEqualTo(OBJECT);
  }

  @Test
  void twoDistinctArrayTypesMergeToObjectWithoutReadingAnyClassFile() {
    assertThat(hierarchy.commonSuperClass("[Ljava/lang/String;", "[Ljava/lang/Integer;"))
        .isEqualTo(OBJECT);
  }

  @Test
  void theClassBeingTransformedIsAnsweredFromItsOwnBytesAndNotFromItsClassFile()
      throws IOException {
    var hidden = new HidingClassLoader(CALCULATOR + ".class", classFiles());
    var seeded = new ClassFileTypeHierarchy(hidden, readerFor(CALCULATOR));

    assertThat(seeded.commonSuperClass(CALCULATOR, "java/lang/Integer")).isEqualTo(OBJECT);
  }

  @Test
  void aTypeWithNoReadableClassFileIsNotGuessedAt() throws IOException {
    var hidden = new HidingClassLoader(CALCULATOR + ".class", classFiles());
    var unseeded =
        new ClassFileTypeHierarchy(hidden, readerFor("ai/narrativetrace/agent/sample/LineService"));

    assertThatThrownBy(() -> unseeded.commonSuperClass(CALCULATOR, "java/lang/Integer"))
        .isInstanceOf(TypeNotPresentException.class)
        .hasMessageContaining(CALCULATOR);
  }

  @Test
  void aClassFileThatCannotBeReadIsNotGuessedAt() throws IOException {
    var broken = new BrokenStreamClassLoader();
    var overBrokenFiles = new ClassFileTypeHierarchy(broken, readerFor(CALCULATOR));

    assertThatThrownBy(() -> overBrokenFiles.commonSuperClass("a/A", "b/B"))
        .isInstanceOf(TypeNotPresentException.class);
  }

  @Test
  void aClassFileFromAFutureJavaVersionIsNotGuessedAt() throws IOException {
    var futureBytes = readAllBytes(CALCULATOR);
    futureBytes[7] = (byte) 0x7F; // the class file's major version, far beyond ASM's range
    var future = new FixedBytesClassLoader(futureBytes);
    var overFutureFiles = new ClassFileTypeHierarchy(future, readerFor(CALCULATOR));

    assertThatThrownBy(() -> overFutureFiles.commonSuperClass("a/A", "b/B"))
        .isInstanceOf(TypeNotPresentException.class);
  }

  @Test
  void aClassFileDeclaringNoSuperclassEndsTheWalkAtObjectInsteadOfRunningOffIt()
      throws IOException {
    // Only Object itself may declare no superclass; bytes that claim it for another name must
    // degrade, not walk past the top of the hierarchy.
    var rootless = new FixedBytesClassLoader(readAllBytes(OBJECT));
    var overRootlessFiles = new ClassFileTypeHierarchy(rootless, readerFor(CALCULATOR));

    assertThat(overRootlessFiles.commonSuperClass("a/A", "b/B")).isEqualTo(OBJECT);
  }

  @Test
  void aMissingClassFileSourceOrTransformedClassIsRefusedAtConstruction() throws IOException {
    var reader = readerFor(CALCULATOR);

    assertThatThrownBy(() -> new ClassFileTypeHierarchy(null, reader))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ClassFileTypeHierarchy(classFiles(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aMissingMergedTypeIsRefused() {
    assertThatThrownBy(() -> hierarchy.commonSuperClass(null, OBJECT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> hierarchy.commonSuperClass(OBJECT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theRootIsKnownBeforeAnythingIsAsked() {
    assertThat(hierarchy.invariant()).isTrue();
  }

  private ClassLoader classFiles() {
    return getClass().getClassLoader();
  }

  private ClassReader readerFor(String internalName) throws IOException {
    try (var classFile = classFiles().getResourceAsStream(internalName + ".class")) {
      return new ClassReader(classFile);
    }
  }

  private byte[] readAllBytes(String internalName) throws IOException {
    try (var classFile = classFiles().getResourceAsStream(internalName + ".class")) {
      return classFile.readAllBytes();
    }
  }

  /** Serves every class file but one — the shape of a class with no file of its own. */
  private static final class HidingClassLoader extends ClassLoader {

    private final String hidden;

    HidingClassLoader(String hidden, ClassLoader parent) {
      super(parent);
      this.hidden = hidden;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return hidden.equals(name) ? null : super.getResourceAsStream(name);
    }
  }

  /** A loader with no class files at all: every name is unresolvable. */
  private static final class NoClassFilesClassLoader extends ClassLoader {

    NoClassFilesClassLoader() {
      super(null);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return null;
    }
  }

  /** A loader whose class files exist but cannot be read — a closed jar, a dead network mount. */
  private static final class BrokenStreamClassLoader extends ClassLoader {

    BrokenStreamClassLoader() {
      super(null);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return new InputStream() {
        @Override
        public int read() throws IOException {
          throw new IOException("class file unreadable");
        }
      };
    }
  }

  /** A loader answering every name with the same bytes, whatever they happen to be. */
  private static final class FixedBytesClassLoader extends ClassLoader {

    private final byte[] bytes;

    FixedBytesClassLoader(byte[] bytes) {
      super(null);
      this.bytes = bytes;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return new ByteArrayInputStream(bytes);
    }
  }
}
