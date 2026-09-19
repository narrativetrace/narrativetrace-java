---
name: add-narrative-tracing
description: "Installs NarrativeTrace into a Java project and gets it to a first trace. Use when NarrativeTrace is not yet installed, a project needs its very first traced call, or traces need to reach a real logger instead of standard output. Adds narrativetrace-core and narrativetrace-proxy with Gradle, wraps a class with NarrativeTraceProxy.trace, renders and runs the first trace, then wires an SLF4J/Logback consumer so traces reach your logger. Ends by running narrativetrace-doctor to confirm the install is correctly wired — narrativetrace-doctor owns diagnosis from there. Say 'add narrative tracing to my service', 'install narrativetrace', 'get a trace in sixty seconds', 'wrap this class so I can see a trace', or 'send my traces to my logger' to invoke it."
---

# add-narrative-tracing

## 1. Install with the real toolchain

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
    implementation("ai.narrativetrace:narrativetrace-core:0.2.4")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.4")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // without it, traces show arg0, arg1
}

application {
    mainClass.set("com.example.orders.Main")
}
```

**verify:** ./gradlew build

**failure:** dependency resolution fails offline → a version pin drifted from what is actually published to Maven Central → check the exact coordinate against documentation/installation-guide.md's dependency block

## 2. First trace: wrap, call, render, run

<!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->
```java
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {

  // snippet:begin fixedTraceparent
  // A fixed W3C traceparent, adopted so this page's embedded output always names the same trace.
  // A real run generates a random one every time (never this — it is this DEMO's own constant,
  // not the library default) via the same mechanism a filter uses for an inbound request header.
  static final String DEMO_TRACEPARENT = "00-a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4-a1b2c3d4a1b2c3d4-01";

  // snippet:end fixedTraceparent

  public static void main(String[] args) {
    var context = new ThreadLocalNarrativeContext();
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    service.placeOrder("C-1234", "SKU-KB", 2);

    System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
    context.reset();
  }
}
```
<!-- /snippet -->

**verify:** ./gradlew run

## 3. Send it to your logger

```kotlin
dependencies {
    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.2.4")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
}
```

**verify:** traces reach the configured logger appender instead of standard output

**failure:** traces still print to standard output, not the logger → narrativetrace-slf4j attaches to the pipeline reflectively once it is on the runtime classpath — a compile-only or test-only dependency scope that never reaches the running classpath leaves it unattached → add narrativetrace-slf4j on runtimeOnly (or testRuntimeOnly for a test-only logger), not compileOnly

## 4. Run narrativetrace-doctor and resolve every finding

```bash
./gradlew narrativetraceDoctor
```

**verify:** the JSON report at build/narrativetrace/doctor-report.json is well-formed, naming all eleven findings

**failure:** the task fails with "Task 'narrativetraceDoctor' not found" → the ai.narrativetrace Gradle plugin isn't applied to this project → add id("ai.narrativetrace") to the plugins block, or run the standalone narrativetrace-cli launcher instead

## Always

- Never assume a step worked without its verify (reproduces-from-clean is the gate; a step that looks right and was never checked is exactly the failure mode the studies found)

## Never

- Never skip the final narrativetrace-doctor call (it is the seam to diagnosis — every other skill's failure path names it, and skipping it here is the one place that convention would go uncompleted)

