/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

/**
 * One span of an exported trace, flattened in pre-order: enough to say what ran, with what, on
 * which thread, under which parent — and simple enough to cross a process boundary as a text line.
 *
 * @param index position in pre-order
 * @param depth 0 for a root; a node's descendants are the nodes after it with a greater depth
 * @param method the traced method's name
 * @param firstArgument the first parameter's rendered value, quotes and all
 * @param returned the rendered return value, empty when it did not return
 * @param thread the thread the span ran on, empty when unknown
 * @param traceId the span's trace id
 */
public record FlatNode(
    int index,
    int depth,
    String method,
    String firstArgument,
    String returned,
    String thread,
    String traceId) {

  private static final String SEPARATOR = "\t";

  /** One line, tabs and newlines in values replaced by spaces. */
  public String toLine() {
    return String.join(
        SEPARATOR,
        Integer.toString(index),
        Integer.toString(depth),
        clean(method),
        clean(firstArgument),
        clean(returned),
        clean(thread),
        clean(traceId));
  }

  public static FlatNode parse(String line) {
    var parts = line.split(SEPARATOR, -1);
    return new FlatNode(
        Integer.parseInt(parts[0]),
        Integer.parseInt(parts[1]),
        parts[2],
        parts[3],
        parts[4],
        parts[5],
        parts[6]);
  }

  private static String clean(String value) {
    return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }
}
