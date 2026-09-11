/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Resolves the on-disk layout for test trace artifacts.
 *
 * <p>INTENT: Use this instead of hand-assembling paths so all test integrations write traces into a
 * consistent directory structure.
 *
 * <p><b>@edgeCase</b> Both halves of the path are sanitized, and they were not always. The method
 * name has always been slugged; the class name reached {@code Path.resolve} verbatim, so a name
 * carrying a separator or a control character raised {@code InvalidPathException} — or, with a
 * separator, wrote outside the directory it was given. {@code TraceTestSupport.writeTraceFile} is
 * public API and its callers include integrations that do not derive the name from {@code
 * Class.getName()}, so "a Java class name cannot contain a slash" was never the whole question. The
 * directory rule is deliberately narrower than the file rule: it replaces only what a path cannot
 * carry, so {@code OrderServiceTest} still resolves to {@code OrderServiceTest} and no existing
 * artifact — or approved baseline beside it — moves.
 */
public final class OutputDirectoryResolver {

  /**
   * The longest a single path element may be, in <em>bytes</em>.
   *
   * <p>255 is what ext4, XFS, APFS and NTFS all allow, and the number is bytes rather than
   * characters on every filesystem this library writes to except NTFS — which counts UTF-16 units
   * and is therefore never the tighter of the two for the names seen here. Counting characters
   * would pass a 200-character CJK name and then fail the write at 600 bytes.
   */
  private static final int MAX_COMPONENT_BYTES = 255;

  /**
   * Bytes held back from a file slug for the suffix a writer appends to it.
   *
   * <p>The longest in the product is {@code .incomplete.nt} at 14; {@code .approved.nt} and {@code
   * .received.nt} are 12, {@code .json} 5, {@code .md} 3. 16 leaves room for one more without this
   * constant having to change.
   */
  private static final int SUFFIX_RESERVE_BYTES = 16;

  /**
   * Bytes an invocation label may occupy inside an artifact name.
   *
   * <p>A display name is prose — {@code @ParameterizedTest(name = ...)} interpolates arguments into
   * it — so it is bounded before the method slug is, and the index it follows is never the part
   * that gets truncated. 60 leaves a long name readable while keeping the whole element far below
   * the component limit.
   */
  private static final int MAX_LABEL_BYTES = 60;

  /**
   * Separates a method slug from its invocation discriminator.
   *
   * <p>The slug alphabet is {@code [a-z0-9_]}, so a hyphen can never appear inside either part: an
   * ordinary method's artifact can never collide with an invocation's, and a reader (or a manifest
   * consumer) can split the name back into method, index and label.
   */
  private static final String INVOCATION_SEPARATOR = "-";

  private final Path baseDir;

  public OutputDirectoryResolver(Path baseDir) {
    this.baseDir = baseDir;
  }

  public Path traceDirectory(String testClassName) {
    return classDirectory(baseDir.resolve("traces"), testClassName);
  }

  /**
   * The per-class directory of any artifact tree, under one sanitizing rule.
   *
   * <p>INTENT: {@code traces}, {@code diagrams}, {@code structural} and the approved-baseline tree
   * all key by test class, and each of them used to slice the simple name out itself. Three of the
   * four then passed it to {@code Path.resolve} verbatim, so a name carrying a separator wrote
   * outside the directory the caller gave — and a name whose last dot was followed by a slash
   * resolved to an <em>absolute</em> path, which {@code Path.resolve} takes as the whole answer.
   * One rule, in one place, is what keeps the four trees from drifting apart again.
   *
   * @param root the tree the artifact belongs to
   * @param testClassName qualified or simple; never trusted to be either
   */
  static Path classDirectory(Path root, String testClassName) {
    return root.resolve(toDirectorySlug(simpleNameOf(testClassName)));
  }

  /** The last dot-separated segment, which is a class name only when the caller passed one. */
  private static String simpleNameOf(String testClassName) {
    return testClassName.contains(".")
        ? testClassName.substring(testClassName.lastIndexOf('.') + 1)
        : testClassName;
  }

