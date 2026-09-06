/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class NarrativeTraceProxyTest {

  interface OrderService {
    String placeOrder(String customerId);
  }

  interface AuditSink {
    void record(String entry);
  }

  @Test
  void voidMethodCapturesNullRenderedValuePerTheReturnedContract() {
    AuditSink real = entry -> {};
    var context = new ThreadLocalNarrativeContext();

    AuditSink proxy = NarrativeTraceProxy.trace(real, AuditSink.class, context);
    proxy.record("payment received");

    var outcome = (TraceOutcome.Returned) context.captureTrace().roots().get(0).outcome();
    assertThat(outcome.renderedValue()).isNull();
    assertThat(outcome.structuredValue()).isNull();
  }

  @Test
  void delegatesToRealImplementationAndReturnsValue() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    OrderService proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    var result = proxy.placeOrder("C-123");

    assertThat(result).isEqualTo("order-42");
  }

  @Test
  void capturesMethodEntryAndReturnInContext() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    OrderService proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.signature().methodName()).isEqualTo("placeOrder");
    assertThat(root.signature().parameters()).hasSize(1);
    assertThat(root.signature().parameters().get(0).name()).isEqualTo("customerId");
    assertThat(root.signature().parameters().get(0).renderedValue()).isEqualTo("\"C-123\"");
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("\"order-42\"");
  }

  interface OverloadedFinder {
    String find(String id, int limit, long[] window);
  }

  @Test
  void capturesDeclaredParameterAndReturnTypes() {
    OverloadedFinder real = (id, limit, window) -> "found";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, OverloadedFinder.class, context);
    proxy.find("X", 3, new long[] {1L});

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.returnType()).isEqualTo("java.lang.String");
    assertThat(sig.parameters().get(0).type()).isEqualTo("java.lang.String");
    assertThat(sig.parameters().get(1).type()).isEqualTo("int");
    assertThat(sig.parameters().get(2).type()).isEqualTo("long[]");
  }

  @Test
  void capturesInstanceIdWhenEnabled() {
    OrderService real = customerId -> "order-42";
    var config = new ai.narrativetrace.core.config.NarrativeTraceConfig();
    config.setCaptureInstanceIds(true);
    var context = new ThreadLocalNarrativeContext(config);

    var proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.instanceId()).isEqualTo(Integer.toHexString(System.identityHashCode(real)));
  }

  @Test
  void capturesCallSiteSourceLocationWhenEnabled() {
    OrderService real = customerId -> "order-42";
    var config = new ai.narrativetrace.core.config.NarrativeTraceConfig();
    config.setCaptureSourceLocation(true);
    var context = new ThreadLocalNarrativeContext(config);

    var proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var source = context.captureTrace().roots().get(0).signature().source();
    assertThat(source).isNotNull();
    assertThat(source.file()).isEqualTo("NarrativeTraceProxyTest.java");
    assertThat(source.line()).isPositive();
  }

  @Test
  void sourceLocationStaysNullByDefault() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    assertThat(context.captureTrace().roots().get(0).signature().source()).isNull();
  }

  @Test
  void instanceIdStaysNullByDefault() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    assertThat(context.captureTrace().roots().get(0).signature().instanceId()).isNull();
  }

  @Test
  void capturesDeclaringPackageOnSignature() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    OrderService proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().packageName()).isEqualTo("ai.narrativetrace.proxy");
  }

  @Test
  void capturesDeclaringPackageOnMultiInterfaceProxy() {
    var context = new ThreadLocalNarrativeContext();
    var target =
        new GreeterAuditor() {
          @Override
          public String greet(String name) {
            return "hi " + name;
          }
        };

    var proxy =
        NarrativeTraceProxy.trace(target, new Class<?>[] {Greeter.class, Auditable.class}, context);
    ((Greeter) proxy).greet("Alice");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().packageName()).isEqualTo("ai.narrativetrace.proxy");
  }

  interface InventoryService {
    boolean checkStock(String itemId);
  }

  @Test
  void twoProxiedServicesShareContextForNestedTrace() {
    var context = new ThreadLocalNarrativeContext();

    InventoryService inventoryProxy =
        NarrativeTraceProxy.trace(
            (InventoryService) itemId -> true, InventoryService.class, context);
    OrderService orderProxy =
        NarrativeTraceProxy.trace(
            (OrderService)
                customerId -> {
                  inventoryProxy.checkStock("ITEM-1");
                  return "order-42";
                },
            OrderService.class,
            context);

    orderProxy.placeOrder("C-123");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.children()).hasSize(1);

    var child = root.children().get(0);
    assertThat(child.signature().className()).isEqualTo("InventoryService");
    assertThat(child.signature().methodName()).isEqualTo("checkStock");
  }

  interface PaymentService {
    void charge(double amount);
  }

  @Test
  void capturesExceptionAndRethrows() {
    var exception = new IllegalStateException("insufficient funds");
    PaymentService real =
        amount -> {
          throw exception;
        };
    var context = new ThreadLocalNarrativeContext();

    PaymentService proxy = NarrativeTraceProxy.trace(real, PaymentService.class, context);

    assertThatThrownBy(() -> proxy.charge(99.95)).isSameAs(exception);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(((TraceOutcome.Threw) root.outcome()).exception()).isSameAs(exception);
  }

  interface NarratedOrderService {
    @Narrated("Placing order of {quantity} units for customer {customerId}")
    String placeOrder(String customerId, int quantity);
  }

  @Test
  void nonNarratedMethodHasNullNarration() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    OrderService proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.narration()).isNull();
  }

  @Test
  void detectsNarratedAnnotationAndResolvesTemplate() {
    NarratedOrderService real = (customerId, quantity) -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, NarratedOrderService.class, context);
    proxy.placeOrder("C-123", 5);

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.narration()).isEqualTo("Placing order of 5 units for customer C-123");
  }

  @Test
  void narratedMethodCarriesTheRawTemplateBesideTheResolvedNarration() {
    NarratedOrderService real = (customerId, quantity) -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, NarratedOrderService.class, context);
    proxy.placeOrder("C-123", 5);

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.narrationTemplate())
        .isEqualTo("Placing order of {quantity} units for customer {customerId}");
  }

  @Test
  void nonNarratedMethodHasNullNarrationTemplate() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();

    OrderService proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);
    proxy.placeOrder("C-123");

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.narrationTemplate()).isNull();
  }

  interface OnErrorService {
    @OnError("Context: charging customer {customerId}, amount was {amount}")
    void charge(String customerId, double amount);
  }

  @Test
  void methodWithoutOnErrorHasNullErrorContext() {
    PaymentService real =
        amount -> {
          throw new IllegalStateException("fail");
        };
    var context = new ThreadLocalNarrativeContext();

    PaymentService proxy = NarrativeTraceProxy.trace(real, PaymentService.class, context);
    assertThatThrownBy(() -> proxy.charge(99.0)).isInstanceOf(IllegalStateException.class);

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.errorContext()).isNull();
  }

  @Test
  void detectsOnErrorAnnotationAndResolvesTemplate() {
    OnErrorService real =
        (customerId, amount) -> {
          throw new RuntimeException("declined");
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, OnErrorService.class, context);
    assertThatThrownBy(() -> proxy.charge("C-123", 99.95)).isInstanceOf(RuntimeException.class);

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.errorContext()).isEqualTo("Context: charging customer C-123, amount was 99.95");
  }

  interface AuthService {
    boolean login(String username, @NotTraced String password);
  }

  @Test
  void respectsNotTracedAnnotationAsRedacted() {
    AuthService real = (username, password) -> true;
    var context = new ThreadLocalNarrativeContext();

    AuthService proxy = NarrativeTraceProxy.trace(real, AuthService.class, context);
    proxy.login("admin", "secret");

    var tree = context.captureTrace();
    var params = tree.roots().get(0).signature().parameters();
    assertThat(params.get(0).redacted()).isFalse();
    assertThat(params.get(1).redacted()).isTrue();
    assertThat(params.get(1).name()).isEqualTo("password");
  }

  interface SpecificExceptionService {
    @OnError(value = "Payment declined for {customerId}", exception = IllegalStateException.class)
    void charge(String customerId);
  }

  @Test
  void matchesSpecificExceptionType() {
    SpecificExceptionService real =
        customerId -> {
          throw new IllegalStateException("declined");
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, SpecificExceptionService.class, context);
    assertThatThrownBy(() -> proxy.charge("C-123")).isInstanceOf(IllegalStateException.class);

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.errorContext()).isEqualTo("Payment declined for C-123");
  }

  interface MostSpecificWinsService {
    @OnError("General error")
    @OnError(value = "Specific: state error", exception = IllegalStateException.class)
    void process();
  }

  @Test
  void mostSpecificExceptionTypeWinsOverDefault() {
    MostSpecificWinsService real =
        () -> {
          throw new IllegalStateException("bad state");
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, MostSpecificWinsService.class, context);
    assertThatThrownBy(proxy::process).isInstanceOf(IllegalStateException.class);

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.errorContext()).isEqualTo("Specific: state error");
  }

  interface NonMatchingService {
    @OnError(value = "IO problem", exception = java.io.IOException.class)
    void process();
  }

  @Test
  void nonMatchingAnnotationProducesNoErrorContext() {
    NonMatchingService real =
        () -> {
          throw new IllegalArgumentException("wrong arg");
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, NonMatchingService.class, context);
    assertThatThrownBy(proxy::process).isInstanceOf(IllegalArgumentException.class);

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.errorContext()).isNull();
  }

  interface StatusService {
    String status();
  }

  @Test
  void enterAndExitCountsAreAlwaysBalanced() {
    var enterCount = new AtomicInteger();
    var exitCount = new AtomicInteger();
    var context = countingContext(enterCount, exitCount);

    PaymentService proxy =
        NarrativeTraceProxy.trace(
            (PaymentService)
                amount -> {
                  throw new IllegalStateException("fail");
                },
            PaymentService.class,
            context);
    try {
      proxy.charge(99.0);
    } catch (Exception ignored) {
    }

    assertThat(enterCount.get()).isEqualTo(1);
    assertThat(exitCount.get()).isEqualTo(1);
  }

  private NarrativeContext countingContext(AtomicInteger enterCount, AtomicInteger exitCount) {
    return new StubContext() {
      @Override
      public SpanId enterMethod(MethodSignature sig) {
        enterCount.incrementAndGet();
        return null;
      }

      @Override
      public void exitMethodWithReturn(String val) {
        exitCount.incrementAndGet();
      }

      @Override
      public void exitMethodWithReturn(String val, SpanId spanId) {
        exitCount.incrementAndGet();
      }

      @Override
      public void exitMethodWithException(Throwable ex, String ctx) {
        exitCount.incrementAndGet();
      }

      @Override
      public void exitMethodWithException(Throwable ex, String ctx, SpanId spanId) {
        exitCount.incrementAndGet();
      }
    };
  }

  private NarrativeContext inactiveCountingContext(AtomicInteger callCount) {
    return new StubContext() {
      @Override
      public boolean isActive() {
        return false;
      }

      @Override
      public SpanId enterMethod(MethodSignature sig) {
        callCount.incrementAndGet();
        return null;
      }

      @Override
      public void exitMethodWithReturn(String val) {
        callCount.incrementAndGet();
      }

      @Override
      public void exitMethodWithException(Throwable ex, String ctx) {
        callCount.incrementAndGet();
      }
    };
  }

  @SuppressWarnings("PMD.AbstractClassWithoutAnyMethod")
  private abstract static class StubContext implements NarrativeContext {
    @Override
    public void detachFrame(SpanId spanId) {}

    @Override
    public void exitMethodWithReturn(String val, SpanId spanId) {}

    @Override
    public void exitMethodWithException(Throwable ex, String ctx, SpanId spanId) {}

    @Override
    public TraceTree captureTrace() {
      return null;
    }

    @Override
    public void reset() {}

    @Override
    public ai.narrativetrace.core.context.ContextSnapshot snapshot() {
      return null;
    }
  }

  @Test
  void noArgMethodPassesNullArgsFromJdkProxy() {
    StatusService real = () -> "ok";
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, StatusService.class, context);
    var result = proxy.status();

    assertThat(result).isEqualTo("ok");
    var tree = context.captureTrace();
    assertThat(tree.roots().get(0).signature().parameters()).isEmpty();
  }

  interface RedactedNarrationService {
    @Narrated("Authenticating {username} with password {password}")
    boolean login(String username, @NotTraced String password);
  }

  @Test
  void redactedParametersMaskedInNarration() {
    RedactedNarrationService real = (username, password) -> true;
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, RedactedNarrationService.class, context);
    proxy.login("admin", "s3cret");

    var tree = context.captureTrace();
    var sig = tree.roots().get(0).signature();
    assertThat(sig.narration()).isEqualTo("Authenticating admin with password [REDACTED]");
  }

  /** A record whose component is redacted, and a template that names it anyway. */
  record Card(String number, @NotTraced String cvv) {}

  record Basket(String id, Card card) {}

  interface RedactedPathNarrationService {
    @Narrated("charging {card.cvv}")
    boolean charge(Card card);

    @Narrated("charging {basket.card.cvv}")
    boolean checkout(Basket basket);
  }

  @Test
  void aTemplateNamingARedactedComponentNarratesTheMarkerNotTheValue() {
    RedactedPathNarrationService real =
        new RedactedPathNarrationService() {
          @Override
          public boolean charge(Card card) {
            return true;
          }

          @Override
          public boolean checkout(Basket basket) {
            return true;
          }
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, RedactedPathNarrationService.class, context);
    proxy.charge(new Card("4111", "123"));
    proxy.checkout(new Basket("b-1", new Card("4111", "123")));

    var roots = context.captureTrace().roots();
    assertThat(roots.get(0).signature().narration()).isEqualTo("charging [REDACTED]");
    assertThat(roots.get(1).signature().narration())
        .as("the rule holds at every depth of the path")
        .isEqualTo("charging [REDACTED]");
  }

  interface Greeter {
    String greet(String name);
  }

  interface Auditable {
    String audit(String action);
  }

  static class GreeterAuditor implements Greeter, Auditable {
    @Override
    public String greet(String name) {
      return "Hello, " + name;
    }

    @Override
    public String audit(String action) {
      return "audited: " + action;
    }
  }

  @Test
  void multiInterfaceProxyCapturesExceptionAndRethrows() {
    var context = new ThreadLocalNarrativeContext();
    var target =
        new GreeterAuditor() {
          @Override
          public String greet(String name) {
            throw new RuntimeException("boom");
          }
        };

    var proxy =
        NarrativeTraceProxy.trace(target, new Class<?>[] {Greeter.class, Auditable.class}, context);

    assertThatThrownBy(() -> ((Greeter) proxy).greet("Alice"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void skipsAllCaptureWhenContextIsNotActive() {
    var enterCount = new AtomicInteger();
    var context = inactiveCountingContext(enterCount);

    OrderService proxy =
        NarrativeTraceProxy.trace(
            (OrderService) customerId -> "order-42", OrderService.class, context);
    var result = proxy.placeOrder("C-123");

    assertThat(result).isEqualTo("order-42");
    assertThat(enterCount.get()).isZero();
  }

  @Test
  void skipsAllCaptureAndRethrowsWhenContextIsNotActive() {
    var context = NoopNarrativeContext.INSTANCE;
    PaymentService proxy =
        NarrativeTraceProxy.trace(
            (PaymentService)
                amount -> {
                  throw new IllegalStateException("declined");
                },
            PaymentService.class,
            context);

    assertThatThrownBy(() -> proxy.charge(99.0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("declined");
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void skipsAllCaptureOnMultiInterfaceProxyWhenContextIsNotActive() {
    var context = NoopNarrativeContext.INSTANCE;
    var target = new GreeterAuditor();

    var proxy =
        NarrativeTraceProxy.trace(target, new Class<?>[] {Greeter.class, Auditable.class}, context);
    var result = ((Greeter) proxy).greet("Alice");

    assertThat(result).isEqualTo("Hello, Alice");
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void skipsAllCaptureOnMultiInterfaceProxyAndRethrowsWhenNotActive() {
    var context = NoopNarrativeContext.INSTANCE;
    var target =
        new GreeterAuditor() {
          @Override
          public String greet(String name) {
            throw new RuntimeException("boom");
          }
        };
    var proxy =
        NarrativeTraceProxy.trace(target, new Class<?>[] {Greeter.class, Auditable.class}, context);

    assertThatThrownBy(() -> ((Greeter) proxy).greet("Alice"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void emptyInterfacesArrayThrowsMeaningfulError() {
    var context = new ThreadLocalNarrativeContext();
    var target = new Object();

    assertThatThrownBy(() -> NarrativeTraceProxy.trace(target, new Class<?>[0], context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interface");
  }

  @Test
  void tracesCallsAcrossMultipleInterfaces() {
    var context = new ThreadLocalNarrativeContext();
    var target = new GreeterAuditor();

    var proxy =
        NarrativeTraceProxy.trace(target, new Class<?>[] {Greeter.class, Auditable.class}, context);

    var greetResult = ((Greeter) proxy).greet("Alice");
    var auditResult = ((Auditable) proxy).audit("login");

    assertThat(greetResult).isEqualTo("Hello, Alice");
    assertThat(auditResult).isEqualTo("audited: login");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("Greeter");
    assertThat(tree.roots().get(1).signature().className()).isEqualTo("Auditable");
  }

  // --- Phase 5: Async return type handling ---

  interface AsyncService {
    CompletableFuture<String> fetchAsync(String id);
  }

  @Test
  void methodReturningStringClosesSpanAtReturn() {
    OrderService real = customerId -> "order-42";
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, OrderService.class, context);

    proxy.placeOrder("C-123");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("\"order-42\"");
  }

  @Test
  void proxyClosesSpanImmediatelyForCompletedFuture() {
    AsyncService real = id -> CompletableFuture.completedFuture("result-" + id);
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, AsyncService.class, context);

    var future = proxy.fetchAsync("X-1");

    assertThat(future.join()).isEqualTo("result-X-1");
    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue())
        .isEqualTo("\"result-X-1\"");
  }

  @Test
  void proxyDefersExitForIncompleteFutureAndCapturesResolvedValue() {
    var incompleteFuture = new CompletableFuture<String>();
    AsyncService real = id -> incompleteFuture;
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, AsyncService.class, context);

    var returned = proxy.fetchAsync("X-1");
    // Before completion: frame is detached, visible as in-flight
    assertThat(context.captureTrace().roots()).hasSize(1);
    assertThat(context.captureTrace().roots().get(0).outcome())
        .isInstanceOf(TraceOutcome.Incomplete.class);

    // Complete the future
    incompleteFuture.complete("resolved-value");
    returned.join();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var outcome = (TraceOutcome.Returned) tree.roots().get(0).outcome();
    assertThat(outcome.renderedValue()).isEqualTo("\"resolved-value\"");
  }

  @Test
  void deferredExitUnwrapsCompletionExceptionCause() {
    var rootCause = new IllegalStateException("underlying failure");
    var incompleteFuture = new CompletableFuture<String>();
    AsyncService real = id -> incompleteFuture;
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, AsyncService.class, context);

    var returned = proxy.fetchAsync("X-1");
    // completeExceptionally with a CompletionException wrapping root cause;
    // whenComplete receives it as-is, and exitDeferred should unwrap
    incompleteFuture.completeExceptionally(new CompletionException(rootCause));

    assertThatThrownBy(returned::join).isInstanceOf(CompletionException.class);

    var tree = context.captureTrace();
    var outcome = (TraceOutcome.Threw) tree.roots().get(0).outcome();
    assertThat(outcome.exception()).isSameAs(rootCause);
  }

  interface TypedAsyncService {
    TrackingFuture fetchAsync(String id);
  }

  static final class TrackingFuture extends CompletableFuture<String> {}

  @Test
  void proxiedFutureSubclassReturnTypeIsTheSameInstance() {
    var returnedFuture = new TrackingFuture();
    TypedAsyncService real =
        id -> {
          returnedFuture.complete("result-" + id);
          return returnedFuture;
        };
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, TypedAsyncService.class, context);

    var result =
        new Object() {
          TrackingFuture value;
        };
    assertThatCode(() -> result.value = proxy.fetchAsync("42")).doesNotThrowAnyException();
    assertThat(result.value).isSameAs(returnedFuture);
  }

  @Test
  void cancellingProxyReturnedFutureCancelsUnderlyingFuture() {
    var underlyingFuture = new CompletableFuture<String>();
    AsyncService real = id -> underlyingFuture;
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, AsyncService.class, context);

    var returned = proxy.fetchAsync("42");
    returned.cancel(true);

    assertThat(underlyingFuture.isCancelled()).isTrue();
  }

  @Test
  void failedFutureSubclassRecordsExceptionInTrace() {
    var failure = new RuntimeException("subclass-failure");
    var failedFuture = new TrackingFuture();
    failedFuture.completeExceptionally(failure);
    TypedAsyncService real = id -> failedFuture;
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, TypedAsyncService.class, context);

    proxy.fetchAsync("42");

    var tree = context.captureTrace();
    var outcome = (TraceOutcome.Threw) tree.roots().get(0).outcome();
    assertThat(outcome.exception()).isSameAs(failure);
  }

  interface BaseService {
    String process(String input);
  }

  interface ExtendedService extends BaseService {}

  @Test
  void singleInterfaceProxyUsesInterfaceNameNotDeclaringClass() {
    ExtendedService real = input -> "processed-" + input;
    var context = new ThreadLocalNarrativeContext();

    ExtendedService proxy = NarrativeTraceProxy.trace(real, ExtendedService.class, context);
    proxy.process("data");

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.className()).isEqualTo("ExtendedService");
  }

  interface SingleOnErrorService {
    @OnError(value = "Failed with {id}", exception = RuntimeException.class)
    String doWork(String id);
  }

  @Test
  void singleOnErrorAnnotationResolvesWhenExceptionMatches() {
    SingleOnErrorService real =
        id -> {
          throw new IllegalArgumentException("boom");
        };
    var context = new ThreadLocalNarrativeContext();

    var proxy = NarrativeTraceProxy.trace(real, SingleOnErrorService.class, context);
    assertThatThrownBy(() -> proxy.doWork("ABC"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("boom");

    var sig = context.captureTrace().roots().get(0).signature();
    assertThat(sig.errorContext()).isEqualTo("Failed with ABC");
  }

  @Test
  void computeMetadataReturnsNonNullForZeroParamMethod() throws Exception {
    var method = StatusService.class.getMethod("status");
    var meta = NarrativeTraceProxy.computeMetadata(method);
    assertThat(meta).isNotNull();
    assertThat(meta.paramNames()).isEmpty();
    assertThat(meta.narratedTemplate()).isNull();
  }

  @Test
  void computeMetadataExtractsParamNamesForMethodWithParameters() throws Exception {
    var method = OrderService.class.getMethod("placeOrder", String.class);
    var meta = NarrativeTraceProxy.computeMetadata(method);
    assertThat(meta.paramNames()).containsExactly("customerId");
    assertThat(meta.redacted()).containsExactly(false);
  }

  @Test
  void computeMetadataExtractsNarratedTemplate() throws Exception {
    var method = NarratedOrderService.class.getMethod("placeOrder", String.class, int.class);
    var meta = NarrativeTraceProxy.computeMetadata(method);
    assertThat(meta.narratedTemplate())
        .isEqualTo("Placing order of {quantity} units for customer {customerId}");
  }

  @Test
  void proxyWrapsInvocationInRunScoped() {
    var scopedSpanIds = new java.util.ArrayList<SpanId>();
    var inner = new ThreadLocalNarrativeContext();
    var context = new RunScopedSpyContext(inner, scopedSpanIds);

    OrderService proxy =
        NarrativeTraceProxy.trace(
            (OrderService) customerId -> "order-42", OrderService.class, context);
    proxy.placeOrder("C-123");

    assertThat(scopedSpanIds).hasSize(1);
    assertThat(scopedSpanIds.get(0).value()).matches("[0-9a-f]{16}");
  }

  private static final class RunScopedSpyContext implements NarrativeContext {
    private final ThreadLocalNarrativeContext inner;
    private final java.util.List<SpanId> scopedSpanIds;

    RunScopedSpyContext(ThreadLocalNarrativeContext inner, java.util.List<SpanId> scopedSpanIds) {
      this.inner = inner;
      this.scopedSpanIds = scopedSpanIds;
    }

    @Override
    public SpanId enterMethod(MethodSignature sig) {
      return inner.enterMethod(sig);
    }

    @Override
    public void detachFrame(SpanId spanId) {
      inner.detachFrame(spanId);
    }

    @Override
    public void exitMethodWithReturn(String val) {
      inner.exitMethodWithReturn(val);
    }

    @Override
    public void exitMethodWithReturn(String val, SpanId spanId) {
      inner.exitMethodWithReturn(val, spanId);
    }

    @Override
    public void exitMethodWithException(Throwable ex, String ctx) {
      inner.exitMethodWithException(ex, ctx);
    }

    @Override
    public void exitMethodWithException(Throwable ex, String ctx, SpanId spanId) {
      inner.exitMethodWithException(ex, ctx, spanId);
    }

    @Override
    public TraceTree captureTrace() {
      return inner.captureTrace();
    }

    @Override
    public void reset() {
      inner.reset();
    }

    @Override
    public ai.narrativetrace.core.context.ContextSnapshot snapshot() {
      return inner.snapshot();
    }

    @Override
    public <T> T runScoped(SpanId spanId, Supplier<T> fn) {
      scopedSpanIds.add(spanId);
      return inner.runScoped(spanId, fn);
    }
  }
}
