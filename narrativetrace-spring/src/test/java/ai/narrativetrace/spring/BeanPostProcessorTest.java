/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.spring.test.AuditService;
import ai.narrativetrace.spring.test.ConcreteService;
import ai.narrativetrace.spring.test.DefaultGreetingService;
import ai.narrativetrace.spring.test.DualInterfaceService;
import ai.narrativetrace.spring.test.GreetingService;
import ai.narrativetrace.spring.test.MultiInterfaceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

class BeanPostProcessorTest {

  @Configuration
  @EnableNarrativeTrace(basePackages = "ai.narrativetrace.spring.test")
  static class TestConfig {
    @Bean
    GreetingService greetingService() {
      return new DefaultGreetingService();
    }

    @Bean
    ConcreteService concreteService() {
      return new ConcreteService();
    }
  }

  @Test
  void wrapsInterfaceBasedBeanWithTracingProxy() {
    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var service = ctx.getBean(GreetingService.class);
      var context = ctx.getBean(NarrativeContext.class);
      context.reset();

      var result = service.greet("Alice");

      assertThat(result).isEqualTo("Hello, Alice!");

      var tree = context.captureTrace();
      assertThat(tree.roots()).hasSize(1);
      var root = tree.roots().get(0);
      assertThat(root.signature().methodName()).isEqualTo("greet");
      assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue())
          .isEqualTo("\"Hello, Alice!\"");
    }
  }

  @Test
  void skipsBeansWithoutInterfaces() {
    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var service = ctx.getBean(ConcreteService.class);

      // Should still work — just not wrapped with a proxy
      assertThat(service.doWork()).isEqualTo("done");
      assertThat(service.getClass().getName()).doesNotContain("Proxy");
    }
  }

  @Configuration
  @EnableNarrativeTrace(basePackages = "com.other.package")
  static class NonMatchingConfig {
    @Bean
    GreetingService greetingService() {
      return new DefaultGreetingService();
    }
  }

  @Test
  void packageFilterControlsWhichBeansAreWrapped() {
    try (var ctx = new AnnotationConfigApplicationContext(NonMatchingConfig.class)) {
      var service = ctx.getBean(GreetingService.class);

      // Should NOT be wrapped since package doesn't match
      assertThat(service.getClass().getName()).doesNotContain("Proxy");
    }
  }

  @Test
  void prefersBasePackageInterfaceOverFrameworkInterface() {
    var context = new ThreadLocalNarrativeContext();
    var processor =
        new NarrativeTraceBeanPostProcessor(context, List.of("ai.narrativetrace.spring.test"));
    var bean = new MultiInterfaceService();

    var result = processor.postProcessAfterInitialization(bean, "multiInterfaceService");

    assertThat(result).isInstanceOf(AuditService.class);
  }

  @Test
  void wrapsAllMatchingInterfacesForMultiInterfaceBean() {
    var context = new ThreadLocalNarrativeContext();
    var processor =
        new NarrativeTraceBeanPostProcessor(context, List.of("ai.narrativetrace.spring.test"));
    var bean = new DualInterfaceService();

    var result = processor.postProcessAfterInitialization(bean, "dualInterfaceService");

    assertThat(result).isInstanceOf(GreetingService.class);
    assertThat(result).isInstanceOf(AuditService.class);

    ((GreetingService) result).greet("Alice");
    ((AuditService) result).audit("login");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);
  }

  @Test
  void implementsOrderedWithHighestPrecedence() {
    var processor =
        new NarrativeTraceBeanPostProcessor(
            new ThreadLocalNarrativeContext(), List.of("ai.narrativetrace"));

    assertThat(processor).isInstanceOf(Ordered.class);
    assertThat(processor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  @Test
  void resolvesContextLazilyFromBeanFactory() {
    var tlc = new ThreadLocalNarrativeContext();
    var beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("narrativeContext", tlc);

    var processor = new NarrativeTraceBeanPostProcessor(List.of("ai.narrativetrace.spring.test"));
    processor.setBeanFactory(beanFactory);

    var bean = new DefaultGreetingService();
    var result = processor.postProcessAfterInitialization(bean, "greetingService");

    assertThat(result).isNotSameAs(bean);
    ((GreetingService) result).greet("lazy");
    assertThat(tlc.captureTrace().roots()).hasSize(1);
  }

  @Test
  void emptyBasePackagesSkipsAllBeans() {
    var processor =
        new NarrativeTraceBeanPostProcessor(new ThreadLocalNarrativeContext(), List.of());
    var bean = new DefaultGreetingService();

    var result = processor.postProcessAfterInitialization(bean, "greetingService");

    assertThat(result).isSameAs(bean);
  }

  @Test
  void defaultsToAnnotatedClassPackageWhenBasePackagesOmitted() {
    try (var ctx =
        new AnnotationConfigApplicationContext(
            ai.narrativetrace.spring.test.DefaultPackageConfig.class)) {
      var service = ctx.getBean(GreetingService.class);
      var context = ctx.getBean(NarrativeContext.class);
      context.reset();

      service.greet("Bob");

      var tree = context.captureTrace();
      assertThat(tree.roots()).hasSize(1);
      assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("greet");
    }
  }

  @Configuration
  @EnableNarrativeTrace(basePackages = "ai.narrativetrace.spring")
  static class ApiSpiConfig {
    @Bean
    TraceExporter exporter() {
      return (tree, requestContext) -> {};
    }

    @Bean
    RequestContextProvider<String> identityResolver() {
      return request -> new RequestContextProvider.UserContext(request, null, null);
    }

    @Bean
    NarrativeRenderer renderer() {
      return tree -> "";
    }
  }

  /**
   * The SPIs live in {@code ai.narrativetrace.api} now. Wrapping one is a silent defect: nothing
   * fails to compile, the SPI simply starts pushing spans of its own into user traces — and a
   * wrapped {@code RequestContextProvider} does it before the identity it resolves is stamped, so
   * the first root of every request carries no user context.
   */
  @Test
  void neverWrapsAnApiSpiBeanEvenInsideAConfiguredBasePackage() {
    try (var ctx = new AnnotationConfigApplicationContext(ApiSpiConfig.class)) {
      assertThat(ctx.getBean(TraceExporter.class).getClass().getName()).doesNotContain("$Proxy");
      assertThat(ctx.getBean(NarrativeRenderer.class).getClass().getName())
          .doesNotContain("$Proxy");
      var provider = ctx.getBean(RequestContextProvider.class);
      assertThat(provider.getClass().getName()).doesNotContain("$Proxy");
      assertThat(provider.resolveUserContext("user-42"))
          .isEqualTo(new RequestContextProvider.UserContext("user-42", null, null));
    }
  }
}
