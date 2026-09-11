# Spring Integration Guide

This guide covers integrating NarrativeTrace into a Spring Boot application, from basic bean tracing through production request lifecycle and cross-thread propagation with `@Async`.

## Modules

| Module | Purpose |
|---|---|
| `narrativetrace-spring` | `@EnableNarrativeTrace` — auto-wraps eligible beans via `BeanPostProcessor` |
| `narrativetrace-servlet` | `NarrativeTraceFilter` — per-request trace lifecycle (no Spring dependency) |
| `narrativetrace-spring-web` | Spring auto-configuration for the servlet filter with pluggable exporter |
| `narrativetrace-micrometer` | `NarrativeTraceThreadLocalAccessor` — cross-thread trace propagation via Micrometer context-propagation |

## 1. Basic Bean Tracing

Add the dependency:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.1")
```

Enable tracing on your configuration class:

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
public class AppConfig { }
```

When `basePackages` is omitted, it defaults to the annotated class's package (like `@ComponentScan`).

### Logger name

When `narrativetrace-slf4j` is on the classpath, the auto-created context narrates through SLF4J automatically (`Slf4jTraceEventListener` on the pipeline's synchronous path). Configure the logger name:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

Default is `"narrativetrace"`. Set to `""` to disable automatic SLF4J wrapping.

### Service identity

Configure service identity metadata on the annotation when spans and exported traces should carry
stable process identity:

```java
@Configuration
@EnableNarrativeTrace(
    basePackages = {"com.example.order", "com.example.payment"},
    serviceName = "order-service",
    serviceVersion = "2.0.0",
    environment = "production"
)
public class AppConfig { }
```

These values are stamped onto every created `SpanContext`. Downstream integrations such as SLF4J
MDC, JSON export, and OpenTelemetry can then surface `service.name`, `service.version`, and
`service.environment` for correlation.

### What happens

A `BeanPostProcessor` (ordered at `HIGHEST_PRECEDENCE`) wraps eligible beans in JDK dynamic proxies that record method entry, return values, and exceptions into a shared `NarrativeContext`.

**Eligibility rules:**
- Bean must implement at least one interface
- Bean's class must be in a configured package
- Spring framework interfaces (e.g., `BeanFactoryAware`, `InitializingBean`) are excluded

A `NarrativeContext` bean is provided automatically. When `narrativetrace-slf4j` is on the classpath and `loggerName` is non-empty, it narrates through SLF4J under that logger. Define your own `narrativeContext` bean to override (e.g., for Micrometer integration — see section 3).

### BPP ordering

The tracing `BeanPostProcessor` runs at `HIGHEST_PRECEDENCE`, meaning the trace proxy is the **innermost** wrapper. When combined with Spring's `@Async` proxy, the layering is:

```
Async proxy (outer) → Trace proxy (inner) → Real bean
```

This ensures tracing runs on the actual async thread, not the caller thread.

## 2. Production Request Tracing

For tracing HTTP requests in a deployed application, add the servlet filter module:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.1")
```

This auto-registers `NarrativeTraceFilter` as a Spring bean. The filter follows a reset-chain-capture-export-reset lifecycle on each request:

1. Reset the context (clear stale state)
2. Execute the filter chain (downstream beans are traced via proxies)
3. Capture the accumulated trace tree
4. Export via the configured `TraceExporter`
5. Reset again

While the request is active, the filter also populates persistent MDC correlation fields including
`traceId`, `traceName`, `httpMethod`, `httpRoute`, and `clientIp`. `traceName` is a deterministic
three-word alias derived from the trace ID, intended for readable log correlation.

### Default exporter

By default, traces are exported via `Slf4jTraceExporter` (JSON to SLF4J at INFO level). To use a custom exporter, register a `TraceExporter` bean:

```java
@Bean
TraceExporter traceExporter() {
    return (tree, requestContext) -> {
        // Send to your observability platform
    };
}
```

The `RequestContext` provides `method`, `uri`, `statusCode`, and `durationMillis`.

### User context (`RequestContextProvider`)

Authentication and tenancy usually live outside the trace context — in a security principal, a header, or a session attribute. `RequestContextProvider` copies them off the request once per request, and NarrativeTrace stamps `enduserId`, `sessionId` and `tenantId` onto every span that request goes on to create, and onto the MDC.

The interface is generic over the framework's request type so the API jar can stay dependency-free. The servlet filter and the Spring Web wiring both bind it to `HttpServletRequest` — **write that binding out**:

```java
import ai.narrativetrace.api.export.RequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
class SecurityContextProvider implements RequestContextProvider<HttpServletRequest> {

    @Override
    public UserContext resolveUserContext(HttpServletRequest request) {
        var principal = request.getUserPrincipal();
        if (principal == null) {
            return null;  // anonymous request — absence, not an error
        }
        return new UserContext(
                principal.getName(),
                request.getRequestedSessionId(),
                request.getHeader("X-Tenant-Id"));
    }
}
```

`NarrativeTraceWebConfiguration` picks the bean up automatically — nothing else to wire.

> **The binding is what makes the bean findable.** The provider is resolved by its *bound* type, `RequestContextProvider<HttpServletRequest>`. A bean declared raw (`implements RequestContextProvider`) or bound to some other request type is silently not a candidate: Spring's generics-aware resolution does not match it, the filter runs with no user context, and nothing is logged. The symptom is empty `enduserId`/`tenantId` fields on every span, not an error.

Returning `null` means "no identity for this request", which is normal for anonymous traffic. A provider that throws is treated the same way — the request proceeds without user context, because observability must never fail a request.

**Without Spring**, the same implementation goes to the filter directly:

```java
var filter = new NarrativeTraceFilter(context, exporter, new SecurityContextProvider());
```

## 3. Cross-Thread Propagation (`@Async`)

When a traced method dispatches work to another thread (via `@Async`, `CompletableFuture.supplyAsync`, or a custom executor), the trace context must be propagated explicitly. Without this, async work appears as a disconnected trace on the worker thread.

### How it works

NarrativeTrace uses [Micrometer context-propagation](https://github.com/micrometer-metrics/context-propagation) to carry trace state across thread boundaries. The setup has three parts:

1. **Register the accessor** — tells Micrometer how to snapshot/restore NarrativeTrace's `ThreadLocal` state
2. **Configure a `TaskDecorator`** — wraps async runnables so they carry the snapshot to the worker thread
3. **Override the `NarrativeContext` bean** — wire the accessor to the actual context instance

### Dependencies

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.1")
implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.1")
```

### Configuration

```java
@Configuration
@EnableNarrativeTrace
@EnableAsync
public class AppConfig {

    @Bean
    NarrativeContext narrativeContext() {
        var tlc = new ThreadLocalNarrativeContext();

        // Register with Micrometer so context-propagation knows about NarrativeTrace
        var accessor = new NarrativeTraceThreadLocalAccessor(tlc);
        ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);

        return tlc; // SLF4J narration attaches automatically when narrativetrace-slf4j is present
    }

    @Bean
    ThreadPoolTaskExecutor taskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setCorePoolSize(2);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### The `TaskDecorator`

The `TaskDecorator` is the bridge that captures context on the calling thread and restores it on the worker thread. `narrativetrace-spring` ships one — `ai.narrativetrace.spring.ContextPropagatingTaskDecorator` — and the snippet above uses it directly. It carries two things, both captured when the task is *submitted*:

| Part | Required for NarrativeTrace? | Purpose |
|---|---|---|
| Micrometer `ContextSnapshot` | **Yes** | Propagates NarrativeTrace state via the registered `ThreadLocalAccessor` |
| MDC snapshot/restore | No | Propagates SLF4J MDC for logging correlation (`traceId`, `traceName`, request metadata, etc.) |

The MDC half is on by default and restores the worker thread's own MDC afterwards, including when the task throws. Turn it off when your application does not use MDC, or when another decorator already owns it:

```java
executor.setTaskDecorator(ContextPropagatingTaskDecorator.withoutMdc());
```

**Dependencies.** The decorator needs `io.micrometer:context-propagation` on the runtime classpath — `narrativetrace-spring` declares it `compileOnly`, so add it yourself (the `narrativetrace-micrometer` module you already need for the accessor pulls it in — see [Dependencies](#dependencies) above). SLF4J is optional: MDC propagation switches itself off when `org.slf4j.MDC` is absent.

**If you already have a `TaskDecorator`**, an executor has only one decorator slot — compose them, or add the Micrometer snapshot to yours:

```java
public class MyDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        var snapshot = ContextSnapshotFactory.builder().build().captureAll();
        return () -> snapshot.wrap(runnable).run();
    }
}
```

### Verifying propagation

An `@Async` method should appear as a child in the trace tree, even though it runs on a different thread:

```
OrderService.placeOrder(customerId: "C-1234")
  InventoryService.reserve(productId: "SKU-KB", quantity: 2) → ReservedInventory(...)
  PaymentService.charge(customerId: "C-1234", amount: 179.98) → Payment(...)
  NotificationService.sendConfirmation(orderId: "ORD-001") → true    ← runs on async-1 thread
