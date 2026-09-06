/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring {@link BeanPostProcessor} that wraps eligible beans in tracing proxies.
 *
 * <p>INTENT: This is the infrastructure component that turns the declarative
 * {@code @EnableNarrativeTrace} switch into actual proxy-wrapped beans.
 *
 * <p><b>@llmNote</b> A bean is wrapped when its concrete class package (or any superclass package)
 * falls within the configured base packages, and at least one interface is reachable through the
 * full class hierarchy. The interface itself may live outside the base packages — only the
 * implementation class must match. Package matching uses proper boundary checks ({@code
 * pkg.equals(base) || pkg.startsWith(base + ".")}) to prevent sibling packages with a common prefix
 * from being treated as sub-packages.
 *
 * <p><b>@edgeCase</b> CGLIB-proxied beans (e.g., from {@code @Transactional} on a concrete class)
 * may not expose user-declared interfaces via {@code getClass().getInterfaces()}, causing silent
 * failure to wrap. Prefer interface-based proxying when combining with NarrativeTrace.
 */
public class NarrativeTraceBeanPostProcessor
    implements BeanPostProcessor, Ordered, BeanFactoryAware {

  private final List<String> basePackages;
  private NarrativeContext context;
  private BeanFactory beanFactory;

  /**
   * Creates the processor and looks up the {@code NarrativeContext} lazily from the BeanFactory.
   *
   * @param basePackages package prefixes whose beans are eligible for tracing
   */
  public NarrativeTraceBeanPostProcessor(List<String> basePackages) {
    this.basePackages = basePackages;
  }

  /**
   * Creates the processor with an explicit context, mainly for direct registration or tests.
   *
   * @param context the context the created proxies capture into
   * @param basePackages package prefixes whose beans are eligible for tracing
   */
  public NarrativeTraceBeanPostProcessor(NarrativeContext context, List<String> basePackages) {
    this.context = context;
    this.basePackages = basePackages;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    var beanClass = bean.getClass();

    if (!classHierarchyInBasePackages(beanClass) || isSpringConfigurationClass(beanClass)) {
      return bean;
    }

    var allInterfaces = collectAllInterfaces(beanClass);
    if (allInterfaces.length == 0) {
      return bean;
    }
    if (allInterfaces.length == 1) {
      @SuppressWarnings("unchecked")
      var iface = (Class<Object>) allInterfaces[0];
      return NarrativeTraceProxy.trace(bean, iface, context());
    }
    return NarrativeTraceProxy.trace(bean, allInterfaces, context());
  }

  /**
   * Returns {@code true} when the bean's class or any superclass carries {@code @Configuration}.
   * Spring CGLIB-enhances {@code @Configuration} classes, so the annotation lives on the original
   * class (the superclass of the synthetic CGLIB subclass). Wrapping these beans with a JDK proxy
   * would break Spring's factory-method invocation mechanism.
   */
  private boolean isSpringConfigurationClass(Class<?> beanClass) {
    for (var c = beanClass; c != null && c != Object.class; c = c.getSuperclass()) {
      if (c.isAnnotationPresent(Configuration.class)) {
        return true;
      }
    }
    return false;
  }

  private NarrativeContext context() {
    if (context == null) {
      context = beanFactory.getBean(NarrativeContext.class);
    }
    return context;
  }

  /**
   * Collects user-defined interfaces reachable from the class hierarchy (class and all
   * superclasses). JDK built-in interfaces (those with a {@code null} classloader, e.g., {@link
   * java.io.Serializable}) are excluded to prevent classloader visibility errors when creating JDK
   * dynamic proxies. Declaration order from most-derived to least-derived is preserved.
   */
  private Class<?>[] collectAllInterfaces(Class<?> beanClass) {
    var found = new java.util.LinkedHashSet<Class<?>>();
    for (var c = beanClass; c != null && c != Object.class; c = c.getSuperclass()) {
      for (var iface : c.getInterfaces()) {
        if (iface.getClassLoader() != null && !isFrameworkInterface(iface)) {
          found.add(iface);
        }
      }
    }
    return found.toArray(Class<?>[]::new);
  }

  /**
   * Returns {@code true} for interfaces from the NarrativeTrace API, core, servlet, and proxy
   * modules. These are infrastructure SPIs (e.g., {@code TraceExporter}, {@code
   * RequestContextProvider}) that should never be wrapped with a tracing proxy — wrapping them
   * would instrument framework internals rather than user business logic, and a wrapped {@code
   * RequestContextProvider} would additionally push a span of its own into the request's trace
   * before the user context it resolves has been stamped.
   *
   * <p><b>@llmNote</b> {@code ai.narrativetrace.api} is where every SPI a third party implements
   * now lives; it must stay first in this list. Omitting it is a silent defect — nothing fails to
   * compile, the SPI simply starts appearing in user traces.
   */
  private boolean isFrameworkInterface(Class<?> iface) {
    var pkg = iface.getPackageName();
    return pkg.startsWith("ai.narrativetrace.api")
        || pkg.startsWith("ai.narrativetrace.core")
        || pkg.startsWith("ai.narrativetrace.servlet")
        || pkg.startsWith("ai.narrativetrace.proxy");
  }

  /**
   * Returns {@code true} when any class in the hierarchy (excluding Object) belongs to a configured
   * base package. This allows a subclass in the base package whose superclass declares the
   * interface to be correctly wrapped.
   */
  private boolean classHierarchyInBasePackages(Class<?> beanClass) {
    if (basePackages.isEmpty()) return false;
    for (var c = beanClass; c != null && c != Object.class; c = c.getSuperclass()) {
      if (inBasePackages(c.getPackageName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether {@code packageName} is exactly a configured base package or a sub-package of
   * one. Uses a dot-boundary test to prevent sibling packages with a common prefix (e.g. {@code
   * impl} vs {@code implx}) from falsely matching.
   */
  private boolean inBasePackages(String packageName) {
    return basePackages.stream()
        .anyMatch(base -> packageName.equals(base) || packageName.startsWith(base + "."));
  }
}
