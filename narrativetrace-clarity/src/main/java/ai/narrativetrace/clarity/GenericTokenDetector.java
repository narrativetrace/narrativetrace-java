/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.Locale;
import java.util.Set;

/**
 * Detects generic or meaningless tokens in identifiers (e.g., data, info, temp, obj).
 *
 * <p>A project's committed glossary nouns ({@link DomainVocabulary}) lift otherwise-broad words out
 * of the generic tiers — {@code position} is vague in general and precise in a trading domain — but
 * never rescue a meaningless placeholder; see {@link #detect}.
 */
public final class GenericTokenDetector {

  public enum Tier {
    MEANINGLESS,
    VAGUE,
    TYPED_GENERIC,
    NOT_GENERIC
  }

  public record Result(Tier tier, double score) {}

  private static final Set<String> MEANINGLESS_PLACEHOLDERS =
      Set.of(
          "foo", "bar", "baz", "qux", "quux", "temp", "tmp", "test", "dummy", "sample", "example",
          "xxx", "yyy", "zzz", "todo", "fixme");

  private static final Set<String> VAGUE_WORDS =
      Set.of(
          "data",
          "info",
          "object",
          "thing",
          "item",
          "element",
          "stuff",
          "result",
          "response",
          "output",
          "input",
          "value",
          "content",
          "payload",
          "resource",
          "record",
          "entry",
          "detail",
          "details",
          "entity",
          "bean",
          "model",
          "wrapper",
          "holder",
          "container",
          "bundle",
          "batch",
          "chunk",
          "block",
          "piece",
          "part",
          "unit",
          "instance",
          "param",
          "argument",
          "body",
          "obj",
          "val",
          "arg",
          "meta",
          "metadata",
          "blob",
          "document",
          "artifact",
          "messagebody",
          "dataset",
          "modeloutput",
          "modelinput");

  private static final Set<String> TYPED_GENERIC_WORDS =
      Set.of(
          "id",
          "name",
          "type",
          "status",
          "state",
          "count",
          "size",
          "length",
          "index",
          "key",
          "flag",
          "code",
          "text",
          "message",
          "label",
          "number",
          "amount",
          "total",
          "level",
          "mode",
          "kind",
          "category",
          "group",
          "list",
          "map",
          "set",
          "queue",
          "stack",
          "array",
          "collection",
          "table",
          "row",
          "column",
          "field",
          "property",
          "tag",
          "version",
          "timestamp",
          "date",
          "time",
          "duration",
          "interval",
          "timeout",
          "limit",
          "offset",
          "page",
          "sort",
          "order",
          "direction",
          "position",
          "priority",
          "weight",
          "rank",
          "score",
          "rating",
          "percentage",
          "ratio",
          "factor",
          "coefficient",
          "path",
          "url",
          "uri",
          "host",
          "port",
          "endpoint",
          "topic",
          "channel",
          "session",
          "token",
          "trace",
          "metric",
          "tenant");

  private final DomainVocabulary vocabulary;

  /** A detector with no project vocabulary — the built-in tiers alone. */
  public GenericTokenDetector() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public GenericTokenDetector(DomainVocabulary vocabulary) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("vocabulary must not be null");
    }
    this.vocabulary = vocabulary;
  }

  /**
   * Classifies one identifier token by how much meaning it carries.
   *
   * <p>Meaningless placeholders are decided first, so committing {@code temp} or {@code foo} to a
   * glossary cannot make them meaningful. Every other tier yields to the project: a declared noun
   * is domain vocabulary by definition.
   *
   * @param token identifier token to classify; must not be {@code null}
   * @return the tier and its specificity score
   */
  public Result detect(String token) {
    var lower = token.toLowerCase(Locale.ROOT);

    if (isMeaninglessSingleLetter(lower) || MEANINGLESS_PLACEHOLDERS.contains(lower)) {
      return new Result(Tier.MEANINGLESS, 0.0);
    }
    if (vocabulary.isDomainNoun(lower)) {
      return new Result(Tier.NOT_GENERIC, 1.0);
    }
    if (VAGUE_WORDS.contains(lower)) {
      return new Result(Tier.VAGUE, 0.2);
    }
    if (TYPED_GENERIC_WORDS.contains(lower)) {
      return new Result(Tier.TYPED_GENERIC, 0.5);
    }
    return new Result(Tier.NOT_GENERIC, 1.0);
  }

  private boolean isMeaninglessSingleLetter(String lower) {
    if (lower.length() != 1) return false;
    char c = lower.charAt(0);
    return c >= 'a' && c <= 'z';
  }
}
