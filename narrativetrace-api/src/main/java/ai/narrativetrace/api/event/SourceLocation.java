/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.event;

/**
 * Source-code location captured for a traced entry ({@code narrativetrace.capture.sourceLocation},
 * default off).
 *
 * <p>INTENT: Maps to OTel semantic conventions {@code code.filepath}/{@code code.lineno}. The two
 * capture paths record different-but-honest locations — an asymmetry that is documented, not
 * hidden: the agent bakes the instrumented method's own source file and first line number (free at
 * instrumentation time), while the proxy records the caller's frame from a per-call stack walk
 * (interfaces carry no line info, and the walk is why the flag defaults off).
 *
 * @param file Source file name (e.g. {@code "OrderService.java"}), or {@code null} when unknown.
 * @param line 1-based line number, or {@code null} when unknown.
 */
public record SourceLocation(String file, Integer line) {}
