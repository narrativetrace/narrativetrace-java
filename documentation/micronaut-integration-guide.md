# Micronaut Integration Guide

This guide covers integrating NarrativeTrace into a Micronaut application, from basic bean tracing through production HTTP request lifecycle.

## Modules

| Module | Purpose |
|---|---|
| `narrativetrace-micronaut` | Auto-wraps eligible beans via `BeanCreatedEventListener` — auto-discovered on classpath |
| `narrativetrace-micronaut-http` | Reactive `HttpServerFilter` for per-request trace lifecycle |

## 1. Basic Bean Tracing

Add the dependency:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
```

Configure base packages in `application.yml`:

```yaml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
```

No enable annotation needed. The `@Factory` and `BeanCreatedEventListener` are auto-discovered on the classpath.

### What happens

A `BeanCreatedEventListener<Any>` wraps eligible beans in JDK dynamic proxies that record method entry, return values, and exceptions into a shared `NarrativeContext`.

**Eligibility rules:**
- Bean's class must be in a configured base package
- Bean must implement at least one interface in a configured package
- Beans without matching interfaces are left untouched
- Empty `base-packages` wraps nothing (safe default)

A `NarrativeContext` bean is provided automatically (marked `@Secondary`). When `narrativetrace-slf4j` is on the classpath and `loggerName` is non-empty, it's wired with SLF4J event logging. Define your own `@Bean NarrativeContext` to override.

### Configuration properties

| Property | Type | Default | Purpose |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` | Package prefixes for bean/interface matching |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | SLF4J logger name (empty disables SLF4J wiring) |
| `narrativetrace.service-name` | `String` | `""` | Service identity — stamped on spans |
| `narrativetrace.service-version` | `String` | `""` | Service identity — stamped on spans |
| `narrativetrace.environment` | `String` | `""` | Service identity — stamped on spans |

### Overriding the default context

Declare your own `@Bean NarrativeContext` and the `@Secondary` default is replaced:

```kotlin
@Factory
class MyConfig {
    @Bean
    @Singleton
    fun narrativeContext(): NarrativeContext {
        // SLF4J narration attaches automatically when narrativetrace-slf4j is present;
        // the pipeline argument routes it to a custom logger name.
        return ThreadLocalNarrativeContext(
            NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"))
    }
}
```

## 2. Production HTTP Request Tracing

Add the HTTP filter module:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
```

The reactive HTTP filter (`HttpServerFilter`) is auto-registered on the classpath — no additional configuration needed.

### Filter lifecycle

1. Reset the context (clear stale state)
2. Stamp request metadata (HTTP method, path, remote address)
3. Proceed through the filter chain via `Mono.from(chain.proceed(request))`
4. Capture the accumulated trace tree
5. Export via the configured `TraceExporter`
6. Reset again (cleanup via `doFinally`)

The filter matches all paths (`@Filter("/**")`).

While the request is active, the filter populates persistent MDC correlation fields including
`traceId`, `traceName`, `httpMethod`, `httpRoute`, and `clientIp`. `traceName` is a deterministic
three-word alias derived from the trace ID, intended for readable log correlation.

### Default exporter

By default, traces are exported via `Slf4jTraceExporter` (JSON to SLF4J at INFO level). The export logger name is derived from the configured `loggerName` property: `<loggerName>.export` (e.g., `narrativetrace.export`).

To use a custom exporter, register a `TraceExporter` bean:

```kotlin
@Factory
class MyExporterConfig {
    @Bean
    @Singleton
    fun traceExporter(): TraceExporter = TraceExporter { tree, requestContext ->
        // Send to your observability platform
    }
}
```

The `@Secondary` default exporter is automatically replaced.

### User context (`RequestContextProvider`)

Authentication and tenancy usually live outside the trace context — in a header, a security principal, or a session attribute. `RequestContextProvider` copies them off the request once per request, and NarrativeTrace stamps `enduserId`, `sessionId` and `tenantId` onto every span that request goes on to create, and onto the MDC.

Micronaut has its **own** provider type, `ai.narrativetrace.micronaut.http.RequestContextProvider`, typed on Micronaut's `HttpRequest`. Declare it as a singleton and the filter injects it:

```kotlin
import ai.narrativetrace.micronaut.http.RequestContextProvider
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
class SecurityContextProvider : RequestContextProvider {
    override fun resolveUserContext(request: HttpRequest<*>): RequestContextProvider.UserContext? {
        val userId = request.headers["X-User-Id"] ?: return null  // anonymous — absence, not an error
        return RequestContextProvider.UserContext(
            enduserId = userId,
            sessionId = request.headers["X-Session-Id"],
            tenantId = request.headers["X-Tenant-Id"],
        )
    }
}
```

> **Import the Micronaut type, not the API jar's.** `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>` is a different interface — generic, and bound to `HttpServletRequest` by the servlet and Spring Web integrations. A bean implementing *that* one is silently not a candidate here: the filter injects nothing, runs without user context, and logs nothing. The symptom is empty `enduserId`/`tenantId` fields on every span, not an error. The two types are deliberately separate; see [API Surface](api-surface.md).

Returning `null` means "no identity for this request", which is normal for anonymous traffic. A provider that throws is treated the same way — the request proceeds without user context, because observability must never fail a request.

### Error handling

- Exporter failures are silently swallowed — observability must never fail requests
- Empty traces (no traced methods called) skip export entirely
- Chain errors trigger the export with status code 500, then the error propagates normally
- `doFinally` guarantees context reset on success, error, or cancellation

## 3. Architecture Comparison with Spring

| Concern | Spring | Micronaut |
|---|---|---|
| Enable mechanism | `@EnableNarrativeTrace` annotation | Auto-discovered `@Factory` on classpath |
| Configuration | Annotation attributes | `application.yml` via `@ConfigurationProperties` |
| Bean wrapping | `BeanPostProcessor` | `BeanCreatedEventListener<Any>` |
| Default bean override | Define a `narrativeContext` bean | Declare `@Bean NarrativeContext` (replaces `@Secondary`) |
| HTTP filter | Servlet `Filter` + Spring `@Configuration` | Reactive `HttpServerFilter` (`@Filter("/**")`) |
| Exporter override | `ObjectProvider<TraceExporter>` | `@Secondary` default, user `@Bean` overrides |
| Lazy resolution | `BeanFactoryAware` | `ApplicationContext` + ThreadLocal re-entrancy guard |

### Why the listener uses ApplicationContext

`BeanCreatedEventListener<Any>` fires for **all** bean creations, including the listener's own dependencies (`NarrativeContext`, `NarrativeTraceProperties`). Constructor-injecting those beans would cause a circular dependency or stack overflow. The listener resolves them lazily from `ApplicationContext` with a `ThreadLocal` re-entrancy guard that skips wrapping during dependency resolution.

## 4. Testing Micronaut Beans

For testing traced beans with JUnit 5, you don't need the Micronaut module — use `NarrativeTraceProxy` directly:

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var orders = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);
        orders.placeOrder("C-1234");
    }
}
```

Use `@MicronautTest` only when you need to test the Micronaut integration itself (auto-wiring, bean listener wrapping, HTTP filter lifecycle).

## See also

- [Installation Guide](installation-guide.md) — dependencies, integration paths, module selection
- [Configuration Guide](configuration-guide.md) — tracing levels, Micronaut configuration details
- [Spring Integration Guide](spring-integration-guide.md) — Spring equivalent of this guide
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`
