# See a trace in 60 seconds

[English](sixty-seconds.md) | [Español](es/sesenta-segundos.md) | [Português](pt-BR/sessenta-segundos.md) | [简体中文](zh-CN/60秒.md)

No log statements, no test framework, no file to open afterward: a plain
Java `main`, one call, and the trace appears in your terminal. Everything
below was run for real against the published artifacts on Maven Central —
the output is pasted, not imagined.

## 1. New project, add the dependency

Java 17+. Two artifacts — the runtime core and the JDK proxy that wraps a
plain interface:

```kotlin
// build.gradle.kts
plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // without it, traces show arg0, arg1
}

application {
    mainClass.set("com.example.orders.Main")
}
```

## 2. The program

An interface and an implementation — the proxy wraps the interface, so
`OrderService` needs one:

<!-- snippet: sixty-seconds/src/main/java/com/example/orders/OrderService.java -->
```java
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
  String placeOrder(String customerId, String productId, int quantity);
}
```
<!-- /snippet -->

<!-- snippet: sixty-seconds/src/main/java/com/example/orders/DefaultOrderService.java -->
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
<!-- /snippet -->

<!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->
```java
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {
  public static void main(String[] args) {
    var context = new ThreadLocalNarrativeContext();
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    service.placeOrder("C-1234", "SKU-KB", 2);

    System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
    context.reset();
  }
}
```
<!-- /snippet -->

`ThreadLocalNarrativeContext` is where a trace accumulates.
`NarrativeTraceProxy.trace(...)` wraps the real implementation behind the
interface, so every call through `service` is captured. `captureTrace()`
returns the finished tree; `IndentedTextRenderer` turns it into text.

## 3. Run it

No `./gradlew` in this directory yet — generate the wrapper once (needs
Gradle installed; see [gradle.org/install](https://gradle.org/install/)):

```bash
gradle wrapper
```

```bash
./gradlew run
```

<!-- snippet: sixty-seconds/build/narrativetrace/sixty-seconds/see-a-trace.txt mask=duration -->
```text
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 18ms
```
<!-- /snippet -->

That is the actual, unedited output of the run above. The duration (`23ms`)
is the one thing that will vary on your machine and between runs.

You did not write a single log statement. The narrative came from the
method name (`placeOrder`), the parameter names (`customerId`, `productId`,
`quantity`), and the value the method returned — nothing else.

## What just happened

- **`ThreadLocalNarrativeContext`** is the recording context — one per
  thread, holding whatever calls run on it until you read them back.
- **`NarrativeTraceProxy.trace(...)`** wraps `OrderService` in a JDK dynamic
  proxy: every call through the wrapped reference is captured before it
  reaches the real implementation.
- **`context.captureTrace()` + a renderer** turn the captured calls into
  output. `IndentedTextRenderer` is the plain-text renderer used above;
  the same tree also renders as Markdown, JSON, or a Mermaid sequence
  diagram — see [Structural Trace Format](structural-trace-format.md) and
  the [Gradle Plugin Guide](gradle-plugin-guide.md) for the other formats
  and how the plugin writes them to disk per test.

## Send it to your logger

Same project, one dependency and one config file — `Main.java` above does
not change. `narrativetrace-slf4j` attaches itself to the pipeline the
moment it is on the classpath, so this is the whole diff:

```diff
 dependencies {
     implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
     implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
+    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.2.1")
+    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
 }
```

<!-- snippet: sixty-seconds/src/main/resources/logback.xml -->
```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```
<!-- /snippet -->

```bash
./gradlew run
```

```text
22:24:53.632 TRACE [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2)
22:24:53.637 TRACE [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 8ms
```

Timing varies as before. The same trace now lands in the logging library
you already have — Logback, picked here since it's the most common SLF4J
backend — while the console line keeps printing exactly as before. Full
configuration (log levels, logger names, MDC fields) in the [Configuration
Guide, §7](configuration-guide.md#7-slf4j-configuration).

## Next

| You want | Go to |
|---|---|
| This running inside your test suite, one file per scenario, automatically | [Installation Guide](installation-guide.md) and the [Gradle Plugin Guide](gradle-plugin-guide.md) |
| To keep a value — a token, a password — out of the trace | [Privacy and Redaction](privacy-and-redaction.md) |
| To score whether your names actually read as a narrative | [Clarity Guide](clarity-guide.md) |
| Every configuration knob, JUnit/Gradle/Spring/Micronaut/SLF4J alike | [Configuration Guide](configuration-guide.md) |
| A different integration path than the JDK proxy above (Spring, Micronaut, java agent, no interface) | [Choosing an Integration](choosing-an-integration.md) |
| Something above did not work as shown | [Troubleshooting](troubleshooting.md) |
