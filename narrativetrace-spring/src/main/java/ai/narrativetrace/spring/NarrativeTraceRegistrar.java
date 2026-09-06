/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import ai.narrativetrace.api.event.ServiceIdentity;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registrar that installs the Spring NarrativeTrace infrastructure beans.
 *
 * <p>INTENT: This is the bridge between annotation metadata on {@link EnableNarrativeTrace} and the
 * concrete beans needed for proxying and context creation.
 *
 * <p><b>@sideEffects</b> Registers infrastructure beans named {@code
 * narrativeTraceBeanPostProcessor}, {@code narrativeContext}, and {@code narrativeTraceLoggerName}
 * when they are not already present.
 */
public class NarrativeTraceRegistrar implements ImportBeanDefinitionRegistrar {

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    var attrs = resolveAttributes(metadata);
    var basePackages = resolveBasePackages(attrs, metadata);
    var loggerName = attrs != null ? attrs.getString("loggerName") : "";
    var serviceIdentity = resolveServiceIdentity(attrs);

    registerBeanPostProcessor(registry, basePackages);
    registerNarrativeContext(registry, loggerName, serviceIdentity);
    registerLoggerName(registry, loggerName);
  }

  private void registerBeanPostProcessor(
      BeanDefinitionRegistry registry, List<String> basePackages) {
    if (!registry.containsBeanDefinition("narrativeTraceBeanPostProcessor")) {
      var bppDef = new RootBeanDefinition(NarrativeTraceBeanPostProcessor.class);
      bppDef.getConstructorArgumentValues().addIndexedArgumentValue(0, basePackages);
      bppDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
      registry.registerBeanDefinition("narrativeTraceBeanPostProcessor", bppDef);
    }
  }

  private void registerLoggerName(BeanDefinitionRegistry registry, String loggerName) {
    if (!registry.containsBeanDefinition("narrativeTraceLoggerName")) {
      var def = new RootBeanDefinition(String.class);
      def.getConstructorArgumentValues().addIndexedArgumentValue(0, loggerName);
      def.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
      registry.registerBeanDefinition("narrativeTraceLoggerName", def);
    }
  }

  private void registerNarrativeContext(
      BeanDefinitionRegistry registry, String loggerName, ServiceIdentity serviceIdentity) {
    if (registry.containsBeanDefinition("narrativeContext")) {
      return;
    }
    var contextDef = new RootBeanDefinition();
    contextDef.setFactoryMethodName("createContext");
    contextDef.setBeanClassName(NarrativeTraceContextFactory.class.getName());
    contextDef.getConstructorArgumentValues().addIndexedArgumentValue(0, loggerName);
    contextDef.getConstructorArgumentValues().addIndexedArgumentValue(1, serviceIdentity);
    registry.registerBeanDefinition("narrativeContext", contextDef);
  }

  private ServiceIdentity resolveServiceIdentity(AnnotationAttributes attrs) {
    if (attrs == null) {
      return null;
    }
    String serviceName = attrs.getString("serviceName");
    String serviceVersion = attrs.getString("serviceVersion");
    String environment = attrs.getString("environment");
    if (serviceName.isEmpty() && serviceVersion.isEmpty() && environment.isEmpty()) {
      return null;
    }
    return new ServiceIdentity(
        emptyToNull(serviceName), emptyToNull(serviceVersion), emptyToNull(environment));
  }

  private static String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }

  private AnnotationAttributes resolveAttributes(AnnotationMetadata metadata) {
    return AnnotationAttributes.fromMap(
        metadata.getAnnotationAttributes(EnableNarrativeTrace.class.getName()));
  }

  private List<String> resolveBasePackages(
      AnnotationAttributes attrs, AnnotationMetadata metadata) {
    if (attrs == null) {
      return List.of();
    }
    var declared = List.of(attrs.getStringArray("basePackages"));
    if (declared.isEmpty()) {
      var className = metadata.getClassName();
      var lastDot = className.lastIndexOf('.');
      return List.of(lastDot > 0 ? className.substring(0, lastDot) : className);
    }
    return declared;
  }
}
