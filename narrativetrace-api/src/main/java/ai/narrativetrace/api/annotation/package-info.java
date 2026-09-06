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
/**
 * Annotations that shape what gets captured and how traces read.
 *
 * <p>{@link ai.narrativetrace.api.annotation.Narrated @Narrated} and {@link
 * ai.narrativetrace.api.annotation.OnError @OnError} attach human-written templates to traced
 * methods. Templates are resolved against raw argument objects before values are rendered, so
 * placeholders such as {@code {order.id}} can traverse properties safely.
 *
 * <p>{@link ai.narrativetrace.api.annotation.NotTraced @NotTraced} marks parameters as redacted.
 * The parameter name remains visible, but renderers and exporters emit a redaction marker instead
 * of the captured value. {@link
 * ai.narrativetrace.api.annotation.NarrativeSummary @NarrativeSummary} lets a type contribute a
 * concise summary string used by {@code ValueRenderer}.
 */
package ai.narrativetrace.api.annotation;
