# NarrativeTrace Java Annotations Guide

This guide lists all annotations available in NarrativeTrace for Java and explains when and how to use each one.

NarrativeTrace follows a **Code is the Log** philosophy: method names, parameter names, and return values should already communicate the runtime story. Keep business logic clean and expressive first, then use annotations exceptionally, not by default. Add annotations only when they provide concrete additional value, such as targeted narration, error-specific context, or sensitive-data redaction.

## Annotation Inventory

| Annotation | Module | Target | Purpose |
|---|---|---|---|
| `@Narrated` | `narrativetrace-core` | Method | Adds human-readable narration text to a traced method. |
| `@OnError` | `narrativetrace-core` | Method | Adds contextual error text when a method throws. |
| `@NotTraced` | `narrativetrace-core` | Parameter, Field, Record component | Marks a value as redacted in trace output; also honored on fields and record components during reflective introspection. |
| `@NarrativeSummary` | `narrativetrace-core` | Method | Provides custom value rendering for objects in traces. |
| `@EnableNarrativeTrace` | `narrativetrace-spring` | Type (`@Configuration`) | Enables Spring auto-proxy tracing for selected packages. |

## Core Annotations

### `@Narrated`

`@Narrated` is an escape hatch, not the standard way to add narration — the
default path is entirely derived from the method's own name, parameters, and
outcome. Reaching for a template is a signal, the same kind clarity scoring
exists to flag: it means the code is not saying what it does on its own.
Before writing one, consider whether the method name is the real problem — a
better name fixes every trace through that method, not just this one.

Use it, deliberately, on methods when you want an explicit sentence in the trace instead of relying only on method name + parameters.

```java
public interface OrderService {
    @Narrated("Placing order of {quantity} units for customer {customerId}")
    OrderResult placeOrder(String customerId, int quantity);
}
```

How it works:

- Template placeholders use parameter names (for example `{customerId}`).
- Works with proxy tracing and agent-based tracing.
- Enriches trace output at all levels with human-readable narration text.

### `@OnError`

Use `@OnError` to attach context-specific messages for exceptions.

```java
public interface PaymentService {
    @OnError(value = "Payment declined for {customerId}, amount was {amount}",
             exception = PaymentDeclinedException.class)
    @OnError(value = "Temporary payment failure for {customerId}",
             exception = ExternalServiceException.class)
    PaymentConfirmation charge(String customerId, double amount, @NotTraced String token);
}
```

How it works:

- You can declare multiple `@OnError` annotations on the same method (it is repeatable).
- If multiple match, the most specific exception type is chosen.
- A bare `@OnError("...")` is equivalent to `exception = Throwable.class`.

The `@OnErrors` container annotation exists behind repeatable `@OnError`. In normal code, you never write it directly — just stack multiple `@OnError` annotations.

### `@NotTraced`

Use `@NotTraced` on sensitive **parameters, fields, or record components** so their values are redacted everywhere.

```java
public interface AuthService {
    Session login(String username, @NotTraced String password);
}

// Also on a field or record component nested inside a traced object:
record Card(String last4, @NotTraced String pan) {}
```

How it works:

- The name stays visible; the value is replaced with `[REDACTED]` in every renderer and exporter.
- On a field or record component, redaction happens during reflective introspection, so a secret nested inside a traced DTO is hidden without erasing the whole object.
- Typical use cases: passwords, tokens, secrets, card data.

**Reflective introspection is redact-by-default, not leak-by-default.** NarrativeTrace reflects over a traced object's fields — but a built-in name-based deny-list (`RedactionPolicy`) automatically redacts common sensitive names (`password`, `cvv`, `ssn`, `token`, `secret`, `authorization`, `cardNumber`, `accountNumber`, `routingNumber`, `sessionId`, `jwt`, `cookie`, `pan`, `iban`, …), and `Map` values whose key matches, before any value is rendered. `Map` keys render through the same guarded path as any other value: key objects honor `@NotTraced` and the deny-list on their own fields (never their raw `toString()`), and string keys are sanitized and length-capped. `@NotTraced` covers sensitive fields the deny-list would not recognize by name. Override the patterns with `new ValueRenderer(…, RedactionPolicy.ofPatterns(...))` or opt out with `RedactionPolicy.DISABLED`.

**Two secrets are hidden by what they are, not only by what they are called.** The name deny-list cannot see a bearer token passed as `value`, returned as a bare `String`, or sitting unnamed in a list, so a second and independent rule looks at the bytes. Exactly three shapes are recognised: a JWT (three base64url segments whose first begins `eyJ`), a card number (13–19 digits, separators allowed, passing the Luhn checksum), and a `Set-Cookie` string (`name=value` followed by a cookie attribute such as `Path`, `Max-Age` or `HttpOnly`). Everything else renders normally — this is a short list of structural signatures, not an entropy heuristic, because a value blanked by guesswork is a hole in your narrative you cannot see. One false positive is accepted deliberately: an identifier of card-number length that happens to satisfy Luhn. An order number that does not satisfy it stays visible. `RedactionPolicy.DISABLED` turns this rule off along with the name deny-list; `RedactionPolicy.ofPatterns(...)` replaces the names only and keeps it on.

