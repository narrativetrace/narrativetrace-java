/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

/**
 * Which artifact one traced test invocation owns.
 *
 * <p>INTENT: A test method used to be the whole answer to "which file does this trace go in", which
 * is wrong the moment the method runs more than once — a parameterized or repeated test. Every
 * invocation then wrote the same path and the last one won, so earlier evidence was unreachable
 * through the advertised files and one invocation's {@code .approved.nt} baseline judged another's
 * structure (2026-09-08 agent evaluation). This record is the identity every per-test artifact keys
 * by: the trace, the JSON export, the diagram, the structural artifact, and the committed approval
 * baseline beside them.
 *
 * <p><b>@llmNote</b> This is the family's master definition of the naming scheme — the other
 * runtimes mirror it byte for byte. The rule, in full:
 *
 * <ul>
 *   <li>An ordinary test method keeps its bare method slug — {@code customerPlacesOrder} → {@code
 *       customer_places_order}. Nothing that exists today moves.
 *   <li>An invocation appends {@code -<index>-<label>}: the 1-based invocation index zero-padded to
 *       three digits, then the invocation's display name through the same slug rule, with runs of
 *       {@code _} collapsed and the ends trimmed — {@code equipment_can_be_found-001-find_kayak}.
 *       The label is dropped when it slugs to nothing.
 *   <li>{@code -} is the separator precisely because the slug alphabet is {@code [a-z0-9_]} and can
 *       never produce one: an ordinary method can never collide with an invocation artifact, and
 *       the name parses back into its three parts.
 *   <li>Two invocations of one method always differ in the index, so display names that differ only
 *       in characters a path cannot carry ({@code find/TENT} versus {@code find TENT}) still land
 *       on different files. The index is what makes the scheme collision-proof; the label is what
 *       makes it readable.
 *   <li>Nothing here varies per process or per run: the index comes from the test engine's own
 *       invocation order and the label from the display name, so an approval baseline recorded on
 *       one machine matches the artifact written on the next. Where a name is too long for the
 *       filesystem, the shared cap appends eight hex characters of {@link String#hashCode} — the
 *       one hash in this scheme, chosen because it is *specified* rather than
 *       implementation-defined (a per-process hash would disqualify the whole scheme).
 * </ul>
 *
 * @param testClassName the test class, qualified or simple; never trusted to be either
 * @param methodName the test method's own name, shared by every invocation of it
 * @param invocationIndex 1-based invocation number, or {@code 0} for a method that runs once
 * @param invocationLabel the invocation's display name, {@code ""} when there is none
 */
public record ArtifactIdentity(
    String testClassName, String methodName, int invocationIndex, String invocationLabel) {

  /**
   * Rejects what cannot name a file; normalizes an absent label to the empty string.
   *
   * <p>Spelled out rather than compact because the label normalization is an assignment to the
   * parameter, which PMD's {@code UnusedAssignment} cannot see through in a compact constructor.
   */
  public ArtifactIdentity(
      String testClassName, String methodName, int invocationIndex, String invocationLabel) {
    if (testClassName == null) {
      throw new IllegalArgumentException("testClassName must not be null");
    }
    if (methodName == null) {
      throw new IllegalArgumentException("methodName must not be null");
    }
    if (invocationIndex < 0) {
      throw new IllegalArgumentException("invocationIndex must not be negative");
    }
    this.testClassName = testClassName;
    this.methodName = methodName;
    this.invocationIndex = invocationIndex;
    this.invocationLabel = invocationLabel == null ? "" : invocationLabel;
  }

  /** The identity of a test method that runs exactly once — the artifact name it has always had. */
  public static ArtifactIdentity ofMethod(String testClassName, String methodName) {
    return new ArtifactIdentity(testClassName, methodName, 0, "");
  }

  /**
   * The identity of one invocation of a test method that runs more than once.
   *
   * @param invocationIndex 1-based, in the engine's invocation order
   * @param invocationLabel the invocation's display name, used only for readability
   */
  public static ArtifactIdentity ofInvocation(
      String testClassName, String methodName, int invocationIndex, String invocationLabel) {
    if (invocationIndex < 1) {
      throw new IllegalArgumentException("invocationIndex is 1-based; got " + invocationIndex);
    }
    return new ArtifactIdentity(testClassName, methodName, invocationIndex, invocationLabel);
  }

  /** Whether this identity names one invocation of a repeated method rather than a whole method. */
  public boolean isInvocation() {
    return invocationIndex > 0;
  }

  /** The artifact base name, without any format suffix: the scheme documented on this record. */
  public String fileSlug() {
    return OutputDirectoryResolver.toFileSlug(methodName, invocationIndex, invocationLabel);
  }
}
