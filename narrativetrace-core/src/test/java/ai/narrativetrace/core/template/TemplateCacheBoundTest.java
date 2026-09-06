/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The template cache is a JVM-global, caller-keyed structure on the synchronous path.
 *
 * <p>INTENT: {@code TemplateParser.resolve} is public and accepts any string. A framework adapter,
 * plugin or downstream caller that resolves a request-shaped template would otherwise grow a static
 * map for the life of the process — host memory exhaustion reached through instrumentation, before
 * any best-effort discard logic can help, because this is the sync path. Found by the 2026-09-02
 * adversarial audit (finding 2).
 *
 * @llmNote These tests deliberately assert a <em>bound</em> and never an exact size. The cache is
 *     static and shared with every other test in this module, so any test that resolves a template
 *     perturbs it; only "no more than" survives that.
 */
class TemplateCacheBoundTest {

  private static final int FLOOD = 100_000;

  @Test
  @DisplayName("resolving a flood of unique templates leaves the cache bounded")
  void resolvingManyUniqueTemplatesKeepsTheCacheBounded() {
    for (int i = 0; i < FLOOD; i++) {
      TemplateParser.resolve("order " + i + " for {customer}", Map.of("customer", "ada"));
    }

    assertThat(TemplateParser.cachedTemplateCount())
        .as("a flood of %d unique templates must not be retained", FLOOD)
        .isLessThanOrEqualTo(TemplateParser.MAX_CACHED_TEMPLATES);
  }

  @Test
  @DisplayName("a template in constant use survives a flood of unique ones")
  void theHotAnnotationTemplateIsNotEvictedByAFloodOfSingleUseTemplates() {
    var hot = "charging {order.total} for {customer}";
    var values = Map.<String, Object>of("customer", "ada");

    for (int i = 0; i < FLOOD; i++) {
      TemplateParser.resolve("noise " + i + " {customer}", values);
      TemplateParser.resolve(hot, values);
    }

    assertThat(TemplateParser.isCached(hot))
        .as("the annotation template used on every call must stay parsed")
        .isTrue();
  }

  @Test
  @DisplayName("a flood does not change what resolution answers")
  void resolutionStaysCorrectForATemplateThatWasEvicted() {
    var evicted = "evicted {customer} template";
    var values = Map.<String, Object>of("customer", "ada");
    var before = TemplateParser.resolve(evicted, values);

    for (int i = 0; i < FLOOD; i++) {
      TemplateParser.resolve("flood " + i + " {customer}", values);
    }

    assertThat(TemplateParser.resolve(evicted, values))
        .isEqualTo(before)
        .isEqualTo("evicted ada template");
  }
}