**A type's own `toString()` is never trusted while the type has state.** A class or record that declares instance fields — its own or inherited — is walked field by field, at every depth, with both redaction mechanisms consulted per field, whatever its `toString()` would have printed. Exactly two kinds of value keep their own text: a class with no instance fields at all (nothing to hide, nothing to walk), and a class the platform defines (`LocalDate`, `Duration`, `UUID`, `URI` and their kind), whose `toString()` is the JDK's format rather than application code. A class your own code declares is application code whatever it extends. The single opt-in back to curated rendering is `@NarrativeSummary` below — and even its text passes the value-shape check, the control-character escape and the length cap. Until 2026-09-11 the rule ran the opposite way, and that left a plain `Login { username, password }` with a hand-written `toString()` printing the password at depth zero, and any curated `toString()` printing nested `@NotTraced` values straight through ordinary Java stringification. The cost of the fix is real and was accepted: a value class with a pleasant `toString()` and no `@NarrativeSummary` now renders as a field dump — `Amount{currency: "EUR", units: 10}` rather than `EUR 10.00`. Add `@NarrativeSummary` to the types where the reading matters.

**Redaction survives every wrapper, at any depth.** A holder such as `Optional`, `OptionalInt`/`OptionalLong`/`OptionalDouble`, `Future`, `AtomicReference`, `AtomicReferenceArray` or a standalone `Map.Entry` prints its payload's raw `toString()` if it is treated as a value, so NarrativeTrace opens it instead and renders what it holds under exactly these rules — which then applies again to whatever *that* holds. An `AtomicReferenceArray` renders exactly as the `Object[]` holding the same elements, and a lone `Map.Entry` renders `key=value` exactly as it would inside a `Map`. `Optional<Card>` renders as `Card(number: "4111", cvv: [REDACTED])`, never as `Optional[Card[number=4111, cvv=123]]`; an empty wrapper renders as `<empty>`. This matters because `Optional<T>` is the idiomatic return type of a lookup, which is precisely where redacted data travels.

**Redaction wins over a template that names it.** `@Narrated` and `@OnError` resolve `{param.property}` paths against the raw arguments, and a path that reaches a redacted member resolves to `[REDACTED]` — at every depth, so a redacted member part-way along a path hides everything named below it too. The same holds when a placeholder names the whole object rather than a path into it: `{card}` renders `Card(number: "4111", cvv: [REDACTED])`, never the object's own `toString()`, which knows nothing about `@NotTraced`. A placeholder naming an object always renders it through the same renderer that captures it, so a template and a captured argument agree on what the value looks like: a record narrates structurally, as `Money(currency: "EUR", amount: 10)`, in both. A plain class narrates structurally too, as `Amount{currency: "EUR", units: 10}`: "hides nothing" was never a fact the renderer could establish, only "no `@NotTraced` member *here*". To choose the narration yourself — for a record or for anything else — give the type a `@NarrativeSummary` method, which is honoured here exactly as it is everywhere else. Naming a path, or an object, never weakens the rules that apply to the value directly. The third form of placeholder obeys the same two rules: a bare `{name}` naming a value directly is answered by the deny-list reading that key exactly as it reads a field name, and by the value's own shape, so `@Narrated("login {password}")` and a JWT arriving as `{value}` both render `[REDACTED]`. If you need the value in a narrative, remove `@NotTraced` from the component; that removal is the deliberate, reviewable decision, and it shows up in the diff.

### `@NarrativeSummary`

Use `@NarrativeSummary` on a zero-argument method that returns a short summary string for value rendering.

```java
public record Customer(String id, String name, CustomerTier tier) {
    @NarrativeSummary
    public String toNarrativeSummary() {
        return "Customer[id=%s, tier=%s]".formatted(id, tier);
    }
}
```

How it works:

- `ValueRenderer` looks for a public method annotated with `@NarrativeSummary` and no parameters.
- If found, that method's output is used in traces — after the value-shape check, the
  control-character escape and the length cap, so a summary that interpolates a bearer
  token still renders `[REDACTED]`. Your intent chooses the text; it does not exempt the bytes.
- If the method throws, the value renders as `<error: IllegalStateException>` — the exception's
  type name and nothing else. The message is excluded on purpose: it routinely interpolates the
  value that failed to format.
- If not found, rendering walks the object's fields. A type with no instance fields, or one the
  platform defines, keeps its own `toString()` instead.

## The Purity Contract — Side Effects During Tracing

NarrativeTrace may invoke a small, fixed set of code paths on your objects while rendering
a trace. Keep those members **pure** — free of side effects such as lazy loading, access
counters, cache population, or I/O — exactly as you would for a debugger or a serializer.

What is invoked, and what is not:

- **Field introspection never calls your code.** For any object that has state,
  `ValueRenderer` reads its *fields* reflectively — a pure memory read. A getter that
  increments a counter or lazily loads data is not touched by introspection. This is now
  the default path rather than the fallback: since 2026-09-11 a custom `toString()` no
  longer stands in for introspection on a type that has fields, so *fewer* of your members
  run during rendering than before, not more.
