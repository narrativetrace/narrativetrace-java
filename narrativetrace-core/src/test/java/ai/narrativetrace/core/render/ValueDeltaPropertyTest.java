/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.LinkedHashMap;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Invariants of the field-level delta that example tests cannot exhaust: whatever the field count
 * or the pattern of changes, the diff names every field that moved, no field that did not, and
 * never emits a raw control character into the document.
 */
class ValueDeltaPropertyTest {

  private static RenderedValue.ObjectVal order(List<Integer> values, int changeMask) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("id", new RenderedValue.StringVal("order-77"));
    for (var i = 0; i < values.size(); i++) {
      var value = changed(changeMask, i) ? values.get(i) + 1 : values.get(i);
      fields.put("f" + i, new RenderedValue.LongVal(value));
    }
    return new RenderedValue.ObjectVal("Order", fields);
  }

  private static boolean changed(int changeMask, int index) {
    return (changeMask & (1 << index)) != 0;
  }

  private static RenderedValue.ObjectVal named(RenderedValue body) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("title", new RenderedValue.StringVal("Standup"));
    fields.put("body", body);
    return new RenderedValue.ObjectVal("Note", fields);
  }

  @Property
  void theDeltaNamesEveryChangedFieldAndNoUnchangedOne(
      @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 20) Integer> values,
      @ForAll @IntRange(min = 0, max = 63) int changeMask) {
    var delta = ValueDelta.between(order(values, 0), order(values, changeMask));

    var anyChanged = false;
    for (var i = 0; i < values.size(); i++) {
      anyChanged |= changed(changeMask, i);
    }
    if (!anyChanged) {
      assertThat(delta).isNull();
      return;
    }
    assertThat(delta).isNotNull();
    for (var i = 0; i < values.size(); i++) {
      var field = "f" + i + ": ";
      if (changed(changeMask, i)) {
        assertThat(delta).contains(field + values.get(i) + "→" + (values.get(i) + 1));
      } else {
        assertThat(delta).doesNotContain(field);
      }
    }
  }

  @Property
  void aValueIsNeverADeltaOfItself(
      @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 20) Integer> values) {
    assertThat(ValueDelta.between(order(values, 0), order(values, 0))).isNull();
  }

  @Property
  void noControlCharacterFromAChangedStringEverReachesTheDelta(
      @ForAll @IntRange(min = 0, max = 31) int code) {
    var after = named(new RenderedValue.StringVal("a" + (char) code + "b"));

    var delta = ValueDelta.between(named(new RenderedValue.StringVal("before")), after);

    assertThat(delta).isNotNull();
    assertThat(delta.chars().noneMatch(Character::isISOControl)).isTrue();
  }

  @Property
  void aChangedStructuredFieldIsNeverExpressedAsADelta(
      @ForAll @IntRange(min = 0, max = 20) int amount) {
    var before = named(new RenderedValue.ListVal(List.of(new RenderedValue.LongVal(amount))));
    var after = named(new RenderedValue.ListVal(List.of(new RenderedValue.LongVal(amount + 1))));

    assertThat(ValueDelta.between(before, after)).isNull();
  }
}
