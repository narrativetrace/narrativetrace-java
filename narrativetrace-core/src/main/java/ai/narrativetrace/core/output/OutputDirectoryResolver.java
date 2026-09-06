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
    return traceDirectory(testClassName).resolve(toFileSlug(testMethodName) + ".md");
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
    var slug = methodName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    return capped(slug.replaceAll("[^a-z0-9_]", "_"), MAX_COMPONENT_BYTES - SUFFIX_RESERVE_BYTES);
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
