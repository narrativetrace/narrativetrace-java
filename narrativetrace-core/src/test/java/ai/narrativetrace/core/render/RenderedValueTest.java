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
import ai.narrativetrace.api.event.RenderedValue.BooleanVal;
import ai.narrativetrace.api.event.RenderedValue.DoubleVal;
import ai.narrativetrace.api.event.RenderedValue.InstantVal;
import ai.narrativetrace.api.event.RenderedValue.ListVal;
import ai.narrativetrace.api.event.RenderedValue.LongVal;
import ai.narrativetrace.api.event.RenderedValue.NullVal;
import ai.narrativetrace.api.event.RenderedValue.ObjectVal;
import ai.narrativetrace.api.event.RenderedValue.StringVal;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderedValueTest {

  @Test
  void stringValPreservesValue() {
    var val = new StringVal("hello");
    assertThat(val.value()).isEqualTo("hello");
  }

  @Test
  void longValPreservesValue() {
    var val = new LongVal(42L);
    assertThat(val.value()).isEqualTo(42L);
  }

  @Test
  void doubleValPreservesValue() {
    var val = new DoubleVal(3.14);
    assertThat(val.value()).isEqualTo(3.14);
  }

  @Test
  void booleanValPreservesValue() {
    var val = new BooleanVal(true);
    assertThat(val.value()).isTrue();
  }

  @Test
  void instantValPreservesEpochMillis() {
    var val = new InstantVal(1742134981123L);
    assertThat(val.epochMillis()).isEqualTo(1742134981123L);
  }

  @Test
  void objectValPreservesTypeNameAndFields() {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("id", new StringVal("X"));
    fields.put("total", new DoubleVal(99.9));
    var val = new ObjectVal("Order", fields);

    assertThat(val.typeName()).isEqualTo("Order");
    assertThat(val.fields()).hasSize(2);
    assertThat(val.fields().get("id")).isEqualTo(new StringVal("X"));
    assertThat(val.fields().get("total")).isEqualTo(new DoubleVal(99.9));
  }

  @Test
  void objectValWithNestedObject() {
    var addressFields = new LinkedHashMap<String, RenderedValue>();
    addressFields.put("city", new StringVal("NYC"));
    var address = new ObjectVal("Address", addressFields);

    var orderFields = new LinkedHashMap<String, RenderedValue>();
    orderFields.put("address", address);
    var order = new ObjectVal("Order", orderFields);

    assertThat(order.fields().get("address")).isInstanceOf(ObjectVal.class);
    var nested = (ObjectVal) order.fields().get("address");
    assertThat(nested.fields().get("city")).isEqualTo(new StringVal("NYC"));
  }

  @Test
  void listValPreservesElements() {
    var elements =
        List.<RenderedValue>of(new LongVal(1), new StringVal("two"), new BooleanVal(true));
    var val = new ListVal(elements);

    assertThat(val.elements()).hasSize(3);
    assertThat(val.elements().get(0)).isEqualTo(new LongVal(1));
    assertThat(val.elements().get(1)).isEqualTo(new StringVal("two"));
    assertThat(val.elements().get(2)).isEqualTo(new BooleanVal(true));
  }

  @Test
  void nullValIsSingleton() {
    var val = new NullVal();
    assertThat(val).isEqualTo(new NullVal());
  }

  @Test
  void allVariantsAreRenderedValueSubtypes() {
    // Verifies every variant is a RenderedValue — if a variant is added to the
    // sealed interface without a test, this list serves as a reminder to add one.
    assertThat(new StringVal("x")).isInstanceOf(RenderedValue.class);
    assertThat(new LongVal(1)).isInstanceOf(RenderedValue.class);
    assertThat(new DoubleVal(1.0)).isInstanceOf(RenderedValue.class);
    assertThat(new BooleanVal(true)).isInstanceOf(RenderedValue.class);
    assertThat(new InstantVal(0)).isInstanceOf(RenderedValue.class);
    assertThat(new ObjectVal("T", new LinkedHashMap<>())).isInstanceOf(RenderedValue.class);
    assertThat(new ListVal(List.of())).isInstanceOf(RenderedValue.class);
    assertThat(new NullVal()).isInstanceOf(RenderedValue.class);
  }

  @Test
  void instantValDistinctFromLongVal() {
    RenderedValue instant = new InstantVal(1000L);
    RenderedValue longVal = new LongVal(1000L);

    assertThat(instant).isNotEqualTo(longVal);
    assertThat(instant).isInstanceOf(InstantVal.class);
    assertThat(longVal).isInstanceOf(LongVal.class);
  }
}
