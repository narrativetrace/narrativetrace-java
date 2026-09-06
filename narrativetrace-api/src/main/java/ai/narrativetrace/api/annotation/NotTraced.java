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
package ai.narrativetrace.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter, field, or record component as redacted in all rendered and exported output.
 *
 * <p>On a <b>parameter</b>, NarrativeTrace still captures the name but marks the {@code
 * ParameterCapture} as redacted. On a <b>field</b> or <b>record component</b>, reflective
 * introspection still lists the name but substitutes a redaction marker such as {@code [REDACTED]}
 * for the value — so a secret nested inside a DTO is hidden even when the whole object is traced.
 *
 * <pre>{@code
 * void authenticate(String username, @NotTraced String password);
 * // Trace output still includes "password", but its value is redacted.
 *
 * record Card(String last4, @NotTraced String pan) {}
 * // Trace shows Card(last4: "1111", pan: [REDACTED])
 * }</pre>
 *
 * <p>A custom {@code toString()} on the declaring class does not defeat it. Rendering normally
 * prefers a curated {@code toString()} over reflective introspection, but a class that declares a
 * redacted field is introspected anyway — the annotation outranks a method that was written before
 * anyone traced the class and knows nothing about it.
 *
 * <p>Use this for sensitive data (passwords, tokens, PII) that should never appear in trace files.
 * Complementary to the runtime's name-based {@code RedactionPolicy} deny-list, which redacts common
 * sensitive field names automatically; this annotation redacts fields the deny-list would not
 * recognize by name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface NotTraced {}
