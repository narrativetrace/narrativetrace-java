# Framework wiring snippets

The wiring lines of every framework-table row whose wiring is source-level, one section per row id.
Every fenced block is a compiled, tested fixture's text: `snippetSync` writes it, `snippetCheck`
fails the build when it drifts. Never edit a block by hand. The doctor reads this file from the
`narrativetrace-tooling` jar to print each `config.<framework>-*` fix line.

## spring

<!-- snippet: narrativetrace-spring/src/test/java/com/example/NarrativeTraceConfig.java region=wiring -->
```java
import ai.narrativetrace.spring.EnableNarrativeTrace;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
public class NarrativeTraceConfig {}
```
<!-- /snippet -->

## spring-web

<!-- snippet: narrativetrace-spring-web/src/test/java/com/example/NarrativeTraceWebConfig.java region=wiring -->
```java
import ai.narrativetrace.spring.EnableNarrativeTrace;
import ai.narrativetrace.spring.web.NarrativeTraceWebConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
@Import(NarrativeTraceWebConfiguration.class)
public class NarrativeTraceWebConfig {}
```
<!-- /snippet -->

## micronaut

<!-- snippet: narrativetrace-micronaut/src/test/resources/wiring/application.properties -->
```properties
narrativetrace.base-packages=com.example
```
<!-- /snippet -->

## servlet

<!-- snippet: narrativetrace-servlet/src/test/java/com/example/NarrativeTraceFilterSetup.java region=wiring -->
```java
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.servlet.Slf4jTraceExporter;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class NarrativeTraceFilterSetup implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent event) {
    var filter =
        new NarrativeTraceFilter(new ThreadLocalNarrativeContext(), new Slf4jTraceExporter());
    event
        .getServletContext()
        .addFilter("narrativetrace", filter)
        .addMappingForUrlPatterns(null, false, "/*");
  }
}
```
<!-- /snippet -->

## junit4

<!-- snippet: narrativetrace-junit4-example/src/test/java/ai/narrativetrace/examples/junit4/GreetingServiceTest.java -->
```java
package ai.narrativetrace.examples.junit4;

import static org.junit.Assert.assertEquals;

import ai.narrativetrace.junit4.NarrativeTraceClassRule;
import ai.narrativetrace.junit4.NarrativeTraceRule;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class GreetingServiceTest {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void greetsByName() {
    var service =
        NarrativeTraceProxy.trace(
            new DefaultGreetingService(), GreetingService.class, narrativeTrace.context());
    assertEquals("greeting message", "Hello, Alice!", service.greet("Alice"));
  }
}
```
<!-- /snippet -->

## micrometer

<!-- snippet: narrativetrace-micrometer/src/test/java/com/example/NarrativeTracePropagationSetup.java region=wiring -->
```java
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import io.micrometer.context.ContextRegistry;

public final class NarrativeTracePropagationSetup {
  private NarrativeTracePropagationSetup() {}

  /** Call once at startup, with the context your traced services use. */
  public static void register(ThreadLocalNarrativeContext context) {
    ContextRegistry.getInstance()
        .registerThreadLocalAccessor(new NarrativeTraceThreadLocalAccessor(context));
  }
}
```
<!-- /snippet -->

## opentelemetry

<!-- snippet: narrativetrace-opentelemetry/src/test/java/com/example/NarrativeTraceOtelSetup.java region=wiring -->
```java
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.opentelemetry.OtelTraceEventListener;
import io.opentelemetry.api.OpenTelemetry;

public final class NarrativeTraceOtelSetup {
  private NarrativeTraceOtelSetup() {}

  /** The context your traced services use, with every call also exported as a span. */
  public static ThreadLocalNarrativeContext tracedContext(OpenTelemetry openTelemetry) {
    var listener = new OtelTraceEventListener(openTelemetry.getTracer("narrativetrace"));
    return new ThreadLocalNarrativeContext(
        new NarrativeTraceConfig(), new DualPathPipeline(listener));
  }
}
```
<!-- /snippet -->