  public Path traceFile(String testClassName, String testMethodName) {
    return traceFile(ArtifactIdentity.ofMethod(testClassName, testMethodName));
  }

  /** The trace file of one invocation — the same layout, keyed by the full artifact identity. */
  public Path traceFile(ArtifactIdentity identity) {
    return traceArtifact(identity, ".md");
  }

  /**
   * A per-test artifact in the {@code traces} tree: the rendered narrative in whichever format was
   * chosen, and the JSON siblings written beside it.
   *
   * @param suffix the whole suffix including its dot, e.g. {@code .txt}, {@code .canonical.json}
   */
  public Path traceArtifact(ArtifactIdentity identity, String suffix) {
    return traceDirectory(identity.testClassName()).resolve(identity.fileSlug() + suffix);
  }

  /** The Mermaid diagram of one invocation, in the {@code diagrams} tree. */
  public Path diagramFile(ArtifactIdentity identity) {
    return classDirectory(baseDir.resolve("diagrams"), identity.testClassName())
        .resolve(identity.fileSlug() + ".mmd");
  }

  /** The last-green structural artifact of one invocation, in the {@code structural} tree. */
  public Path structuralFile(ArtifactIdentity identity) {
    return classDirectory(baseDir.resolve("structural"), identity.testClassName())
        .resolve(identity.fileSlug() + ".nt");
  }

  public Path baseDir() {
    return baseDir;
  }

  /**
   * Everything a path cannot carry, replaced: separators (which would write outside the directory)
   * and control characters (which raise {@code InvalidPathException}). Nothing else — a class name
   * is already a Java identifier and must keep its own spelling, unicode letters included.
   *
   * @return the safe directory segment; {@code unnamed} when nothing legible survives
   */
  static String toDirectorySlug(String simpleName) {
    var sb = new StringBuilder(simpleName.length());
    var index = 0;
    while (index < simpleName.length()) {
      index = appendPathSafe(sb, simpleName, index);
    }
    var slug = sb.toString();
    var named = slug.isEmpty() || slug.chars().allMatch(c -> c == '.') ? "unnamed" : slug;
    return capped(named, MAX_COMPONENT_BYTES);
  }

  /**
   * Appends one character, or one well-formed surrogate pair, replacing what a path cannot carry.
   *
   * @return the index to read from next
   */
  private static int appendPathSafe(StringBuilder sb, String name, int index) {
    char c = name.charAt(index);
    if (Character.isHighSurrogate(c)
        && index + 1 < name.length()
        && Character.isLowSurrogate(name.charAt(index + 1))) {
      sb.append(c).append(name.charAt(index + 1));
      return index + 2;
    }
    sb.append(isPathSafe(c) ? c : '_');
    return index + 1;
  }

  /**
   * A lone surrogate is refused for the same reason a control character is: it encodes to no bytes,
   * so {@code Path.resolve} raises {@code InvalidPathException} before the file is ever written.
   */
  private static boolean isPathSafe(char c) {
    return !Character.isISOControl(c)
        && !Character.isSurrogate(c)
        && c != '/'
        && c != '\\'
        && c != File.separatorChar;
  }

  /**
   * One slug rule for every per-test artifact: {@code customerPlacesOrder → customer_places_order}.
   */
  static String toFileSlug(String methodName) {
    return capped(rawFileSlug(methodName), MAX_COMPONENT_BYTES - SUFFIX_RESERVE_BYTES);
  }

  /**
   * The artifact base name for one invocation of a test method — the scheme {@link
   * ArtifactIdentity} documents in full, and the one place it is computed.
   *
   * <p>An index of zero means "this method runs once", which is every ordinary test, and returns
   * exactly what {@link #toFileSlug(String)} always returned: no existing artifact — or approved
   * baseline beside it — moves. Otherwise the discriminator is appended and the <em>method</em>
   * half absorbs any shortening, so the index a reader navigates by is never the part truncated
   * away.
   *
   * @param invocationIndex 1-based invocation number, or {@code 0} for a method that runs once
   * @param invocationLabel the invocation's display name; may be blank
   */
  static String toFileSlug(String methodName, int invocationIndex, String invocationLabel) {
    if (invocationIndex <= 0) {
      return toFileSlug(methodName);
    }
    var tail = invocationTail(invocationIndex, invocationLabel);
    var budget = MAX_COMPONENT_BYTES - SUFFIX_RESERVE_BYTES - utf8Length(tail);
    return capped(rawFileSlug(methodName), budget) + tail;
  }