- **What NarrativeTrace does invoke:** a `@NarrativeSummary` method, the `toString()` of a
  type with no instance fields, record component accessors, and any property path you name
  in a `@Narrated`/`@OnError` template (`{order.total}` resolves by calling the direct accessor method `total()` first,
  then the JavaBean getter `getTotal()`). These are the only places user code runs during
  rendering.
- **Invocation is bounded and isolated.** Output is capped (string length, collection
  items, introspection depth), a throwing getter, summary or `toString()` can never fail
  the traced business call (templates fall back to the literal `{placeholder}`; the failed
  part of a rendering falls back to `<error: TypeName>`, naming the exception's type and
  never its message), and values are rendered eagerly at the call site — any side
  effect happens once, at a deterministic point, on the calling thread.
- **Rendering never forces deferred computation.** A `Future` is only unwrapped when it
  `isDone()`; nothing is blocked on or triggered.

If a member cannot be pure, annotate it `@NotTraced` — a redacted member's value is never
read at all — or give the type a `@NarrativeSummary` so you control exactly what is
accessed. A curated `toString()` no longer serves this purpose on a type that has fields:
it is not called, precisely so that it cannot print past a redaction. At `TracingLevel.OFF` (and for parameter values at `SUMMARY`),
no argument rendering happens at all, so no user code is touched in the hot path.

## Spring Annotations

### `@EnableNarrativeTrace`

Use `@EnableNarrativeTrace` on a Spring configuration class to auto-wrap beans with NarrativeTrace proxies.

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.orders", "com.example.payments"})
public class AppConfig {
}
```

Attributes:

- `basePackages` — package prefixes to limit which beans are wrapped. Defaults to the annotated class's package.
- `loggerName` — SLF4J logger name for trace events (default `"narrativetrace"`). When `narrativetrace-slf4j` is on the classpath, the auto-created context narrates through SLF4J under this name (`Slf4jTraceEventListener` on the pipeline's synchronous path). Set to `""` to disable narration.

How it works:

- Registers NarrativeTrace Spring configuration and bean post-processor.
- Only beans in `basePackages` are considered.
- Beans must implement interfaces to be proxied (JDK dynamic proxies).
- When `narrativetrace-slf4j` is on the classpath, the auto-created context narrates through SLF4J automatically. Define your own `narrativeContext` bean to override.

## Complete Example

```java
public interface TransferService {
    @Narrated("Transferring {amount} from {fromAccountId} to {toAccountId}")
    @OnError(value = "Transfer rejected for source account {fromAccountId}",
             exception = IllegalStateException.class)
    TransferResult transfer(
            String fromAccountId,
            String toAccountId,
            double amount,
            @NotTraced String authToken
    );
}
```

This single method combines narration, targeted error context, and parameter redaction.

## Template Validation

`@Narrated` and `@OnError` templates use `{paramName}` and `{param.property}` placeholders. If a placeholder doesn't match any parameter — for example `{custmerId}` instead of `{customerId}` — the literal `{custmerId}` survives in the resolved trace output.

NarrativeTrace detects this automatically during tests. After each test, the JUnit 5 extension and JUnit 4 rule scan every trace node for unresolved `{...}` patterns and print a warning:

```
WARNING: Unresolved template placeholder(s) detected:
  - OrderService.placeOrder: {custmerId} in narration
  - ExpenseService.pay: {expense.payer.name} in narration
      ↳ nested path not supported — only one property level resolves (e.g. {object.property})
```

Only a single property level resolves: `{expense.payer}` calls `payer()` (or `getPayer()`) on the `expense` argument. A deeper path such as `{expense.payer.name}` can never resolve — the parser treats `payer.name` as one missing accessor — so the placeholder survives literally at runtime (graceful, never throws). Because this is a structural authoring error rather than a value that happened to be `null`, the test-time validation flags multi-level paths with the extra `nested path not supported` hint.

**A redacted path resolves to `[REDACTED]`** — not to the value, and not to the literal placeholder. A placeholder naming the object itself (`{card}`) is refused the same way: NarrativeTrace renders the object without its redacted members rather than calling its `toString()`. Both halves of the redaction rule apply along the path: `@NotTraced` on a field or record component, and the name-based deny-list (`password`, `cvv`, `token`, …) on a property no annotation covers. Because it is the whole path that is refused, this holds for a multi-level path that could never resolve anyway: `{order.card.cvv}` renders `[REDACTED]` rather than surviving literally. A placeholder that names no parameter, or a property its owner does not declare, is still preserved literally — that is an authoring typo, no value stands behind it, and the warning above must still fire.

No configuration is needed — warnings appear in console output whenever the extension or rule is active. This catches typos in placeholder names (for example `{order.stauts}`) and unresolvable multi-level paths as soon as a test exercises the annotated method.

## See also

- [Installation Guide](installation-guide.md) — dependencies, integration paths, trace output setup
- [Configuration Guide](configuration-guide.md) — tracing levels, JUnit/Gradle/Spring/SLF4J configuration
- [Clarity Guide](clarity-guide.md) — scoring model, NLP components, JUnit integration
