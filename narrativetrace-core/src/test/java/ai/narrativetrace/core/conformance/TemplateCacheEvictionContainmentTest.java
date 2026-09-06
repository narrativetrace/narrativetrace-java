/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.template.TemplateParser;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bounding the template cache must not change a single answer it gives — least of all a redacted
 * one.
 *
 * <p>INTENT: The 2026-09-02 audit's finding 2 made the cache evict; finding 9, fixed a day earlier,
 * was a template resolution printing a secret verbatim. Eviction is a new way to reach the parse
 * path a second time, so this asserts the combination the two findings create: a template that has
 * been evicted and re-parsed hides exactly what it hid the first time.
 *
 * <p><b>@llmNote</b> Deliberately outside {@code core.template}: it may use only the public {@code
 * resolve} surface, so it cannot accidentally assert on cache internals instead of on the shipped
 * behaviour, and it proves the resolver is reachable and correct across a package boundary.
 */
class TemplateCacheEvictionContainmentTest {

  private static final String SECRET = "cvv-9137-not-for-logs";
  private static final int ENOUGH_TO_EVICT_ANYTHING = 5_000;

  record Card(String number, @NotTraced String cvv) {}

  private void floodTheCache() {
    for (int i = 0; i < ENOUGH_TO_EVICT_ANYTHING; i++) {
      TemplateParser.resolve("flood " + i + " {x}", Map.of("x", "y"));
    }
  }

  @Test
  @DisplayName("a redacted property still redacts after its template was evicted")
  void aRedactedPropertyStillRedactsAfterEviction() {
    var template = "charging {card.cvv}";
    var values = Map.<String, Object>of("card", new Card("4111", SECRET));
    var before = TemplateParser.resolve(template, values);
    assertThat(before).isEqualTo("charging " + RedactionPolicy.MARKER).doesNotContain(SECRET);

    floodTheCache();

    assertThat(TemplateParser.resolve(template, values)).isEqualTo(before).doesNotContain(SECRET);
  }

  @Test
  @DisplayName("a whole redacted object still redacts after its template was evicted")
  void aWholeObjectStillRedactsAfterEviction() {
    var template = "audit {card}";
    var values = Map.<String, Object>of("card", new Card("4111", SECRET));
    var before = TemplateParser.resolve(template, values);
    assertThat(before).doesNotContain(SECRET);

    floodTheCache();

    assertThat(TemplateParser.resolve(template, values)).isEqualTo(before).doesNotContain(SECRET);
  }

  @Test
  @DisplayName("an unresolved placeholder survives eviction verbatim")
  void anUnresolvedPlaceholderSurvivesEviction() {
    var template = "hello {absent} and {also.absent}";
    var before = TemplateParser.resolve(template, Map.of());
    assertThat(before).isEqualTo("hello {absent} and {also.absent}");

    floodTheCache();

    assertThat(TemplateParser.resolve(template, Map.of())).isEqualTo(before);
  }

  @Test
  @DisplayName("the hostile template shapes still resolve identically after eviction")
  void hostileTemplateShapesResolveIdenticallyAfterEviction() {
    var shapes =
        new String[] {
          "", "{}", "{{a}}", "{a", "a}", "{a.b.c}", "{ }", "no placeholders at all", "{a}{a}{a}"
        };
    var values = Map.<String, Object>of("a", "A");
    var first = new String[shapes.length];
    for (int i = 0; i < shapes.length; i++) {
      first[i] = TemplateParser.resolve(shapes[i], values);
    }

    floodTheCache();

    for (int i = 0; i < shapes.length; i++) {
      assertThat(TemplateParser.resolve(shapes[i], values))
          .as("shape %s", shapes[i])
          .isEqualTo(first[i]);
    }
  }
}
