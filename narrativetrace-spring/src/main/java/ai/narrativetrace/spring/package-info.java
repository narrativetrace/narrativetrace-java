/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Spring Framework integration for automatic proxy-based tracing.
 *
 * <p>{@link ai.narrativetrace.spring.EnableNarrativeTrace @EnableNarrativeTrace} imports the
 * registrar and configuration needed to register a shared {@code NarrativeContext} plus a bean post
 * processor that wraps eligible beans in JDK tracing proxies.
 *
 * <p>INTENT: Use this when your application is Spring-based and most traced services are interface
 * driven. The web filter lives in {@code narrativetrace-spring-web}; this package focuses on core
 * bean instrumentation.
 */
package ai.narrativetrace.spring;
