/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.annotation.NotTraced;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateParserTest {

  @Test
  void resolvesSimpleParameterSubstitution() {
    var result =
        TemplateParser.resolve(
            "Placing order of {quantity} units for customer {customerId}",
            Map.of("quantity", 5, "customerId", "C-123"));

    assertThat(result).isEqualTo("Placing order of 5 units for customer C-123");
  }

  record Customer(String name, String tier) {}

  @Test
  void resolvesOneLevelPropertyAccess() {
    var result =
        TemplateParser.resolve(
            "Order for {customer.name} (tier: {customer.tier})",
            Map.of("customer", new Customer("Alice", "GOLD")));

    assertThat(result).isEqualTo("Order for Alice (tier: GOLD)");
  }

  @Test
  void leavesMissingParamAsIs() {
    var result =
        TemplateParser.resolve(
            "Order for {customerId} with {unknown}", Map.of("customerId", "C-123"));

    assertThat(result).isEqualTo("Order for C-123 with {unknown}");
  }

  @Test
  void missingObjectInDottedPlaceholderPreservesPlaceholder() {
    var result = TemplateParser.resolve("Hello {missing.name}", Map.of());

    assertThat(result).isEqualTo("Hello {missing.name}");
  }

  @Test
  void invalidPropertyFallsBackToPlaceholder() {
    var result =
        TemplateParser.resolve(
            "Value: {customer.nonexistent}", Map.of("customer", new Customer("Alice", "GOLD")));

    assertThat(result).isEqualTo("Value: {customer.nonexistent}");
  }

  @Test
  void nullPropertyValuePreservesPlaceholder() {
    var result =
        TemplateParser.resolve("Value: {customer.name}", Map.of("customer", new NullName()));

    assertThat(result).isEqualTo("Value: {customer.name}");
  }

  @Test
  void sameTemplateWithDifferentValuesProducesCorrectResults() {
    var template = "{item} costs {price}";

    var first = TemplateParser.resolve(template, Map.of("item", "Widget", "price", 9.99));
    var second = TemplateParser.resolve(template, Map.of("item", "Gadget", "price", 19.99));

    assertThat(first).isEqualTo("Widget costs 9.99");
    assertThat(second).isEqualTo("Gadget costs 19.99");
  }

  @Test
  void findUnresolvedInResultReturnsEmptyForNull() {
    assertThat(TemplateParser.findUnresolvedInResult(null)).isEmpty();
  }

  @Test
  void findUnresolvedInResultReturnsEmptyWhenNoPlaceholders() {
    assertThat(TemplateParser.findUnresolvedInResult("Order for Alice")).isEmpty();
  }

  @Test
  void findUnresolvedInResultFindsSurvivingPlaceholders() {
    assertThat(TemplateParser.findUnresolvedInResult("Order for {custmerId} with {order.stauts}"))
        .containsExactly("custmerId", "order.stauts");
  }

  @Test
  void propertyAccessorThrowingUncheckedExceptionPreservesPlaceholder() {
    var result =
        TemplateParser.resolve("Value: {obj.explode}", Map.of("obj", new ThrowsOnAccess()));

    assertThat(result).isEqualTo("Value: {obj.explode}");
  }

  @Test
  void parseProducesDottedPropertyPlaceholder() {
    var segments = TemplateParser.parse("{order.total}");

    assertThat(segments).hasSize(1);
    assertThat(segments.get(0)).isInstanceOf(TemplateParser.Segment.PropertyPlaceholder.class);
    var prop = (TemplateParser.Segment.PropertyPlaceholder) segments.get(0);
    assertThat(prop.objectKey()).isEqualTo("order");
    assertThat(prop.property()).isEqualTo("total");
  }

  @Test
  void parseProducesSimplePlaceholderForUndottedKey() {
    var segments = TemplateParser.parse("{name}");

    assertThat(segments).hasSize(1);
    assertThat(segments.get(0)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
    var simple = (TemplateParser.Segment.SimplePlaceholder) segments.get(0);
    assertThat(simple.key()).isEqualTo("name");
  }

  @Test
  void parsePreservesLiteralsBetweenAdjacentPlaceholders() {
    var segments = TemplateParser.parse("a{x}b{y}c");

    assertThat(segments).hasSize(5);
    assertThat(segments.get(0)).isInstanceOf(TemplateParser.Segment.Literal.class);
    assertThat(segments.get(1)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
    assertThat(segments.get(2)).isInstanceOf(TemplateParser.Segment.Literal.class);
    assertThat(((TemplateParser.Segment.Literal) segments.get(2)).text()).isEqualTo("b");
    assertThat(segments.get(3)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
    assertThat(segments.get(4)).isInstanceOf(TemplateParser.Segment.Literal.class);
    assertThat(((TemplateParser.Segment.Literal) segments.get(4)).text()).isEqualTo("c");
  }

  @Test
  void parseHandlesAdjacentPlaceholdersWithNoLiteralBetween() {
    var segments = TemplateParser.parse("{a}{b}");

    assertThat(segments).hasSize(2);
    assertThat(segments.get(0)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
    assertThat(segments.get(1)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
  }

  @Test
  void parsePreservesTrailingLiteral() {
    var segments = TemplateParser.parse("{x} end");

    assertThat(segments).hasSize(2);
    assertThat(segments.get(0)).isInstanceOf(TemplateParser.Segment.SimplePlaceholder.class);
    assertThat(segments.get(1)).isInstanceOf(TemplateParser.Segment.Literal.class);
    assertThat(((TemplateParser.Segment.Literal) segments.get(1)).text()).isEqualTo(" end");
  }

  static final class AccountView {
    private final String accountId;
    private final String ownerName;

    AccountView(String accountId, String ownerName) {
      this.accountId = accountId;
      this.ownerName = ownerName;
    }

    public String getAccountId() {
      return accountId;
    }

    public String getOwnerName() {
      return ownerName;
    }
  }

  @Test
  void resolvesJavaBeanGetterPropertyAccess() {
    var result =
        TemplateParser.resolve(
            "Transfer from {account.accountId} owned by {account.ownerName}",
            Map.of("account", new AccountView("ACC-42", "Alice")));

    assertThat(result).isEqualTo("Transfer from ACC-42 owned by Alice");
  }

  /** The dogfood shape: an author names a component the record declares as redacted. */
  record Card(String number, @NotTraced String cvv) {}

  record Order(String id, Card card) {}

  record Payment(String id, @NotTraced Card card) {}

  /** {@code clearance} is on no deny-list — only the annotation can redact it. */
  record Badge(String id, @NotTraced String clearance) {}

  /** Nothing is annotated here; the name-based policy is the only rule that applies. */
  static class Login {
    private final String username;
    private final String password;

    Login(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String username() {
      return username;
    }

    public String password() {
      return password;
    }
  }

  @Test
  void aTemplateNamingARedactedRecordComponentRendersTheMarker() {
    var result =
        TemplateParser.resolve("charging {card.cvv}", Map.of("card", new Card("4111", "123")));

    assertThat(result).isEqualTo("charging [REDACTED]").doesNotContain("123");
  }

  @Test
  void theAnnotationRedactsAPropertyNoDenyListPatternWouldRecognize() {
    var result =
        TemplateParser.resolve(
            "badge {badge.clearance}", Map.of("badge", new Badge("b-1", "TOP-SECRET")));

    assertThat(result).isEqualTo("badge [REDACTED]").doesNotContain("TOP-SECRET");
  }

  @Test
  void theDenyListRedactsAnUnannotatedPropertyByNameAlone() {
    var result =
        TemplateParser.resolve(
            "login {user.password}", Map.of("user", new Login("jsmith", "hunter2")));

    assertThat(result).isEqualTo("login [REDACTED]").doesNotContain("hunter2");
  }

  @Test
  void aRedactedSegmentAtTheEndOfANestedPathRendersTheMarker() {
    var order = new Order("o-1", new Card("4111", "123"));

    assertThat(TemplateParser.resolve("charging {order.card.cvv}", Map.of("order", order)))
        .isEqualTo("charging [REDACTED]")
        .doesNotContain("123");
  }

  @Test
  void aRedactedSegmentInTheMiddleOfAPathRendersTheMarkerForEverythingBelowIt() {
    var payment = new Payment("p-1", new Card("4111", "123"));

    assertThat(TemplateParser.resolve("charging {payment.card.number}", Map.of("payment", payment)))
        .isEqualTo("charging [REDACTED]")
        .doesNotContain("4111");
  }

  @Test
  void anUnredactedNestedPathStillSurvivesLiterallyBecauseNestedAccessIsUnsupported() {
    var order = new Order("o-1", new Card("4111", "123"));

    assertThat(TemplateParser.resolve("card {order.card.number}", Map.of("order", order)))
        .isEqualTo("card {order.card.number}");
  }

  /** A computed property: an accessor with no backing field, so only the name can redact it. */
  static class Session {
    public String sessionToken() {
      return "tok-9";
    }

    public String owner() {
      return "jsmith";
    }
  }

  @Test
  void aComputedPropertyWithNoBackingFieldIsStillRedactedByName() {
    var result =
        TemplateParser.resolve("session {session.sessionToken}", Map.of("session", new Session()));

    assertThat(result).isEqualTo("session [REDACTED]").doesNotContain("tok-9");
  }

  @Test
  void aComputedPropertyTheDenyListDoesNotMatchStillResolves() {
    var result =
        TemplateParser.resolve("session {session.owner}", Map.of("session", new Session()));

    assertThat(result).isEqualTo("session jsmith");
  }

  /** No accessor on purpose: only the inherited field can answer, so the walk must find it. */
  static class Credentials {
    final String apikey = "ak-1";
  }

  static class ServiceAccount extends Credentials {}

  @Test
  void anInheritedFieldIsFoundSoRedactionIsNotEscapedBySubclassing() {
    var result =
        TemplateParser.resolve("key {account.apikey}", Map.of("account", new ServiceAccount()));

    assertThat(result).isEqualTo("key [REDACTED]").doesNotContain("ak-1");
  }

  @Test
  void aPlaceholderNamingNoParameterStaysLiteralSoTheTypoWarningSurvives() {
    assertThat(TemplateParser.resolve("charging {custmer.cvv}", Map.of()))
        .as("no value exists, so nothing can leak — and the unresolved warning must still fire")
        .isEqualTo("charging {custmer.cvv}");
  }

  @Test
  void aPropertyTheOwnerDoesNotDeclareStaysLiteralEvenWhenItsNameSoundsSensitive() {
    assertThat(
            TemplateParser.resolve(
                "order {order.password}", Map.of("order", new Order("o-1", null))))
        .as("a name that matches no member is a typo, not a secret")
        .isEqualTo("order {order.password}");
  }

  static class NullName {
    public String name() {
      return null;
    }
  }

  static class ThrowsOnAccess {
    public String explode() {
      throw new RuntimeException("boom");
    }
  }

  /**
   * Regression: an empty path segment used to raise {@code StringIndexOutOfBoundsException} out of
   * the redaction walk, because the JavaBean branch built its getter name from {@code charAt(0)}. A
   * trailing or doubled dot is an ordinary authoring typo, and the placeholder grammar accepts one,
   * so it reached instrumentation and failed the call it was narrating. Found by the security
   * suite's hostile corpus (`trailing-dot`, `double-dot`, `leading-dot`, `dot-only`).
   */
  @Test
  void aTrailingDotInAPathResolvesToNothingInsteadOfThrowing() {
    assertThat(TemplateParser.resolve("charging {card.}", Map.of("card", new Card("4111", "123"))))
        .isEqualTo("charging {card.}");
  }

  @Test
  void aDoubledDotInAPathResolvesToNothingInsteadOfThrowing() {
    assertThat(
            TemplateParser.resolve("charging {card..cvv}", Map.of("card", new Card("4111", "123"))))
        .isEqualTo("charging {card..cvv}");
  }

  @Test
  void aLeadingDotInAPathResolvesToNothingInsteadOfThrowing() {
    assertThat(TemplateParser.resolve("charging {.cvv}", Map.of("card", new Card("4111", "123"))))
        .isEqualTo("charging {.cvv}");
  }

  @Test
  void aPathThatIsOnlyASeparatorResolvesToNothingInsteadOfThrowing() {
    assertThat(TemplateParser.resolve("charging {.}", Map.of("card", new Card("4111", "123"))))
        .isEqualTo("charging {.}");
  }

  /**
   * Regression: a placeholder naming the <em>object</em> rather than a path into it printed the
   * object's own {@code toString()}, which knows nothing about {@code @NotTraced}. One level up
   * from the path case the owner ruled on, and on the more common spelling of the two: {@code
   * {paramName}} is the first form the annotations guide documents. Found by the security suite's
   * generated templates.
   */
  @Test
  void anObjectPlaceholderRendersItsRedactedComponentAsTheMarker() {
    var resolved =
        TemplateParser.resolve("charging {card}", Map.of("card", new Card("4111", "123")));

    assertThat(resolved).contains("[REDACTED]").doesNotContain("123");
  }

  @Test
  void anObjectPlaceholderStillShowsWhatIsNotRedacted() {
    var resolved =
        TemplateParser.resolve("charging {card}", Map.of("card", new Card("4111", "123")));

    assertThat(resolved).contains("4111");
  }

  @Test
  void aNestedRedactedComponentIsHiddenTooWhenTheOuterObjectIsNamed() {
    var resolved =
        TemplateParser.resolve(
            "charging {order}", Map.of("order", new Order("o-1", new Card("4111", "123"))));

    assertThat(resolved).contains("[REDACTED]").doesNotContain("123");
  }

  /**
   * Superseded by the 2026-09-11 family invariant: a plain class that has state narrates
   * structurally too, because "nothing to hide" was never a fact the renderer could establish — it
   * only ever meant "no {@code @NotTraced} member here", which says nothing about a deny-listed
   * name or a nested holder the author's {@code toString()} interpolates. {@code @NarrativeSummary}
   * is how an author chooses the bytes, on a class exactly as on a record — see {@link
   * #aNarrativeSummaryChoosesTheBytesForARecord}.
   */
  @Test
  void aPlainClassNarratesStructurallyRatherThanThroughItsOwnToString() {
    var resolved =
        TemplateParser.resolve("transfer {amount}", Map.of("amount", new Amount("EUR", 10)));

    assertThat(resolved).isEqualTo("transfer Amount{currency: \"EUR\", units: 10}");
  }

  /**
   * Where the ruling stops: a <em>record</em> narrates structurally, because a record's generated
   * {@code toString()} prints every component including the ones annotated out of the trace. {@code
   * ValueRenderer} has always rendered records this way for traced arguments; the template path
   * used to disagree, so one value narrated two ways in one trace.
   */
  @Test
  void aRecordNarratesStructurallyRatherThanThroughItsOwnToString() {
    var resolved =
        TemplateParser.resolve("transfer {amount}", Map.of("amount", new Money("EUR", 10)));

    assertThat(resolved).isEqualTo("transfer Money(currency: \"EUR\", amount: 10)");
  }

  /** How an author chooses the bytes for a record: the supported annotation, honoured here too. */
  @Test
  void aNarrativeSummaryChoosesTheBytesForARecord() {
    var resolved =
        TemplateParser.resolve("transfer {amount}", Map.of("amount", new Priced("EUR", 10)));

    assertThat(resolved).isEqualTo("transfer EUR 10.00");
  }

  @Test
  void aStringPlaceholderIsStillUnquoted() {
    assertThat(TemplateParser.resolve("place {item}", Map.of("item", "widget")))
        .isEqualTo("place widget");
  }

  @Test
  void aNumberPlaceholderIsStillPlain() {
    assertThat(TemplateParser.resolve("quantity {n}", Map.of("n", 42))).isEqualTo("quantity 42");
  }

  @Test
  void anEnumPlaceholderIsStillItsName() {
    assertThat(
            TemplateParser.resolve("unit {u}", Map.of("u", java.util.concurrent.TimeUnit.SECONDS)))
        .isEqualTo("unit SECONDS");
  }

  /** A value object whose own {@code toString()} is the narration an author wants. */
  record Money(String currency, int amount) {
    @Override
    public String toString() {
      return currency + " " + amount + ".00";
    }
  }

  /** The same value as a class rather than a record: its own {@code toString()} stands. */
  static final class Amount {
    private final String currency;
    private final int units;

    Amount(String currency, int units) {
      this.currency = currency;
      this.units = units;
    }

    @Override
    public String toString() {
      return currency + " " + units + ".00";
    }
  }

  /** A record that names its own narration the supported way. */
  record Priced(String currency, int amount) {
    @NarrativeSummary
    public String summary() {
      return currency + " " + amount + ".00";
    }
  }

  /**
   * A record whose redacted component sits past {@code ValueRenderer}'s five-field cap: the safe
   * rendering truncates it away, so the safe rendering carries no marker at all.
   *
   * <p>The bug class this pins is the inference "no marker in the safe form" ⇒ "nothing is hidden,
   * the value's own toString() may stand". A truncated render has not seen the whole value, so it
   * cannot answer that question, and the record's own toString() prints every component.
   */
  record Wide(
      String one, String two, String three, String four, String five, @NotTraced String six) {}

  @Test
  void aRedactedComponentPastTheRenderersFieldCapIsNotNarratedByToString() {
    var resolved =
        TemplateParser.resolve(
            "audit {row}", Map.of("row", new Wide("1", "2", "3", "4", "5", "topsecret")));

    assertThat(resolved).doesNotContain("topsecret");
  }

  /** A chain longer than the renderer's depth cap, with a redacted leaf at the bottom. */
  record Node(Object next) {}

  @Test
  void aRedactedLeafDeeperThanTheRenderersDepthCapIsNotNarratedByToString() {
    Object chain = new Card("4111", "topsecret");
    for (var i = 0; i < 40; i++) {
      chain = new Node(chain);
    }

    var resolved = TemplateParser.resolve("audit {row}", Map.of("row", chain));

    assertThat(resolved).doesNotContain("topsecret");
  }

  /** A third-party {@code Number} — {@link TemplateParser}'s scalar test must not trust it. */
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
  void hostileNumberSubclassArgumentIsSanitizedInTemplateSubstitution() {
    var resolved =
        TemplateParser.resolve("amount: {amount}", Map.of("amount", new HostileAmount()));

    assertThat(resolved).doesNotContain("\n");
    assertThat(resolved).isEqualTo("amount: 1\\n## forged\\n");
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
  void hostileEnumArgumentIsSanitizedInTemplateSubstitution() {
    var resolved = TemplateParser.resolve("status: {status}", Map.of("status", HostileEnum.VALUE));

    assertThat(resolved).doesNotContain("\n");
    assertThat(resolved).isEqualTo("status: 1\\n## forged\\n");
  }

  /**
   * The third production of the grammar. {@code {card.cvv}} and {@code {card}} were both ruled on;
   * {@code {password}} — a bare key naming a scalar — asked nothing at all and printed the value in
   * full, so one line of ordinary annotation out-narrated the policy the trace beside it obeyed.
   */
  @Test
  void aScalarPlaceholderNamingASecretIsRedactedLikeAField() {
    var resolved = TemplateParser.resolve("login {password}", Map.of("password", "hunter2"));

    assertThat(resolved).isEqualTo("login [REDACTED]").doesNotContain("hunter2");
  }

  /** The other axis on the same line: the bytes are a credential, whatever the key is called. */
  @Test
  void aCredentialShapedScalarIsRedactedWhateverThePlaceholderIsCalled() {
    var jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";

    var resolved = TemplateParser.resolve("issued {value}", Map.of("value", jwt));

    assertThat(resolved).isEqualTo("issued [REDACTED]").doesNotContain("eyJhbGciOiJIUzI1NiJ9");
  }

  @Test
  void aTextPlaceholderCannotForgeALineWithARawControlCharacter() {
    var comment = "ok\n## forged\r" + (char) 0x0000;

    var resolved = TemplateParser.resolve("note: {comment}", Map.of("comment", comment));

    assertThat(resolved).doesNotContain("\n").isEqualTo("note: ok\\n## forged\\r\\u0000");
  }

  @Test
  void aTextPlaceholderIsCappedTheWayACapturedStringIs() {
    var resolved = TemplateParser.resolve("body {payload}", Map.of("payload", "x".repeat(500)));

    assertThat(resolved).isEqualTo("body " + "x".repeat(200) + "…");
  }

  /**
   * A {@code CharSequence} that is not a {@code String} takes the same route: {@code StringBuilder}
   * is the one an application hands a template most often, and its content is text like any other.
   */
  @Test
  void aNonStringCharSequenceIsRedactedByShapeToo() {
    var jwt = new StringBuilder("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl");

    assertThat(TemplateParser.resolve("issued {value}", Map.of("value", jwt)))
        .isEqualTo("issued [REDACTED]");
  }

  @Test
  void aPlaceholderNamingASecretWithNoValueStaysLiteralSoTheTypoWarningSurvives() {
    assertThat(TemplateParser.resolve("login {password}", Map.of()))
        .as("nothing resolves, so nothing can leak — and the unresolved warning must still fire")
        .isEqualTo("login {password}");
  }

  /** The deny-list reads the key exactly as it reads a field name: substring, case-insensitive. */
  @Test
  void aScalarPlaceholderWhoseKeyMerelyContainsASecretWordIsRedactedToo() {
    assertThat(TemplateParser.resolve("using {apiToken}", Map.of("apiToken", "tok-9")))
        .isEqualTo("using [REDACTED]");
  }

  @Test
  void anOrdinaryScalarPlaceholderIsUntouchedByEitherAxis() {
    assertThat(TemplateParser.resolve("order {orderId}", Map.of("orderId", "ORD-7")))
        .isEqualTo("order ORD-7");
  }

  @Test
  void jdkBoxedNumericArgumentStillSubstitutesRawUnsanitized() {
    var resolved =
        TemplateParser.resolve("qty: {qty}, price: {price}", Map.of("qty", 5, "price", 9.99));

    assertThat(resolved).isEqualTo("qty: 5, price: 9.99");
  }
}