  /** {@code -002-find_tent}: the index a reader navigates by, then the label they recognize. */
  private static String invocationTail(int invocationIndex, String invocationLabel) {
    var index = INVOCATION_SEPARATOR + String.format("%03d", invocationIndex);
    var label = labelSlug(invocationLabel);
    return label.isEmpty() ? index : index + INVOCATION_SEPARATOR + label;
  }

  /**
   * A display name reduced to a readable name fragment: the shared slug rule, then runs of {@code
   * _} collapsed and the ends trimmed, so JUnit's default {@code [1] KAYAK} reads as {@code
   * 1_kayak} rather than {@code _1__kayak}. A label that slugs to nothing is dropped entirely — the
   * index alone still names the invocation.
   *
   * <p><b>@llmNote</b> Never null: {@link ArtifactIdentity} normalizes an absent label to the empty
   * string before it reaches here, so absence is handled in exactly one place.
   */
  private static String labelSlug(String invocationLabel) {
    var collapsed = rawFileSlug(invocationLabel).replaceAll("_+", "_");
    var trimmed = collapsed.replaceAll("^_+", "").replaceAll("_+$", "");
    return capped(trimmed, MAX_LABEL_BYTES);
  }

  /** The uncapped slug: camel-case split, lowercased, everything outside the alphabet replaced. */
  private static String rawFileSlug(String name) {
    var slug = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    return slug.replaceAll("[^a-z0-9_]", "_");
  }

  /**
   * The slug, shortened to fit a path element when it does not.
   *
   * <p>INTENT: A name longer than the filesystem allows made the writers throw {@code
   * FileSystemException: File name too long} — an observability failure becoming an application
   * failure, which this library does not do. A name is data here, not an identifier: {@code
   * TraceTestSupport} is public API and its callers include integrations that pass a scenario name
   * or an HTTP route rather than {@code Class.getName()}.
   *
   * <p>The truncated form keeps eight hex characters of the full slug's hash, because truncation
   * alone is a silent overwrite: two long names sharing a prefix would land on one artifact, and
   * one scenario's approved baseline would then judge another's trace. {@link String#hashCode} is
   * specified, so the same name maps to the same artifact on every JVM and every run — the
   * disambiguator has to be stable, and it is not a security boundary.
   *
   * <p><b>@edgeCase</b> Nothing under the limit is touched, so no existing artifact — or approved
   * baseline beside it — moves.
   */
  private static String capped(String slug, int maxBytes) {
    if (utf8Length(slug) <= maxBytes) {
      return slug;
    }
    var suffix = "_" + String.format("%08x", slug.hashCode());
    return truncateToBytes(slug, maxBytes - suffix.length()) + suffix;
  }

  private static int utf8Length(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  /**
   * The longest prefix of {@code value} that encodes to at most {@code maxBytes}, cut on a
   * character boundary so a surrogate pair is never split in half.
   */
  private static String truncateToBytes(String value, int maxBytes) {
    var bytes = 0;
    var index = 0;
    while (index < value.length()) {
      var codePoint = value.codePointAt(index);
      var width = utf8Width(codePoint);
      if (bytes + width > maxBytes) {
        break;
      }
      bytes += width;
      index += Character.charCount(codePoint);
    }
    return value.substring(0, index);
  }

  /**
   * UTF-8 bytes one code point costs. A lone surrogate reaches the three-byte branch and actually
   * encodes to one ({@code '?'}), so the count is an over-estimate there — which shortens the name
   * rather than overflowing the element, the direction an estimate here has to err in.
   */
  private static int utf8Width(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    return codePoint < 0x10000 ? 3 : 4;
  }
}
