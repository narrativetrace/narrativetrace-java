/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.regex.Pattern;

/**
 * Whether a class's binary name carries a shape only a bytecode generator or the JVM itself
 * produces — list 1, built in, never configurable.
 *
 * <p>INTENT: A generated subclass of a user's own class (CGLIB, Byte Buddy, a JDK proxy for a
 * package-private interface) inherits the user's own package, so no package-name exclusion (list 2,
 * see {@link AgentConfig#DEFAULT_EXCLUDE}) could ever catch it. Only a name-shape rule can, which
 * is what this class is.
 *
 * <p><b>@llmNote</b> Hidden classes (lambda instances, anything defined through {@link
 * java.lang.invoke.MethodHandles.Lookup#defineHiddenClass}) were confirmed, against this
 * repository's own JDK, to never reach {@link java.lang.instrument.ClassFileTransformer#transform}
 * at all — the JVM's class-file-load hook does not fire for them. The hidden-class check here is
 * kept anyway as a defensive rule a future JDK or JVMTI agent chain might need; it is tested
 * directly, not through a live hidden-class scenario, because no live scenario can reach it today.
 */
final class GeneratedClassNames {

  /** CGLIB, Spring CGLIB, lambda hidden classes — every double-dollar generator shape. */
  static final String GENERIC_DOUBLE_DOLLAR_MARKER = "$$";

  /** Byte Buddy's own default naming strategy for the main generated type. */
  static final String BYTE_BUDDY_MARKER = "$ByteBuddy$";

  /** Mockito's inline mock maker subclass naming. */
  static final String MOCKITO_MOCK_MARKER = "$MockitoMock$";

  /** Glowroot's rule: a JDK dynamic proxy's simple name is exactly this shape, any package. */
  private static final Pattern JDK_DYNAMIC_PROXY = Pattern.compile("\\$Proxy\\d+");

  /** A hidden class's name, as the JVM assigns it, ends with its identity as a hex suffix. */
  private static final Pattern HIDDEN_CLASS_SUFFIX = Pattern.compile("/0x[0-9a-fA-F]+$");

  private GeneratedClassNames() {}

  /**
   * @param internalClassName the class's JVM-internal name (slash-separated), as handed to a {@link
   *     java.lang.instrument.ClassFileTransformer}
   * @return {@code true} when the name's shape is one only a generator or the JVM itself produces
   */
  static boolean isGenerated(String internalClassName) {
    if (HIDDEN_CLASS_SUFFIX.matcher(internalClassName).find()) {
      return true;
    }
    var simpleName = simpleNameOf(internalClassName);
    return simpleName.contains(GENERIC_DOUBLE_DOLLAR_MARKER)
        || simpleName.contains(BYTE_BUDDY_MARKER)
        || simpleName.contains(MOCKITO_MOCK_MARKER)
        || JDK_DYNAMIC_PROXY.matcher(simpleName).matches();
  }

  private static String simpleNameOf(String internalClassName) {
    var lastSlash = internalClassName.lastIndexOf('/');
    return lastSlash < 0 ? internalClassName : internalClassName.substring(lastSlash + 1);
  }
}
