/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.RenderedValue.BooleanVal;
import ai.narrativetrace.api.event.RenderedValue.DoubleVal;
import ai.narrativetrace.api.event.RenderedValue.InstantVal;
import ai.narrativetrace.api.event.RenderedValue.ListVal;
import ai.narrativetrace.api.event.RenderedValue.LongVal;
import ai.narrativetrace.api.event.RenderedValue.NullVal;
import ai.narrativetrace.api.event.RenderedValue.ObjectVal;
import ai.narrativetrace.api.event.RenderedValue.StringVal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ValueRendererStructuredTest {

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void nullRendersAsNullVal() {
    assertThat(renderer.renderStructured(null)).isEqualTo(new NullVal());
  }

  @Test
  void stringRendersAsStringVal() {
    assertThat(renderer.renderStructured("hello")).isEqualTo(new StringVal("hello"));
  }

  static class Account {
    final String username = "jsmith";
    final String password = "hunter2";
  }

  @Test
  void redactsDenyListedFieldNamesInStructuredObjectOutput() {
    // Secure-by-default: a reflectively-introspected field whose name matches the deny-list is
    // redacted without any annotation, so nested secrets do not leak into structured output.
    var rendered = (ObjectVal) renderer.renderStructured(new Account());

    assertThat(rendered.fields().get("username")).isEqualTo(new StringVal("jsmith"));
    assertThat(rendered.fields().get("password")).isEqualTo(new StringVal("[REDACTED]"));
  }

  record Card(String number, String cvv) {}

  @Test
  void redactsDenyListedRecordComponentsInStructuredOutput() {
    var rendered = (ObjectVal) renderer.renderStructured(new Card("4111", "123"));

    assertThat(rendered.fields().get("number")).isEqualTo(new StringVal("4111"));
    assertThat(rendered.fields().get("cvv")).isEqualTo(new StringVal("[REDACTED]"));
  }

  static class Profile {
    final String bio = "hello";
    @NotTraced final String note = "custom-secret";
  }

  @Test
  void redactsFieldsAnnotatedNotTracedInStructuredOutput() {
    // "note" is not in the deny-list, so redaction here proves the annotation is honored on its
    // own.
    var rendered = (ObjectVal) renderer.renderStructured(new Profile());

    assertThat(rendered.fields().get("bio")).isEqualTo(new StringVal("hello"));
    assertThat(rendered.fields().get("note")).isEqualTo(new StringVal("[REDACTED]"));
  }

  record Session(String id, @NotTraced String data) {}

  @Test
  void redactsRecordComponentsAnnotatedNotTracedInStructuredOutput() {
    var rendered = (ObjectVal) renderer.renderStructured(new Session("s1", "secret-data"));

    assertThat(rendered.fields().get("id")).isEqualTo(new StringVal("s1"));
    assertThat(rendered.fields().get("data")).isEqualTo(new StringVal("[REDACTED]"));
  }

  static class ChattyProfile {
    final String bio = "hello";
    @NotTraced final String note = "toString-secret";

    @Override
    public String toString() {
      return "ChattyProfile{bio=" + bio + ", note=" + note + "}";
    }
  }

  @Test
  void aCustomToStringDoesNotBypassAnAnnotatedFieldsRedactionInStructuredOutput() {
    // Parity with the flat path: a class that declares a redacted field is introspected, so the
    // structured value stays an ObjectVal with the marker in it instead of one opaque StringVal
    // holding whatever toString() chose to print.
    var rendered = (ObjectVal) renderer.renderStructured(new ChattyProfile());

    assertThat(rendered.fields().get("bio")).isEqualTo(new StringVal("hello"));
    assertThat(rendered.fields().get("note")).isEqualTo(new StringVal("[REDACTED]"));
  }

  record LoudCard(String number, @NotTraced String cvv) {
    @Override
    public String toString() {
      return "LoudCard{number=" + number + ", cvv=" + cvv + "}";
    }
  }

  @Test
  void aRecordThatOverridesToStringStillRedactsItsAnnotatedComponent() {
    // Records were never exposed to the seam — isRecord() is checked before toString() — but a
    // future reordering of that decision tree would be a leak, so the order is pinned here.
    var rendered = (ObjectVal) renderer.renderStructured(new LoudCard("4111", "cvv-secret"));

    assertThat(rendered.fields().get("number")).isEqualTo(new StringVal("4111"));
    assertThat(rendered.fields().get("cvv")).isEqualTo(new StringVal("[REDACTED]"));
  }

  @Test
  void mapRendersAsStructuredObjectValWithDenyListedValuesRedacted() {
    var headers = new java.util.LinkedHashMap<String, String>();
    headers.put("Accept", "application/json");
    headers.put("Authorization", "Bearer xyz");

    var rendered = (ObjectVal) renderer.renderStructured(headers);

    assertThat(rendered.typeName()).isEqualTo("Map");
    assertThat(rendered.fields().get("Accept")).isEqualTo(new StringVal("application/json"));
    assertThat(rendered.fields().get("Authorization")).isEqualTo(new StringVal("[REDACTED]"));
  }

  @Test
  void structuredMapKeysRenderThroughRedactionInsteadOfToString() {
    var balances = new java.util.LinkedHashMap<Session, Integer>();
    balances.put(new Session("s1", "secret-data"), 42);

    var rendered = (ObjectVal) renderer.renderStructured(balances);

    assertThat(rendered.fields()).containsKey("Session(id: \"s1\", data: [REDACTED])");
    assertThat(rendered.fields().keySet().toString()).doesNotContain("secret-data");
  }

  @Test
  void structuredMapStringKeysAreSanitizedAgainstControlCharacters() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    map.put("a\nb", 1);

    var rendered = (ObjectVal) renderer.renderStructured(map);

    assertThat(rendered.fields()).containsKey("a\\nb");
  }

  @Test
  void structuredMapCapsEntriesAtCollectionLimit() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    for (int i = 0; i < 8; i++) {
      map.put("k" + i, i);
    }

    var rendered = (ObjectVal) renderer.renderStructured(map);

    assertThat(rendered.fields()).hasSize(5); // default cap
    assertThat(rendered.fields()).containsKey("k4");
    assertThat(rendered.fields()).doesNotContainKey("k5");
  }

  @Test
  void structuredSelfReferentialMapCollapsesToMarkerWithoutInfiniteRecursion() {
    var map = new java.util.LinkedHashMap<String, Object>();
    map.put("self", map);

    var rendered = (ObjectVal) renderer.renderStructured(map);

    assertThat(rendered.fields()).containsKey("self");
    assertThat(rendered.fields().get("self")).isInstanceOf(StringVal.class);
  }

  @Test
  void integerRendersAsLongVal() {
    assertThat(renderer.renderStructured(42)).isEqualTo(new LongVal(42L));
  }

  @Test
  void longRendersAsLongVal() {
    assertThat(renderer.renderStructured(100L)).isEqualTo(new LongVal(100L));
  }

  @Test
  void shortRendersAsLongVal() {
    assertThat(renderer.renderStructured((short) 7)).isEqualTo(new LongVal(7L));
  }

  @Test
  void byteRendersAsLongVal() {
    assertThat(renderer.renderStructured((byte) 3)).isEqualTo(new LongVal(3L));
  }

  @Test
  void doubleRendersAsDoubleVal() {
    assertThat(renderer.renderStructured(3.14)).isEqualTo(new DoubleVal(3.14));
  }

  @Test
  void floatRendersAsDoubleVal() {
    var result = renderer.renderStructured(2.5f);
    assertThat(result).isInstanceOf(DoubleVal.class);
    assertThat(((DoubleVal) result).value())
        .isEqualTo(2.5, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void bigDecimalRendersAsDoubleVal() {
    assertThat(renderer.renderStructured(new BigDecimal("129.99")))
        .isEqualTo(new DoubleVal(129.99));
  }

  @Test
  void booleanRendersAsBooleanVal() {
    assertThat(renderer.renderStructured(true)).isEqualTo(new BooleanVal(true));
    assertThat(renderer.renderStructured(false)).isEqualTo(new BooleanVal(false));
  }

  enum Color {
    RED,
    GREEN
  }

  @Test
  void enumRendersAsStringVal() {
    assertThat(renderer.renderStructured(Color.RED)).isEqualTo(new StringVal("RED"));
  }

  enum HostileColor {
    RED {
      @Override
      public String toString() {
        return "1\n## forged\n";
      }
    }
  }

  @Test
  void hostileEnumToStringIsSanitizedInStructuredOutput() {
    // Enum.toString() is overridable, so the structured path must not trust it unsanitized
    // either — parity with the flat renderer's ScalarTrust decision.
    var rendered = (StringVal) renderer.renderStructured(HostileColor.RED);

    assertThat(rendered.value()).doesNotContain("\n");
    assertThat(rendered.value()).isEqualTo("1\\n## forged\\n");
  }

  @Test
  void characterRendersAsStringVal() {
    assertThat(renderer.renderStructured('X')).isEqualTo(new StringVal("X"));
  }

  /** A third-party {@code Number} — the structured path must not trust its own toString(). */
  static class HostileAmount extends Number {
    @Override
    public String toString() {
      return "1\n## forged\n";
    }

    @Override
    public int intValue() {
      return 1;
    }

    @Override
    public long longValue() {
      return 1L;
    }

    @Override
    public float floatValue() {
      return 1f;
    }

    @Override
    public double doubleValue() {
      return 1d;
    }
  }

  @Test
  void hostileNumberSubclassIsSanitizedInStructuredOutput() {
    var rendered = (StringVal) renderer.renderStructured(new HostileAmount());

    assertThat(rendered.value()).doesNotContain("\n");
    assertThat(rendered.value()).isEqualTo("1\\n## forged\\n");
  }

  @Test
  void instantRendersAsInstantValWithCorrectEpochMillis() {
    var instant = Instant.ofEpochMilli(1742134981123L);
    assertThat(renderer.renderStructured(instant)).isEqualTo(new InstantVal(1742134981123L));
  }

  @Test
  void javaUtilDateRendersAsInstantVal() {
    var date = new Date(1742134981123L);
    assertThat(renderer.renderStructured(date)).isEqualTo(new InstantVal(1742134981123L));
  }

  @Test
  void localDateTimeRendersAsInstantValInUtc() {
    var ldt = LocalDateTime.of(2025, 3, 16, 12, 0, 0);
    var expected = ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
    assertThat(renderer.renderStructured(ldt)).isEqualTo(new InstantVal(expected));
  }

  @Test
  void zonedDateTimeRendersAsInstantVal() {
    var zdt = ZonedDateTime.of(2025, 3, 16, 12, 0, 0, 0, ZoneOffset.ofHours(5));
    var expected = zdt.toInstant().toEpochMilli();
    assertThat(renderer.renderStructured(zdt)).isEqualTo(new InstantVal(expected));
  }

  @Test
  void offsetDateTimeRendersAsInstantVal() {
    var odt = java.time.OffsetDateTime.of(2025, 3, 16, 12, 0, 0, 0, ZoneOffset.ofHours(-3));
    var expected = odt.toInstant().toEpochMilli();
    assertThat(renderer.renderStructured(odt)).isEqualTo(new InstantVal(expected));
  }

  record SimpleRecord(String id, int count) {}

  @Test
  void recordRendersAsObjectValWithTypedFields() {
    var result = renderer.renderStructured(new SimpleRecord("X", 42));

    assertThat(result).isInstanceOf(ObjectVal.class);
    var obj = (ObjectVal) result;
    assertThat(obj.typeName()).isEqualTo("SimpleRecord");
    assertThat(obj.fields()).containsEntry("id", new StringVal("X"));
    assertThat(obj.fields()).containsEntry("count", new LongVal(42L));
  }

  record OrderWithTimestamp(String id, double total, Instant createdAt) {}

  @Test
  void recordWithInstantFieldRendersDateAsInstantVal() {
    var instant = Instant.ofEpochMilli(1742134981123L);
    var result = renderer.renderStructured(new OrderWithTimestamp("X", 129.99, instant));

    var obj = (ObjectVal) result;
    assertThat(obj.fields().get("createdAt")).isEqualTo(new InstantVal(1742134981123L));
    assertThat(obj.fields().get("total")).isEqualTo(new DoubleVal(129.99));
    assertThat(obj.fields().get("id")).isEqualTo(new StringVal("X"));
  }

  @SuppressWarnings("unused")
  static class LegacyPojo {
    String name = "Alice";
    int age = 30;
  }

  @Test
  void pojoRendersAsObjectValWithTypedFields() {
    var result = renderer.renderStructured(new LegacyPojo());

    assertThat(result).isInstanceOf(ObjectVal.class);
    var obj = (ObjectVal) result;
    assertThat(obj.typeName()).isEqualTo("LegacyPojo");
    assertThat(obj.fields()).containsEntry("name", new StringVal("Alice"));
    assertThat(obj.fields()).containsEntry("age", new LongVal(30L));
  }

  @SuppressWarnings("unused")
  static class Address {
    String city = "NYC";
  }

  @SuppressWarnings("unused")
  static class Customer {
    String name = "Bob";
    Address address = new Address();
  }

  @Test
  void nestedObjectRendersAsNestedObjectVal() {
    var result = renderer.renderStructured(new Customer());

    var customer = (ObjectVal) result;
    assertThat(customer.fields().get("name")).isEqualTo(new StringVal("Bob"));
    var address = (ObjectVal) customer.fields().get("address");
    assertThat(address.typeName()).isEqualTo("Address");
    assertThat(address.fields()).containsEntry("city", new StringVal("NYC"));
  }

  @Test
  void collectionRendersAsListVal() {
    var result = renderer.renderStructured(List.of(1, 2, 3));

    assertThat(result).isInstanceOf(ListVal.class);
    var list = (ListVal) result;
    assertThat(list.elements()).containsExactly(new LongVal(1), new LongVal(2), new LongVal(3));
  }

  @Test
  void arrayRendersAsListVal() {
    var result = renderer.renderStructured(new int[] {10, 20});

    assertThat(result).isInstanceOf(ListVal.class);
    var list = (ListVal) result;
    assertThat(list.elements()).containsExactly(new LongVal(10), new LongVal(20));
  }

  @Test
  void collectionRespectsMaxItems() {
    var customRenderer = new ValueRenderer(200, 2, 5);
    var result = customRenderer.renderStructured(List.of(1, 2, 3, 4));

    var list = (ListVal) result;
    assertThat(list.elements()).hasSize(2);
    assertThat(list.elements()).containsExactly(new LongVal(1), new LongVal(2));
  }

  @SuppressWarnings("unused")
  static class SelfRef {
    String name = "root";
    SelfRef self;
  }

  @Test
  void cycleDetectionRendersAsStringVal() {
    var obj = new SelfRef();
    obj.self = obj;
    var result = renderer.renderStructured(obj);

    var objVal = (ObjectVal) result;
    assertThat(objVal.fields().get("self")).isInstanceOf(StringVal.class);
    var selfStr = ((StringVal) objVal.fields().get("self")).value();
    assertThat(selfStr).startsWith("<SelfRef@");
  }

  static class WithCustomToString {
    @Override
    public String toString() {
      return "custom-value";
    }
  }

  @Test
  void customToStringRendersAsStringVal() {
    assertThat(renderer.renderStructured(new WithCustomToString()))
        .isEqualTo(new StringVal("custom-value"));
  }

  @Test
  void pendingFutureRendersAsStringVal() {
    var future = new CompletableFuture<String>();
    assertThat(renderer.renderStructured(future)).isEqualTo(new StringVal("<pending>"));
  }

  @Test
  void completedFutureRendersResolvedValue() {
    var future = CompletableFuture.completedFuture(42);
    assertThat(renderer.renderStructured(future)).isEqualTo(new LongVal(42L));
  }

  @Test
  void cancelledFutureRendersAsCancelled() {
    var future = new CompletableFuture<String>();
    future.cancel(true);
    assertThat(renderer.renderStructured(future)).isEqualTo(new StringVal("<cancelled>"));
  }

  @Test
  void failedFutureRendersAsFailed() {
    var future = new CompletableFuture<String>();
    future.completeExceptionally(new RuntimeException("boom"));
    assertThat(renderer.renderStructured(future)).isEqualTo(new StringVal("<failed>"));
  }

  @Test
  void objectArrayRendersAsListVal() {
    var result = renderer.renderStructured(new String[] {"a", "b"});

    var list = (ListVal) result;
    assertThat(list.elements()).containsExactly(new StringVal("a"), new StringVal("b"));
  }

  @Test
  void emptyCollectionRendersAsEmptyListVal() {
    var result = renderer.renderStructured(List.of());

    assertThat(result).isEqualTo(new ListVal(List.of()));
  }

  @Test
  void completedFutureWithNullRendersAsNullVal() {
    var future = CompletableFuture.completedFuture(null);
    assertThat(renderer.renderStructured(future)).isEqualTo(new NullVal());
  }

  record BrokenRecord(String name) {
    @Override
    public String name() {
      throw new RuntimeException("broken accessor");
    }
  }

  @Test
  void recordWithBrokenAccessorRendersErrorField() {
    var result = renderer.renderStructured(new BrokenRecord("test"));

    var obj = (ObjectVal) result;
    assertThat(obj.fields()).containsEntry("name", new StringVal("<error: RuntimeException>"));
  }

  record SummarizedOrder(String id) {
    @NarrativeSummary
    public String toNarrativeSummary() {
      return "OrderSummary[" + id + "]";
    }
  }

  @Test
  void narrativeSummaryUsedByRenderStructured() {
    assertThat(renderer.renderStructured(new SummarizedOrder("ORD-42")))
        .isEqualTo(new StringVal("OrderSummary[ORD-42]"));
  }

  @Test
  void selfReferentialCollectionDoesNotCauseStackOverflow() {
    var list = new ArrayList<>();
    list.add("root");
    list.add(list);

    assertThatCode(() -> renderer.renderStructured(list)).doesNotThrowAnyException();
    var rendered = (ListVal) renderer.renderStructured(list);
    assertThat(rendered.elements().get(1)).isInstanceOf(StringVal.class);
    assertThat(((StringVal) rendered.elements().get(1)).value()).contains("<ArrayList@");
  }

  @Test
  void selfReferentialArrayDoesNotCauseStackOverflow() {
    var array = new Object[2];
    array[0] = "root";
    array[1] = array;

    assertThatCode(() -> renderer.renderStructured(array)).doesNotThrowAnyException();
    var rendered = (ListVal) renderer.renderStructured(array);
    assertThat(rendered.elements().get(1)).isInstanceOf(StringVal.class);
    assertThat(((StringVal) rendered.elements().get(1)).value()).contains("<Object[]@");
  }

  @Test
  void moduleEncapsulatedFieldsRenderAsError() {
    var cleaner = java.lang.ref.Cleaner.create();
    var result = renderer.renderStructured(cleaner);

    assertThat(result).isInstanceOf(ObjectVal.class);
    var obj = (ObjectVal) result;
    assertThat(obj.fields().values()).allMatch(v -> v instanceof StringVal);
  }
}
