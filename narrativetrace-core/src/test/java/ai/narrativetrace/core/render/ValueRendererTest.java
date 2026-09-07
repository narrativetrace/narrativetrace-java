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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ValueRendererTest {

  private final ValueRenderer renderer = new ValueRenderer();

  static class Order {
    private final String orderId;
    private final int itemCount;

    Order(String orderId, int itemCount) {
      this.orderId = orderId;
      this.itemCount = itemCount;
    }

    @NarrativeSummary
    public String toNarrativeSummary() {
      return "Order(" + orderId + ", " + itemCount + " items)";
    }

    @Override
    public String toString() {
      return "Order{orderId=" + orderId + ", itemCount=" + itemCount + "}";
    }
  }

  @Test
  void usesNarrativeSummaryMethodWhenPresent() {
    var order = new Order("ORD-1", 3);
    var result = renderer.render(order);

    assertThat(result).isEqualTo("Order(ORD-1, 3 items)");
  }

  @Test
  void fallsBackToToStringWhenNoNarrativeSummary() {
    var result = renderer.render(42);
    assertThat(result).isEqualTo("42");
  }

  static class Account {
    final String username = "jsmith";
    final String password = "hunter2";
  }

  @Test
  void redactsDenyListedFieldNamesInStringObjectOutput() {
    // Parity with the structured path: the flat-string introspection must redact by name too.
    var result = renderer.render(new Account());

    assertThat(result).contains("username: \"jsmith\"");
    assertThat(result).contains("password: [REDACTED]");
    assertThat(result).doesNotContain("hunter2");
  }

  record Card(String number, String cvv) {}

  @Test
  void redactsDenyListedRecordComponentsInStringOutput() {
    var result = renderer.render(new Card("4111", "123"));

    assertThat(result).contains("number: \"4111\"");
    assertThat(result).contains("cvv: [REDACTED]");
    assertThat(result).doesNotContain("\"123\"");
  }

  static class Profile {
    final String bio = "hi";
    @NotTraced final String note = "cust-secret";
  }

  @Test
  void redactsFieldsAnnotatedNotTracedInStringOutput() {
    var result = renderer.render(new Profile());

    assertThat(result).contains("bio: \"hi\"");
    assertThat(result).contains("note: [REDACTED]");
    assertThat(result).doesNotContain("cust-secret");
  }

  record Session(String id, @NotTraced String data) {}

  @Test
  void redactsRecordComponentsAnnotatedNotTracedInStringOutput() {
    var result = renderer.render(new Session("s1", "secret-data"));

    assertThat(result).contains("id: \"s1\"");
    assertThat(result).contains("data: [REDACTED]");
    assertThat(result).doesNotContain("secret-data");
  }

  static class ChattyProfile {
    final String bio = "hi";
    @NotTraced final String note = "toString-secret";

    @Override
    public String toString() {
      return "ChattyProfile{bio=" + bio + ", note=" + note + "}";
    }
  }

  static class SecretHolder {
    @NotTraced final String token = "inherited-secret";
  }

  static class LoudHolder extends SecretHolder {
    final String label = "held";

    @Override
    public String toString() {
      return "LoudHolder{label=" + label + ", token=" + token + "}";
    }
  }

  @Test
  void aSubclassToStringDoesNotBypassAnInheritedAnnotatedFieldsRedaction() {
    // The annotation is declared one class up, so a detector that looked only at the runtime
    // class's declared fields would trust this toString() and print the secret it prints.
    var result = renderer.render(new LoudHolder());

    assertThat(result).doesNotContain("inherited-secret");
    assertThat(result).contains("label: \"held\"");
  }

  @Test
  void aCustomToStringDoesNotBypassAnAnnotatedFieldsRedaction() {
    // The seam this defect class hides in: a class that prints its own secret. toString() is
    // written for humans and debuggers, not for a trace, so a declared @NotTraced field outranks
    // it — the renderer introspects instead of trusting the class.
    var result = renderer.render(new ChattyProfile());

    assertThat(result).doesNotContain("toString-secret");
    assertThat(result).contains("note: [REDACTED]");
    assertThat(result).contains("bio: \"hi\"");
  }

  @Test
  void mapRedactsValuesForDenyListedKeysInsteadOfDumpingViaToString() {
    var headers = new java.util.LinkedHashMap<String, String>();
    headers.put("Accept", "application/json");
    headers.put("Authorization", "Bearer secret-token-xyz");

    var result = renderer.render(headers);

    assertThat(result).contains("Accept=\"application/json\"");
    assertThat(result).contains("Authorization=[REDACTED]");
    assertThat(result).doesNotContain("secret-token-xyz");
  }

  @Test
  void mapKeysRenderThroughRedactionInsteadOfToString() {
    var balances = new java.util.LinkedHashMap<Session, Integer>();
    balances.put(new Session("s1", "secret-data"), 42);

    var result = renderer.render(balances);

    assertThat(result).contains("Session(id: \"s1\", data: [REDACTED])=42");
    assertThat(result).doesNotContain("secret-data");
  }

  @Test
  void mapStringKeysAreSanitizedAgainstControlCharacters() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    map.put("a\nb", 1);

    var result = renderer.render(map);

    assertThat(result).contains("a\\nb=1");
    assertThat(result).doesNotContain("\n");
  }

  @Test
  void mapStringKeysAreTruncatedAtTheStringLimit() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    map.put("k".repeat(500), 1);

    var result = renderer.render(map);

    assertThat(result).contains("k".repeat(200) + "…=1");
    assertThat(result).doesNotContain("k".repeat(201));
  }

  static class ExplodingKey {
    @Override
    public String toString() {
      throw new IllegalStateException("boom");
    }
  }

  @Test
  void mapKeyWithThrowingToStringRendersPlaceholderInsteadOfFailingTheTracedCall() {
    var map = new java.util.LinkedHashMap<Object, Integer>();
    map.put(new ExplodingKey(), 1);

    var result = renderer.render(map);

    assertThat(result).contains("<ExplodingKey>=1");
  }

  @Test
  void mapKeyedByItselfCollapsesToIdentityMarkerWithoutInfiniteRecursion() {
    var map = new java.util.IdentityHashMap<Object, String>();
    map.put(map, "v");

    var result = renderer.render(map);

    assertThat(result).contains("<IdentityHashMap@");
    assertThat(result).contains("=\"v\"");
  }

  @Test
  void mapCapsEntriesAtCollectionLimitInsteadOfDumpingEveryEntry() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    for (int i = 0; i < 10; i++) {
      map.put("k" + i, i);
    }

    var result = renderer.render(map);

    assertThat(result).contains("k0=0");
    assertThat(result).contains("k4=4"); // exactly at the default cap of 5 (0..4)
    assertThat(result).doesNotContain("k5=5"); // first entry beyond the cap
    assertThat(result).contains("…");
  }

  static class Chatty {
    @Override
    public String toString() {
      return "x".repeat(500);
    }
  }

  @Test
  void capsCustomToStringOutputAtTheStringLimit() {
    var result = renderer.render(new Chatty());

    assertThat(result).hasSize(201); // 200 chars + the … marker
    assertThat(result).endsWith("…");
  }

  static class Noisy {
    @Override
    public String toString() {
      return "a\nb";
    }
  }

  @Test
  void sanitizesControlCharactersOnTheToStringPath() {
    var result = renderer.render(new Noisy());

    assertThat(result).isEqualTo("a\\nb");
    assertThat(result).doesNotContain("\n");
  }

  @Test
  void selfReferentialMapCollapsesToIdentityMarkerWithoutInfiniteRecursion() {
    var map = new java.util.LinkedHashMap<String, Object>();
    map.put("self", map);

    var result = renderer.render(map);

    // The self-reference must collapse to the identity marker (not an empty value, not a
    // recursion).
    assertThat(result).contains("self=<LinkedHashMap@");
    assertThat(result).doesNotContain("StackOverflow");
  }

  @Test
  void mapWithExactlyLimitEntriesHasNoTruncationMarker() {
    var map = new java.util.LinkedHashMap<String, Integer>();
    for (int i = 0; i < 5; i++) {
      map.put("k" + i, i);
    }

    var result = renderer.render(map);

    assertThat(result).contains("k4=4");
    assertThat(result).doesNotContain("…");
  }

  static class NullToString {
    @Override
    public String toString() {
      return null;
    }
  }

  @Test
  void objectWithNullToStringRendersClassNameMarker() {
    assertThat(renderer.render(new NullToString())).isEqualTo("<NullToString>");
  }

  static class ExactlyAtLimit {
    @Override
    public String toString() {
      return "y".repeat(200);
    }
  }

  @Test
  void toStringExactlyAtTheLimitIsNotTruncated() {
    var result = renderer.render(new ExactlyAtLimit());

    assertThat(result).hasSize(200);
    assertThat(result).doesNotContain("…");
  }

  /** A third-party {@code Number} — any caller can extend the abstract class. */
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
  void hostileNumberSubclassToStringIsSanitizedNotEmittedRaw() {
    // A Number subclass's toString() is application code, not a JDK-fixed format: the same
    // control-character sanitizing a String gets must apply, or a forged line break/Markdown
    // structure reaches narrative text unescaped.
    var result = renderer.render(new HostileAmount());

    assertThat(result).doesNotContain("\n");
    assertThat(result).isEqualTo("1\\n## forged\\n");
  }

  enum HostileEnum {
    VALUE {
      @Override
      public String toString() {
        return "1\n## forged\n";
      }
    }
  }

  @Test
  void hostileEnumToStringIsSanitizedNotEmittedRaw() {
    // Enum.toString() is overridable per-constant (a constant body), so it is application code
    // too, not a JDK-fixed format.
    var result = renderer.render(HostileEnum.VALUE);

    assertThat(result).doesNotContain("\n");
    assertThat(result).isEqualTo("1\\n## forged\\n");
  }

  @Test
  void jdkBoxedNumericsStillRenderRawUnsanitized() {
    // The fast path must stay fast for the types whose toString() the platform controls.
    assertThat(renderer.render(Integer.valueOf(42))).isEqualTo("42");
    assertThat(renderer.render(Long.valueOf(42L))).isEqualTo("42");
    assertThat(renderer.render(Short.valueOf((short) 42))).isEqualTo("42");
    assertThat(renderer.render(Byte.valueOf((byte) 42))).isEqualTo("42");
    assertThat(renderer.render(Double.valueOf(4.2))).isEqualTo("4.2");
    assertThat(renderer.render(Float.valueOf(4.2f))).isEqualTo("4.2");
    assertThat(renderer.render(BigInteger.valueOf(42))).isEqualTo("42");
    assertThat(renderer.render(new BigDecimal("4.20"))).isEqualTo("4.20");
  }

  /** A subclass of a non-final JDK numeric type — the hole the exact-class match closes. */
  static class HostileBigDecimal extends BigDecimal {
    HostileBigDecimal() {
      super(0);
    }

    @Override
    public String toString() {
      return "1\n## forged\n";
    }
  }

  @Test
  void hostileBigDecimalSubclassIsSanitizedByExactClassNotInstanceof() {
    var result = renderer.render(new HostileBigDecimal());

    assertThat(result).doesNotContain("\n");
    assertThat(result).isEqualTo("1\\n## forged\\n");
  }

  @Test
  void escapesControlCharactersInStringValuesToPreventLogForging() {
    // A raw newline in a value would forge a second log line and break the console line. Rendered
    // values must never carry control characters into any sink.
    var result = renderer.render("ORD-1\nINFO forged-line");

    assertThat(result).doesNotContain("\n");
    assertThat(result).isEqualTo("\"ORD-1\\nINFO forged-line\"");
  }

  @Test
  void truncatesLongStringsAtConfiguredLimit() {
    var longString = "a".repeat(250);
    var result = renderer.render(longString);

    assertThat(result).hasSize(203); // 1 quote + 200 chars + … + 1 quote
    assertThat(result).startsWith("\"");
    assertThat(result).endsWith("…\"");
  }

  @Test
  void customStringLimitIsRespected() {
    var customRenderer = new ValueRenderer(10, 5, 5);
    var result = customRenderer.render("a".repeat(20));

    assertThat(result).hasSize(13); // 1 quote + 10 chars + … + 1 quote
    assertThat(result).endsWith("…\"");
  }

  @Test
  void limitsCollectionsToConfiguredMax() {
    var list = List.of("a", "b", "c", "d", "e", "f", "g");
    var result = renderer.render(list);

    assertThat(result).isEqualTo("[\"a\", \"b\", \"c\", \"d\", \"e\", … (7 total)]");
  }

  @Test
  void customCollectionLimitIsRespected() {
    var customRenderer = new ValueRenderer(200, 2, 5);
    var list = List.of("a", "b", "c", "d");
    var result = customRenderer.render(list);

    assertThat(result).isEqualTo("[\"a\", \"b\", … (4 total)]");
  }

  @Test
  void rendersSmallCollectionsFully() {
    var list = List.of("a", "b");
    var result = renderer.render(list);

    assertThat(result).isEqualTo("[\"a\", \"b\"]");
  }

  record LargeRecord(String a, String b, String c, String d, String e, String f) {}

  @Test
  void limitsRecordFieldsToConfiguredMax() {
    var record = new LargeRecord("1", "2", "3", "4", "5", "6");
    var result = renderer.render(record);

    assertThat(result)
        .isEqualTo("LargeRecord(a: \"1\", b: \"2\", c: \"3\", d: \"4\", e: \"5\", …)");
  }

  @Test
  void customObjectFieldLimitIsRespected() {
    var customRenderer = new ValueRenderer(200, 5, 2);
    var record = new LargeRecord("1", "2", "3", "4", "5", "6");
    var result = customRenderer.render(record);

    assertThat(result).isEqualTo("LargeRecord(a: \"1\", b: \"2\", …)");
  }

  record SmallRecord(String name, int count) {}

  @Test
  void rendersSmallRecordFully() {
    var record = new SmallRecord("test", 42);
    var result = renderer.render(record);

    assertThat(result).isEqualTo("SmallRecord(name: \"test\", count: 42)");
  }

  @Test
  void rendersNullAsNull() {
    assertThat(renderer.render(null)).isEqualTo("null");
  }

  static class FailingSummary {
    @NarrativeSummary
    public String toNarrativeSummary() {
      throw new RuntimeException("oops");
    }

    @Override
    public String toString() {
      return "FailingSummary{}";
    }
  }

  @Test
  void narrativeSummaryThatThrowsFallsBackToToString() {
    var result = renderer.render(new FailingSummary());
    assertThat(result).isEqualTo("FailingSummary{}");
  }

  static class HasSummaryWithParams {
    @NarrativeSummary
    public String toNarrativeSummary(String format) {
      return "formatted";
    }

    @Override
    public String toString() {
      return "HasSummaryWithParams{}";
    }
  }

  record BrokenRecord(String name) {
    @Override
    public String name() {
      throw new RuntimeException("broken accessor");
    }
  }

  @Test
  void recordWithFailingAccessorRendersErrorFallback() {
    var result = renderer.render(new BrokenRecord("test"));

    assertThat(result).isEqualTo("BrokenRecord(name: <error>)");
  }

  @Test
  void narrativeSummaryWithParametersIsIgnored() {
    var result = renderer.render(new HasSummaryWithParams());
    assertThat(result).isEqualTo("HasSummaryWithParams{}");
  }

  @Test
  void rendersPrimitiveArray() {
    assertThat(renderer.render(new int[] {1, 2, 3})).isEqualTo("[1, 2, 3]");
  }

  @Test
  void rendersObjectArray() {
    assertThat(renderer.render(new String[] {"a", "b"})).isEqualTo("[\"a\", \"b\"]");
  }

  @Test
  void truncatesLargeArray() {
    var arr = new int[] {1, 2, 3, 4, 5, 6, 7};
    assertThat(renderer.render(arr)).isEqualTo("[1, 2, 3, 4, 5, ... (7 total)]");
  }

  @SuppressWarnings("unused")
  static class LegacyPojo {
    String name = "Alice";
    int age = 30;
  }

  @Test
  void rendersLegacyPojoWithReflectiveIntrospection() {
    assertThat(renderer.render(new LegacyPojo())).isEqualTo("LegacyPojo{name: \"Alice\", age: 30}");
  }

  static class WithCustomToString {
    @SuppressWarnings("unused")
    String name = "ignored";

    @Override
    public String toString() {
      return "custom";
    }
  }

  @Test
  void objectWithCustomToStringUsesToString() {
    assertThat(renderer.render(new WithCustomToString())).isEqualTo("custom");
  }

  @SuppressWarnings("unused")
  static class ManyFields {
    String a = "1";
    String b = "2";
    String c = "3";
    String d = "4";
    String e = "5";
    String f = "6";
  }

  @Test
  void objectFieldTruncationRespectsMaxObjectFields() {
    assertThat(renderer.render(new ManyFields())).endsWith(", ...}");
  }

  @SuppressWarnings("unused")
  static class WithStaticField {
    static int COUNT = 0;
    String name = "test";
  }

  @Test
  void staticFieldsExcludedFromIntrospection() {
    assertThat(renderer.render(new WithStaticField())).isEqualTo("WithStaticField{name: \"test\"}");
  }

  static class ThrowingToString {
    @Override
    public String toString() {
      throw new RuntimeException("boom");
    }
  }

  @Test
  void toStringFailureRendersClassName() {
    assertThat(renderer.render(new ThrowingToString())).isEqualTo("<ThrowingToString>");
  }

  @SuppressWarnings("unused")
  static class SelfRef {
    String name = "root";
    SelfRef self;
  }

  @Test
  void cycleDetectionForReflectiveIntrospection() {
    var obj = new SelfRef();
    obj.self = obj;
    assertThat(renderer.render(obj)).contains("self: <SelfRef@");
  }

  @SuppressWarnings("unused")
  static class FieldWithThrowingGetter {
    String name = "ok";
    Object broken =
        new Object() {
          @Override
          public String toString() {
            throw new RuntimeException("field boom");
          }
        };
  }

  @Test
  void fieldWithThrowingToStringRendersClassName() {
    var result = renderer.render(new FieldWithThrowingGetter());
    assertThat(result).startsWith("FieldWithThrowingGetter{name: \"ok\"");
  }

  @Test
  void objectWithModuleEncapsulatedFieldsRendersGracefully() {
    // Cleaner is in java.base/java.lang.ref which is not opened for
    // reflective access. It has no custom toString(), so renderObject
    // is reached and field.setAccessible(true) throws
    // InaccessibleObjectException. This must not crash — fields should
    // render as <error>.
    var cleaner = java.lang.ref.Cleaner.create();
    var result = renderer.render(cleaner);

    assertThat(result).startsWith("Cleaner{");
    assertThat(result).contains("<error>");
    assertThat(result).endsWith("}");
  }

  @Test
  void stringAtExactMaxLengthIsNotTruncated() {
    var exactLength = "x".repeat(200);
    var result = renderer.render(exactLength);

    assertThat(result).isEqualTo("\"" + exactLength + "\"");
    assertThat(result).doesNotContain("\u2026");
  }

  @Test
  void stringOneOverMaxLengthIsTruncated() {
    var overLength = "x".repeat(201);
    var result = renderer.render(overLength);

    assertThat(result).endsWith("\u2026\"");
    assertThat(result).hasSize(203); // quote + 200 chars + ellipsis + quote
  }

  @Test
  void collectionAtExactMaxSizeIsNotTruncated() {
    var list = List.of("a", "b", "c", "d", "e");
    var result = renderer.render(list);

    assertThat(result).isEqualTo("[\"a\", \"b\", \"c\", \"d\", \"e\"]");
    assertThat(result).doesNotContain("total");
  }

  @Test
  void arrayAtExactMaxSizeIsNotTruncated() {
    var arr = new int[] {1, 2, 3, 4, 5};
    var result = renderer.render(arr);

    assertThat(result).isEqualTo("[1, 2, 3, 4, 5]");
    assertThat(result).doesNotContain("total");
  }

  record FiveFields(String a, String b, String c, String d, String e) {}

  @Test
  void recordAtExactMaxFieldCountIsNotTruncated() {
    var record = new FiveFields("1", "2", "3", "4", "5");
    var result = renderer.render(record);

    assertThat(result).isEqualTo("FiveFields(a: \"1\", b: \"2\", c: \"3\", d: \"4\", e: \"5\")");
    assertThat(result).doesNotContain("\u2026");
  }

  @SuppressWarnings("unused")
  static class FiveFieldsPojo {
    String a = "1";
    String b = "2";
    String c = "3";
    String d = "4";
    String e = "5";
  }

  @Test
  void objectAtExactMaxFieldCountIsNotTruncated() {
    var result = renderer.render(new FiveFieldsPojo());

    assertThat(result)
        .isEqualTo("FiveFieldsPojo{a: \"1\", b: \"2\", c: \"3\", d: \"4\", e: \"5\"}");
    assertThat(result).doesNotContain("...");
  }

  @SuppressWarnings("unused")
  static class SharedChild {
    String value = "shared";
  }

  @SuppressWarnings("unused")
  static class TwoFieldsSameRef {
    SharedChild first;
    SharedChild second;

    TwoFieldsSameRef(SharedChild shared) {
      this.first = shared;
      this.second = shared;
    }
  }

  @Test
  void sharedReferenceInSiblingFieldsIsNotFlaggedAsCircular() {
    var shared = new SharedChild();
    var parent = new TwoFieldsSameRef(shared);
    var result = renderer.render(parent);

    // Both fields should render fully — shared reference is not a cycle
    assertThat(result).doesNotContain("<SharedChild@");
    assertThat(result)
        .isEqualTo(
            "TwoFieldsSameRef{first: SharedChild{value: \"shared\"}, "
                + "second: SharedChild{value: \"shared\"}}");
  }

  @Test
  void rendersIncompleteCompletableFutureAsPending() {
    var future = new CompletableFuture<String>();
    assertThat(renderer.render(future)).isEqualTo("<pending>");
  }

  @Test
  void rendersCompletedCompletableFutureWithResolvedValue() {
    var future = CompletableFuture.completedFuture("order-42");
    assertThat(renderer.render(future)).isEqualTo("\"order-42\"");
  }

  @Test
  void rendersCompletedCompletableFutureWithIntegerValue() {
    var future = CompletableFuture.completedFuture(42);
    assertThat(renderer.render(future)).isEqualTo("42");
  }

  @Test
  void rendersCompletedCompletableFutureWithNullValue() {
    var future = CompletableFuture.completedFuture(null);
    assertThat(renderer.render(future)).isEqualTo("null");
  }

  @Test
  void rendersCancelledCompletableFutureAsCancelled() {
    var future = new CompletableFuture<String>();
    future.cancel(true);
    assertThat(renderer.render(future)).isEqualTo("<cancelled>");
  }

  @Test
  void rendersExceptionallyCompletedFutureAsFailed() {
    var future = new CompletableFuture<String>();
    future.completeExceptionally(new RuntimeException("boom"));
    assertThat(renderer.render(future)).isEqualTo("<failed>");
  }

  @Test
  void rendersRawFutureAsPendingViaParentInterface() {
    java.util.concurrent.Future<String> future = new CompletableFuture<>();
    assertThat(renderer.render(future)).isEqualTo("<pending>");
  }

  @Test
  void rendersCompletedRecordInsideFuture() {
    var future = CompletableFuture.completedFuture(new SmallRecord("test", 42));
    assertThat(renderer.render(future)).isEqualTo("SmallRecord(name: \"test\", count: 42)");
  }

  @Test
  void selfReferentialCollectionDoesNotCauseStackOverflow() {
    var list = new ArrayList<>();
    list.add("root");
    list.add(list);

    assertThatCode(() -> renderer.render(list)).doesNotThrowAnyException();
    assertThat(renderer.render(list)).contains("<ArrayList@");
  }

  @Test
  void selfReferentialArrayDoesNotCauseStackOverflow() {
    var array = new Object[2];
    array[0] = "root";
    array[1] = array;

    assertThatCode(() -> renderer.render(array)).doesNotThrowAnyException();
    assertThat(renderer.render(array)).contains("<Object[]@");
  }
}