```

Where it lands depends on when the task was submitted, not on when it finished:
submitted while `placeOrder` was still running, it is a child of `placeOrder`;
submitted after `placeOrder` returned, it is the next root in the same trace.
Either way the caller's `captureTrace()` reports it, matching what the
synchronous log stream already narrated (ADR-013).

Without the `TaskDecorator`, the notification call would not appear in the trace at all.

### CompletableFuture deferred exit

When a traced method returns `CompletableFuture<T>`, the proxy automatically handles deferred completion:

1. The frame is detached from the active stack via `detachFrame(handle)`
2. A `whenComplete` callback is registered on the future
3. When the future resolves, the frame is completed with the actual resolved value (not `<pending>`)

This works transparently with `@Async` methods that return `CompletableFuture`. No additional configuration is needed — the proxy detects the return type and applies deferred exit automatically.

## 4. Testing Spring Beans

For testing traced beans with JUnit 5, you don't need the Spring module at all — use `NarrativeTraceProxy` directly:

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {

    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var customers = NarrativeTraceProxy.trace(
            new InMemoryCustomerService(), CustomerService.class, context);
        var orders = NarrativeTraceProxy.trace(
            new DefaultOrderService(customers), OrderService.class, context);

        orders.placeOrder("C-1234");
        // Trace output is automatic via the extension
    }
}
```

This approach is faster than a full Spring context and gives you direct control over wiring. Use `@SpringBootTest` only when you need to test the Spring integration itself (auto-wiring, BPP ordering, `@Async` propagation).

For a working example that boots a real Spring context and verifies both auto-wrapped bean tracing and `@Async` thread propagation, see [`SpringIntegrationTest`](../narrativetrace-examples/ecommerce/src/test/java/ai/narrativetrace/examples/ecommerce/SpringIntegrationTest.java).

## See also

- [Installation Guide](installation-guide.md) — dependencies, integration paths, module selection
- [Configuration Guide](configuration-guide.md) — tracing levels, `@EnableNarrativeTrace` details
- [Micronaut Integration Guide](micronaut-integration-guide.md) — Micronaut equivalent of this guide
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`
