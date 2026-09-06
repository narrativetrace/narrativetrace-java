/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import ai.narrativetrace.api.event.SourceLocation;

/**
 * Locates the call site of a proxied invocation via {@link StackWalker}.
 *
 * <p>INTENT: Proxied interfaces carry no line info, so when {@code
 * narrativetrace.capture.sourceLocation} is enabled the proxy records the CALLER's frame — the
 * first stack frame that is neither this library nor JDK proxy/reflection plumbing. This walk is
 * the per-call cost that keeps the flag off by default, and it is deliberately asymmetric with the
 * agent path (which bakes the instrumented method's own declaration site for free).
 *
 * <p><b>@llmNote</b> Infrastructure frames are matched by exact library class names plus JDK
 * reflection/proxy prefixes — never by the {@code ai.narrativetrace.proxy} package prefix, which
 * would also swallow user classes that happen to live in a similarly named package (and this
 * module's own tests).
 */
final class CallSiteLocator {

  private static final StackWalker WALKER = StackWalker.getInstance();
  private static final String PROXY_CLASS = NarrativeTraceProxy.class.getName();

  private CallSiteLocator() {}

  /** The caller's source location, or {@code null} when no application frame is found. */
  static SourceLocation callerLocation() {
    return WALKER.walk(
        frames ->
            frames
                .filter(f -> !isInfrastructure(f.getClassName()))
                .findFirst()
                .map(CallSiteLocator::toLocation)
                .orElse(null));
  }

  private static SourceLocation toLocation(StackWalker.StackFrame frame) {
    int line = frame.getLineNumber();
    return new SourceLocation(frame.getFileName(), line >= 0 ? line : null);
  }

  static boolean isInfrastructure(String className) {
    return className.equals(CallSiteLocator.class.getName())
        || className.equals(PROXY_CLASS)
        || className.startsWith(PROXY_CLASS + "$")
        || className.startsWith("java.lang.reflect.")
        || className.startsWith("jdk.internal.reflect.")
        || className.startsWith("jdk.proxy")
        || className.startsWith("com.sun.proxy")
        || isGeneratedProxyClass(className);
  }

  /**
   * Generated proxy classes for non-public interfaces live in the INTERFACE's package (e.g. {@code
   * com.acme.$Proxy14}), not under {@code jdk.proxy}; match them by their reserved simple-name
   * prefix.
   */
  private static boolean isGeneratedProxyClass(String className) {
    int lastDot = className.lastIndexOf('.');
    return className.startsWith("$Proxy", lastDot + 1);
  }
}
