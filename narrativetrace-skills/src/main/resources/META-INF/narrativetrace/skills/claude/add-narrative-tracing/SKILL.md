---
name: add-narrative-tracing
description: "Installs NarrativeTrace into a Java project and gets it to a first trace. Use when NarrativeTrace is not yet installed, a project needs its very first traced call, or traces need to reach a real logger instead of standard output. Adds narrativetrace-core and narrativetrace-proxy with Gradle, wraps a class with NarrativeTraceProxy.trace, renders and runs the first trace, then wires an SLF4J/Logback consumer so traces reach your logger. Applies the doctor's framework-wiring fixes for the frameworks the project already uses, and runs narrativetrace-doctor to confirm the install is correctly wired — narrativetrace-doctor owns diagnosis from there — and ends by previewing the agent-skills install so the next session finds them. Say 'add narrative tracing to my service', 'install narrativetrace', 'get a trace in sixty seconds', 'wrap this class so I can see a trace', or 'send my traces to my logger' to invoke it."
allowed-tools: Bash(./gradlew *), Bash(git *), Bash(find *)
---

# add-narrative-tracing

## 1. Install with the real toolchain

**when:** if the project already has an application entry point — a main class, a web or application framework the doctor reports — add only the dependencies and the -parameters flag below and leave out the application plugin and the application { mainClass } block; otherwise use the block as written

```kotlin
// build.gradle.kts
plugins {
    java
    application   // only when the project has no application entry point of its own
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.3.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.3.0")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // without it, traces show arg0, arg1
}

application {   // only when the project has no application entry point of its own
    mainClass.set("com.example.orders.Main")
}
```

**verify:** ./gradlew build

**failure:** dependency resolution fails offline → a version pin drifted from what is actually published to Maven Central → check the exact coordinate against documentation/installation-guide.md's dependency block

## 2. Wire the frameworks this project already uses

```bash
./gradlew narrativetraceDoctor
```

**verify:** every config.<framework>-* finding in build/narrativetrace/doctor-report.json passes: apply each failing one's fix in order, as printed, then run the doctor again; a framework the doctor reports as having no integration shipped is left alone

**failure:** the task fails with "Task 'narrativetraceDoctor' not found" → the ai.narrativetrace Gradle plugin isn't applied to this project → add id("ai.narrativetrace") to the plugins block, or run the standalone narrativetrace-cli launcher instead

## 3. First trace: wrap, call, render, run

**when:** if the project already has an application entry point — a main class, a web or application framework the doctor reports — do not add a demo main: run the application the way it already runs, exercise one real boundary, and read that request's trace; the verify below is for the console app, and in an existing application the step is done when that request's trace is in the output; otherwise create the smallest console app as follows

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

## 4. Send it to your logger

```kotlin
dependencies {
    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.3.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
}
```

**verify:** traces reach the configured logger appender instead of standard output

**failure:** traces still print to standard output, not the logger → narrativetrace-slf4j attaches to the pipeline reflectively once it is on the runtime classpath — a compile-only or test-only dependency scope that never reaches the running classpath leaves it unattached → add narrativetrace-slf4j on runtimeOnly (or testRuntimeOnly for a test-only logger), not compileOnly

## 5. Run narrativetrace-doctor and resolve every finding

```bash
./gradlew narrativetraceDoctor
```

**verify:** the JSON report at build/narrativetrace/doctor-report.json is well-formed, naming all twenty findings

**failure:** the task fails with "Task 'narrativetraceDoctor' not found" → the ai.narrativetrace Gradle plugin isn't applied to this project → add id("ai.narrativetrace") to the plugins block, or run the standalone narrativetrace-cli launcher instead

## 6. Install the skills for next time

```bash
./gradlew narrativetraceInit --diff
```

**verify:** the diff names .agents/skills/ and AGENTS.md, and neither exists yet — nothing is written until the same command runs again without --diff; the next session verifies with narrativetrace-verify — once a change's tests are green, it reads the trace before it reports

**failure:** the task fails with "Task 'narrativetraceInit' not found" → the ai.narrativetrace Gradle plugin isn't applied to this project → add id("ai.narrativetrace") to the plugins block, or preview the same install with the standalone launcher: narrativetrace init --dry-run

## Always

- Never assume a step worked without its verify (reproduces-from-clean is the gate; a step that looks right and was never checked is exactly the failure mode the studies found)

## Never

- Never skip the narrativetrace-doctor call (it is the seam to diagnosis — every other skill's failure path names it, and skipping it here is the one place that convention would go uncompleted)
- Never apply the installer without showing its diff first (it writes into AGENTS.md and the project's skill directories, and the approval for that is a person reading the diff — run it with --diff, show the output, and let them run it again without the flag)

