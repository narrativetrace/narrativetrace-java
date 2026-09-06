/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.context.catdd.ContractVerifiable;
import ai.narrativetrace.core.context.catdd.InvariantCheckExtension;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The eviction policy behind {@link TemplateParser}'s cache, exercised directly. */
@ExtendWith(InvariantCheckExtension.class)
class BoundedTemplateCacheTest implements ContractVerifiable<BoundedTemplateCache> {

  private static final Function<String, List<TemplateParser.Segment>> PARSER =
      TemplateParser::parse;

  private BoundedTemplateCache cache;
  private AtomicInteger parseCount;

  @BeforeEach
  void setUp() {
    cache = new BoundedTemplateCache(4);
    parseCount = new AtomicInteger();
  }

  @Override
  public BoundedTemplateCache subject() {
    return cache;
  }

  @Override
  public boolean checkInvariant() {
    return cache == null || cache.invariant();
  }

  private Function<String, List<TemplateParser.Segment>> countingParser() {
    return template -> {
      parseCount.incrementAndGet();
      return TemplateParser.parse(template);
    };
  }

  @Test
  @DisplayName("a repeated template is parsed once")
  void aRepeatedTemplateIsParsedOnce() {
    cache.get("{a}", countingParser());
    cache.get("{a}", countingParser());
    cache.get("{a}", countingParser());

    assertThat(parseCount).hasValue(1);
  }

  @Test
  @DisplayName("retention never exceeds two generations")
  void retentionNeverExceedsTwoGenerations() {
    for (int i = 0; i < 10_000; i++) {
      cache.get("t" + i, PARSER);
    }

    assertThat(cache.size()).isLessThanOrEqualTo(8);
  }

  @Test
  @DisplayName("a template used through a flood is promoted, not re-parsed")
  void aTemplateUsedThroughAFloodIsPromotedRatherThanReparsed() {
    var parser = countingParser();
    for (int i = 0; i < 500; i++) {
      cache.get("noise" + i, parser);
      cache.get("{hot}", parser);
    }

    assertThat(cache.contains("{hot}")).isTrue();
    assertThat(parseCount).hasValue(501);
  }

  @Test
  @DisplayName("an unused template is dropped once two generations have turned over")
  void anUnusedTemplateIsEventuallyDropped() {
    cache.get("{cold}", PARSER);
    for (int i = 0; i < 100; i++) {
      cache.get("churn" + i, PARSER);
    }

    assertThat(cache.contains("{cold}")).isFalse();
  }

  @Test
  @DisplayName("a demoted template is still found, without re-parsing")
  void aDemotedTemplateIsServedFromThePreviousGeneration() {
    var parser = countingParser();
    cache.get("{demoted}", parser);
    // Four more admissions retire the generation holding it.
    for (int i = 0; i < 4; i++) {
      cache.get("filler" + i, parser);
    }

    cache.get("{demoted}", parser);

    assertThat(parseCount).as("promotion must not re-parse").hasValue(5);
    assertThat(cache.contains("{demoted}")).isTrue();
  }

  @Test
  @DisplayName("a generation size below one is refused")
  void aGenerationSizeBelowOneIsRefused() {
    assertThatThrownBy(() -> new BoundedTemplateCache(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
    assertThatThrownBy(() -> new BoundedTemplateCache(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a generation size of one is legal and still caches")
  void aGenerationSizeOfOneIsLegal() {
    var tiny = new BoundedTemplateCache(1);

    assertThat(tiny.get("{a}", PARSER)).isEqualTo(TemplateParser.parse("{a}"));
    assertThat(tiny.size()).isLessThanOrEqualTo(2);
  }

  @Test
  @DisplayName("null arguments are refused rather than cached")
  void nullArgumentsAreRefused() {
    assertThatThrownBy(() -> cache.get(null, PARSER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template");
    assertThatThrownBy(() -> cache.get("{a}", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parser");
  }

  @Test
  @DisplayName("an absent template is not reported as contained")
  void anAbsentTemplateIsNotReportedAsContained() {
    assertThat(cache.contains("{never-seen}")).isFalse();
    assertThat(cache.size()).isZero();
  }

  private static final int FLOOD_THREADS = 8;
  private static final int GENERATION = 16;

  @Test
  @DisplayName("concurrent flooding stays bounded and answers correctly")
  void concurrentFloodingStaysBoundedAndCorrect() throws InterruptedException {
    var bounded = new BoundedTemplateCache(GENERATION);
    var wrong = new ConcurrentHashMap<String, List<TemplateParser.Segment>>();

    floodConcurrently(bounded, wrong);

    assertThat(wrong).as("every answer must equal a fresh parse").isEmpty();
    assertThat(bounded.invariant()).isTrue();
    assertThat(bounded.size())
        .as("bounded by two generations plus threads racing to admit")
        .isLessThanOrEqualTo(GENERATION * 2 + FLOOD_THREADS);
  }

  private void floodConcurrently(
      BoundedTemplateCache bounded, Map<String, List<TemplateParser.Segment>> wrong)
      throws InterruptedException {
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(FLOOD_THREADS);
    for (int t = 0; t < FLOOD_THREADS; t++) {
      final int id = t;
      new Thread(() -> floodOnce(bounded, wrong, id, start, done)).start();
    }
    start.countDown();
    assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
  }

  private void floodOnce(
      BoundedTemplateCache bounded,
      Map<String, List<TemplateParser.Segment>> wrong,
      int id,
      CountDownLatch start,
      CountDownLatch done) {
    try {
      start.await();
      for (int i = 0; i < 2_000; i++) {
        var key = "t" + id + "-" + i;
        var got = bounded.get(key, PARSER);
        if (!got.equals(TemplateParser.parse(key))) {
          wrong.put(key, got);
        }
        bounded.get("{hot}", PARSER);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      done.countDown();
    }
  }
}
