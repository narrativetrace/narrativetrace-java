/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * JDK dynamic proxy integration for interface-based tracing.
 *
 * <p>This package owns the reflection-based capture path for applications that can wrap service
 * implementations at construction time. {@link ai.narrativetrace.proxy.NarrativeTraceProxy} creates
 * proxies, and {@link ai.narrativetrace.proxy.ParameterNameResolver} turns method parameters into
 * eager {@code ParameterCapture} values.
 *
 * <p>INTENT: Prefer this module when your targets already implement interfaces and you do not need
 * bytecode instrumentation. Use the agent module when you must trace concrete classes or code you
 * do not instantiate yourself.
 */
package ai.narrativetrace.proxy;
