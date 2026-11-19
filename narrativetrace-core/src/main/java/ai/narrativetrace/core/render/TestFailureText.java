/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * The one-line form of a failed test's own message, as a trace file states it.
 *
 * <p>INTENT: a test that fails on its own assertion while every recorded call returns normally
 * leaves the message nowhere in the trace: the test method is not a span. The file's header and its
 * Markdown body both state it, and both state this same text, so the two cannot drift on how a long
 * or multi-line message is shortened.
 *
 * <p><b>@llmNote</b> The input is the string the run's manifest row already carries, which has been
 * through {@link ExceptionMessage}; never pass a raw {@link Throwable#getMessage()}. Escaping for
 * the sink (a Markdown body, an HTML comment) is the sink's job and happens after this.
 */
public final class TestFailureText {

  /** The most characters a trace file states of a test's failure message, ellipsis included. */
  public static final int LIMIT = 1000;

  private static final String ELLIPSIS = "…";

  private TestFailureText() {}

  /**
   * The message on one line, cut to {@link #LIMIT} characters with a trailing ellipsis when longer.
   *
   * @param message the manifest row's message, or {@code null}
   * @return the one-line text, or {@code null} when there is no message to state (absent or blank)
   */
  public static String oneLine(String message) {
    if (message == null || message.isBlank()) {
      return null;
    }
    var line = message.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    if (line.length() <= LIMIT) {
      return line;
    }
    var end = LIMIT - ELLIPSIS.length();
    if (Character.isHighSurrogate(line.charAt(end - 1))) {
      end--; // never cut a surrogate pair in half
    }
    return line.substring(0, end) + ELLIPSIS;
  }
}
