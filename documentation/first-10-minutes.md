# First 10 minutes

One tiny service, one JUnit test, eight steps. Every command below was run
for real against this version of the repository — the file paths, the
clarity scores, the assertion message and the `[REDACTED]` marker are actual
output, not illustrations. The only things that will differ on your machine
are the duration (`ms`) and the two-word `trace_name`, both of which are
generated fresh on every run.

Java 17+, the Gradle plugin, JUnit 5. If you have not run the demo yet,
`./demo.sh --example ecommerce --no-pause` from the repository root is faster
still — this page is for when you want to see it against *your own* code.

## 1. Add the plugin

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

That is the entire dependency setup: the plugin adds `narrativetrace-core`,
`-proxy`, `-clarity`, `-diagrams` and `-junit5`, the `-parameters` compiler
flag, and the JUnit Platform engine.

## 2. Add one service interface and implementation

```java
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
    String placeOrder(String customerId, String productId, int quantity);
}
```

```java
// src/main/java/com/example/orders/DefaultOrderService.java
package com.example.orders;

public class DefaultOrderService implements OrderService {
    @Override
    public String placeOrder(String customerId, String productId, int quantity) {
        return "ORD-" + customerId + "-" + productId + "-" + quantity;
    }
}
```

An interface, because the JDK proxy this tutorial uses wraps interfaces. The
[integration matrix](choosing-an-integration.md) has the path for services
that do not have one.

## 3. Add one JUnit test

```java
// src/test/java/com/example/orders/OrderServiceTest.java
package com.example.orders;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

`NarrativeTraceExtension` injects `context`; `NarrativeTraceProxy.trace(...)`
wraps the real implementation behind the interface, so every call to
`service` is captured.

## 4. Run the suite

```bash
./gradlew test
```

Output lands in `build/narrativetrace/` — the same shape the plugin promises
and nothing more, one file per scenario per format:

```text
build/narrativetrace/
   |
   +-- traces/OrderServiceTest/customer_places_order.md    human narrative
   +-- traces/OrderServiceTest/customer_places_order.json  same trace, canonical JSON
   +-- diagrams/OrderServiceTest/customer_places_order.mmd Mermaid sequence diagram
   +-- structural/OrderServiceTest/customer_places_order.nt value-free shape (last-green baseline)
   +-- clarity-report.md                                   naming feedback for the whole suite
   +-- clarity-results.json                                same scores, for clarityCheck
```

`OrderServiceTest.customerPlacesOrder` became `customer_places_order` — the
test method name, humanized and slugged. Nothing else to configure.

> NarrativeTrace also prints a live summary to the test process's standard
> output, which Gradle by default routes only into the XML/HTML report — a
> plain `./gradlew test` shows nothing in your terminal even though the files
> above are written correctly. To see it live, see
> [Troubleshooting](troubleshooting.md#i-dont-see-anything-in-my-terminal).

## 5. Open the narrative

`build/narrativetrace/traces/OrderServiceTest/customer_places_order.md`:

```markdown
---
type: trace
scenario: Customer places order
entry_point: OrderService.placeOrder
duration_ms: 3.973
trace_id: be4e2e8ea4dfcb18ccc43fbb59223bcc
trace_name: rural ivory heaps
method_count: 1
error_count: 0
---

## Trace: OrderService.placeOrder

**Scenario:** Customer places order
**Duration:** 3.973ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`) → `"ORD-C-1234-SKU-KB-2"` — 3.973ms
```

Every value in the call flow — the parameter values, the return value — came
from the call you actually made. Nothing was written by hand.

## 6. Rename `placeOrder` to `process` and watch clarity drop

Naming quality is measured, not asserted. Before the rename,
`clarity-report.md` read:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.89 |

| Element | Score | Note |
|---------|-------|------|
| `OrderService.placeOrder` | 0.81 | Standard verb 'place' + broad noun 'order' |
```

Rename the method (interface, implementation, and the call site) to
`process` and run `./gradlew test` again:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.68 |

| Category | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Method Names | 0.10 | 0.30 | 0.03 |
| Class Names | 0.91 | 0.20 | 0.18 |
| Parameter Names | 0.92 | 0.25 | 0.23 |
| Structural | 1.00 | 0.15 | 0.15 |
| Cohesion | 0.90 | 0.10 | 0.09 |
| **Overall** | **0.68** | | |

| Severity | Category | Element | Suggestion |
|----------|----------|---------|------------|
| HIGH | method-name | `OrderService.process` | Use a domain-specific verb+noun (e.g., calculateTotal, reserveInventory) |
```

Same call, same values, same everything but the name — the overall score
fell from 0.89 to 0.68, the method-name dimension alone fell from 0.81 to
0.10, and a HIGH-severity issue appeared. This is the mechanism behind
`clarityCheck`: set a threshold in CI and a generic name fails the build
instead of quietly shipping. See the [Clarity Guide](clarity-guide.md) for
the full scoring model. Rename it back to `placeOrder` (or to something even
more specific) before continuing.

## 7. Add `@NotTraced` and see redaction

```java
public interface OrderService {
    String placeOrder(
        String customerId, String productId, int quantity, @NotTraced String paymentToken);
}
```

Because the plugin's default `scope` is `"test"`, the NarrativeTrace jars —
including `narrativetrace-api`, where `@NotTraced` lives — sit on
`testImplementation` only, not on the main compile classpath. Reference the
annotation from a `src/main/java` interface and `./gradlew test` fails to
*compile* with "package ai.narrativetrace.api.annotation does not exist"
before it ever runs a test. Add the API jar to `compileOnly` to fix it (it
carries no runtime dependencies of its own):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.1")
}
```

Pass a token in the test (`service.placeOrder("C-1234", "SKU-KB", 2,
"tok_live_51H8x9J")`) and run again. The trace:

```text
- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`, paymentToken: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 9.428ms
```

The parameter name still appears — you can see a token *was* passed — but
its value never reaches disk. See
[Privacy and Redaction](privacy-and-redaction.md) for what else redaction
covers and the two ways it can be turned off.

## 8. Turn on approval mode and see `.received.nt`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

Run `./gradlew test` with no committed baseline yet, and the passing test
fails anyway:

```text
java.lang.AssertionError: No approved narrative for scenario "Customer places order".
Received: src/test/narratives/OrderServiceTest/customer_places_order.received.nt
Review it and approve via the approveNarratives task (or rename it to customer_places_order.approved.nt).
```

`customer_places_order.received.nt` holds the value-free shape of the call:

```text
scenario: Customer places order

- OrderService.placeOrder(customerId, productId, quantity, paymentToken) → value
```

Review it, then promote it:

```bash
./gradlew approveNarratives
# Approved: src/test/narratives/OrderServiceTest/customer_places_order.approved.nt
```

`./gradlew test` now passes. From here on, any structural change to this
scenario — a new call, a dropped call, a changed outcome kind — fails the
build with a readable diff until someone reviews and re-approves it. Full
detail, including what counts as a "structural change," is in the
[Structural Trace Format](structural-trace-format.md).

## Where to go next

| You want | Go to |
|---|---|
| A different integration path than the JDK proxy above | [Choosing an Integration](choosing-an-integration.md) |
| The row-by-row privacy contract | [Privacy and Redaction](privacy-and-redaction.md) |
| Which generated files to commit | [What to Commit](what-to-commit.md) |
| Something above did not work as shown | [Troubleshooting](troubleshooting.md) |
| Every configuration knob | [Configuration Guide](configuration-guide.md) |
