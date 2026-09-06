/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputDirectoryResolverTest {

  /** The per-element limit the resolver caps to; ext4, APFS and NTFS all stop at 255. */
  private static final int MAX_COMPONENT_BYTES = 255;

  @Test
  void computesTraceDirectoryFromTestClassName() {
    var resolver = new OutputDirectoryResolver(Path.of("target/narrativetrace"));

    var dir = resolver.traceDirectory("ai.narrativetrace.examples.ecommerce.OrderServiceTest");

    assertThat(dir).isEqualTo(Path.of("target/narrativetrace/traces/OrderServiceTest"));
  }

  @Test
  void computesTraceFilePathWithSluggedMethodName() {
    var resolver = new OutputDirectoryResolver(Path.of("target/narrativetrace"));

    var file =
        resolver.traceFile(
            "ai.narrativetrace.examples.ecommerce.OrderServiceTest",
            "customerPlacesOrderSuccessfully");

    assertThat(file)
        .isEqualTo(
            Path.of(
                "target/narrativetrace/traces/OrderServiceTest/customer_places_order_successfully.md"));
  }

  @Test
  void preservesUnderscoreMethodNames() {
    var resolver = new OutputDirectoryResolver(Path.of("target/narrativetrace"));

    var file = resolver.traceFile("OrderServiceTest", "customer_places_order");

    assertThat(file)
        .isEqualTo(
            Path.of("target/narrativetrace/traces/OrderServiceTest/customer_places_order.md"));
  }

  @Test
  void computesTraceDirectoryFromSimpleClassName() {
    var resolver = new OutputDirectoryResolver(Path.of("build/narrativetrace"));

    var dir = resolver.traceDirectory("OrderServiceTest");

    assertThat(dir).isEqualTo(Path.of("build/narrativetrace/traces/OrderServiceTest"));
  }

  @Test
  void parameterizedTestNameWithBracketsIsSanitized() {
    var resolver = new OutputDirectoryResolver(Path.of("build/narrativetrace"));

    var file = resolver.traceFile("OrderTest", "placesOrder[0]");

    assertThat(file.getFileName().toString()).doesNotContain("[").doesNotContain("]");
    assertThat(file.getFileName().toString()).isEqualTo("places_order_0_.md");
  }

  @Test
  void parameterizedTestNameWithParensAndColonIsSanitized() {
    var resolver = new OutputDirectoryResolver(Path.of("build/narrativetrace"));

    var file = resolver.traceFile("OrderTest", "placesOrder(orderId: 42)");

    var name = file.getFileName().toString();
    assertThat(name).doesNotContain("(").doesNotContain(")").doesNotContain(":");
  }

  @Test
  void baseDirReturnsConfiguredPath() {
    var resolver = new OutputDirectoryResolver(Path.of("target/narrativetrace"));

    assertThat(resolver.baseDir()).isEqualTo(Path.of("target/narrativetrace"));
  }

  /**
   * Regression: the method name has always been slugged and the class name never was, so it reached
   * {@code Path.resolve} verbatim. A separator wrote outside the directory the caller gave; a
   * control character raised {@code InvalidPathException} out of the writer. {@code
   * TraceTestSupport.writeTraceFile} is public API, so "a Java class name cannot contain a slash"
   * was never the whole question. Found by the security suite's hostile corpus.
   */
  @Test
  void aClassNameCarryingASeparatorCannotEscapeTheBaseDirectory() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("..\u002f..\u002fetc", "passwd");

    assertThat(file.normalize().toString()).startsWith("/tmp/base/traces/");
  }

  @Test
  void aClassNameCarryingAControlCharacterResolvesInsteadOfThrowing() {
    var base = Path.of("/tmp/base");

    var file =
        new OutputDirectoryResolver(base).traceFile("com.example.A" + (char) 0 + "B", "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/A_B/runs.md");
  }

  @Test
  void aClassNameThatSanitizesAwayEntirelyGetsANameRatherThanNone() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("com.example.", "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/unnamed/runs.md");
  }

  @Test
  void aClassNameCarryingALoneSurrogateResolvesInsteadOfThrowing() {
    var base = Path.of("/tmp/base");

    var file =
        new OutputDirectoryResolver(base).traceFile("com.example.A" + (char) 0xd800 + "B", "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/A_B/runs.md");
  }

  @Test
  void aClassNameCarryingAWellFormedPairKeepsIt() {
    var base = Path.of("/tmp/base");
    var monkey = "" + (char) 0xd83d + (char) 0xde48;

    var file = new OutputDirectoryResolver(base).traceFile("com.example.A" + monkey, "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/A" + monkey + "/runs.md");
  }

  /** The point of the narrower directory rule: no existing artifact moves. */
  @Test
  void anOrdinaryClassNameKeepsItsOwnSpelling() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("com.example.OrderServiceTest", "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/OrderServiceTest/runs.md");
  }

  @Test
  void aClassNameWithUnicodeLettersKeepsThemToo() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("com.example.\u00dcberTest", "runs");

    assertThat(file.toString()).isEqualTo("/tmp/base/traces/\u00dcberTest/runs.md");
  }

  /**
   * Regression: a name longer than a path element made every writer throw {@code
   * FileSystemException: File name too long}, which is an observability failure becoming an
   * application failure. Found by the security suite's hostile corpus.
   */
  @Test
  void anOverLongClassNameIsShortenedToFitAPathElement() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("a".repeat(300), "runs");

    assertThat(byteLengthOf(file.getParent().getFileName())).isEqualTo(MAX_COMPONENT_BYTES);
  }

  /** The file half has to leave room for the longest suffix a writer appends. */
  @Test
  void anOverLongMethodNameLeavesRoomForTheLongestArtifactSuffix() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("Test", "a".repeat(300));

    assertThat(file.getFileName().toString()).endsWith(".md");
    assertThat(byteLengthOf(file.getFileName()) + ".incomplete.nt".length() - ".md".length())
        .isLessThanOrEqualTo(MAX_COMPONENT_BYTES);
  }

  /**
   * A name that is short in characters and long in bytes: 200 three-byte characters are 600 bytes,
   * and a cap that counted characters would pass this and then fail the write.
   */
  @Test
  void aMultibyteClassNameIsMeasuredInBytesRatherThanCharacters() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("\u4e2d".repeat(200), "runs");

    assertThat(byteLengthOf(file.getParent().getFileName()))
        .isLessThanOrEqualTo(MAX_COMPONENT_BYTES);
  }

  /** Truncation must not cut a surrogate pair in half and leave an unencodable path element. */
  @Test
  void anOverLongNameOfSurrogatePairsIsCutOnACharacterBoundary() {
    var base = Path.of("/tmp/base");
    var monkey = "" + (char) 0xd83d + (char) 0xde48;

    var file = new OutputDirectoryResolver(base).traceFile(monkey.repeat(200), "runs");

    var directory = file.getParent().getFileName().toString();
    assertThat(directory.codePoints().noneMatch(cp -> cp >= 0xd800 && cp <= 0xdfff))
        .as("half a surrogate pair encodes to no bytes, so the path would not be writable")
        .isTrue();
    assertThat(byteLengthOf(file.getParent().getFileName()))
        .isLessThanOrEqualTo(MAX_COMPONENT_BYTES);
  }

  /**
   * Truncation alone is a silent overwrite: two long names sharing a prefix would land on one
   * artifact, so one scenario's approved baseline would judge another scenario's trace.
   */
  @Test
  void twoOverLongNamesDifferingOnlyPastTheLimitStayApart() {
    var resolver = new OutputDirectoryResolver(Path.of("/tmp/base"));
    var prefix = "a".repeat(300);

    var first = resolver.traceFile(prefix + "one", "runs");
    var second = resolver.traceFile(prefix + "two", "runs");

    assertThat(first).isNotEqualTo(second);
  }

  /** The shortening is a function of the name, so an artifact keeps its place between runs. */
  @Test
  void shorteningTheSameNameTwiceGivesTheSamePath() {
    var resolver = new OutputDirectoryResolver(Path.of("/tmp/base"));

    assertThat(resolver.traceFile("b".repeat(400), "c".repeat(400)))
        .isEqualTo(resolver.traceFile("b".repeat(400), "c".repeat(400)));
  }

  /** A name that already fits is untouched, so no existing artifact moves. */
  @Test
  void aNameThatFitsIsNotShortened() {
    var base = Path.of("/tmp/base");

    var file = new OutputDirectoryResolver(base).traceFile("a".repeat(255), "b".repeat(239));

    assertThat(file.getParent().getFileName().toString()).isEqualTo("a".repeat(255));
    assertThat(file.getFileName().toString()).isEqualTo("b".repeat(239) + ".md");
  }

  /**
   * The rule the three other artifact trees now share: whatever the name, the directory is one
   * element under the root it was given.
   */
  @Test
  void aClassDirectoryIsAlwaysOneElementUnderTheRootItWasGiven() {
    var root = Path.of("/tmp/base/diagrams");

    for (var hostile :
        java.util.List.of(
            "..\u002f..\u002fetc\u002fpasswd", "a\u002fb", "...", "", "\u4e2d".repeat(200))) {
      var directory = OutputDirectoryResolver.classDirectory(root, hostile);

      assertThat(directory.normalize()).as("name '%s'", hostile).startsWithRaw(root);
      assertThat(root.relativize(directory).getNameCount())
          .as("name '%s' must produce exactly one directory element", hostile)
          .isOne();
    }
  }

  /**
   * Every width the UTF-8 counter distinguishes, taken at its boundary: the last two-byte code
   * point and the first three-byte one, the last three-byte one and the first four-byte one.
   *
   * <p><b>@llmNote</b> The assertion is a range, not an upper bound, on purpose. An upper bound
   * alone passes for a counter that over-estimates every character and truncates to nothing; the
   * lower bound (three bytes of slack, which is the most a four-byte character can waste) says the
   * element is also as long as it is allowed to be. U+0080 itself cannot be tested here — it is a
   * C1 control, so the path rule replaces it with an underscore before the counter sees it.
   */
  @ParameterizedTest
  @ValueSource(ints = {0x00e9, 0x07ff, 0x0800, 0xffff, 0x10000})
  void anOverLongNameOfAnyCharacterWidthFillsItsPathElementWithoutOverflowing(int codePoint) {
    var base = Path.of("/tmp/base");
    var name = new String(Character.toChars(codePoint)).repeat(300);

    var file = new OutputDirectoryResolver(base).traceFile(name, "runs");

    assertThat(byteLengthOf(file.getParent().getFileName()))
        .as("U+%04X repeated", codePoint)
        .isBetween(MAX_COMPONENT_BYTES - 3, MAX_COMPONENT_BYTES);
  }

  private static int byteLengthOf(Path component) {
    return component.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }
}
