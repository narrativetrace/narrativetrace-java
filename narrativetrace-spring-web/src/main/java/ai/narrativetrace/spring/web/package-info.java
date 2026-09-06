/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Spring Web bridge that registers the servlet tracing filter as a bean.
 *
 * <p>{@link ai.narrativetrace.spring.web.NarrativeTraceWebConfiguration} adapts the lower-level
 * servlet module into Spring bean wiring, reusing the shared {@code NarrativeContext} from the
 * spring module and optionally plugging in custom exporters or request-context providers.
 *
 * <p>INTENT: Use this module when your app is Spring MVC or Spring Boot servlet-based and you want
 * filter registration through Spring configuration instead of manual servlet setup.
 */
package ai.narrativetrace.spring.web;
