/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.servlet.NarrativeTraceFilter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The servlet row's wiring snippet, run against a recording servlet container: the listener the
 * doctor's fix line and the docs show hands the container a {@link NarrativeTraceFilter} and maps
 * it to every request.
 */
class NarrativeTraceFilterSetupTest {

  private final List<Object> addedFilters = new ArrayList<>();
  private final List<String> mappings = new ArrayList<>();

  @Test
  void registersTheFilterForEveryRequest() {
    new NarrativeTraceFilterSetup().contextInitialized(new ServletContextEvent(container()));

    assertThat(addedFilters).singleElement().isInstanceOf(NarrativeTraceFilter.class);
    assertThat(mappings).containsExactly("/*");
  }

  private ServletContext container() {
    FilterRegistration.Dynamic registration =
        proxy(
            FilterRegistration.Dynamic.class,
            (method, args) -> {
              if ("addMappingForUrlPatterns".equals(method)) {
                mappings.addAll(Arrays.asList((String[]) args[2]));
              }
              return null;
            });
    return proxy(
        ServletContext.class,
        (method, args) -> {
          if ("addFilter".equals(method)) {
            addedFilters.add(args[1]);
            return registration;
          }
          return null;
        });
  }

  private interface Recorder {
    Object call(String method, Object[] args);
  }

  private static <T> T proxy(Class<T> type, Recorder recorder) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (self, method, args) -> recorder.call(method.getName(), args)));
  }
}
