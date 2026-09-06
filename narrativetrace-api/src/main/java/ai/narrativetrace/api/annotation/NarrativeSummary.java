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
 * Marks a public no-arg method as the preferred summary for its declaring type.
 *
 * <p>When {@code ValueRenderer} encounters an object whose class exposes a method annotated with
 * {@code @NarrativeSummary}, it invokes that method before falling back to record rendering,
 * reflective field introspection, or {@code toString()}.
 *
 * <pre>{@code
 * public class Order {
 *     private String id;
 *     private BigDecimal total;
 *
 *     @NarrativeSummary
 *     public String narrativeSummary() {
 *         return "Order " + id + " ($" + total + ")";
 *     }
 * }
 * }</pre>
 *
 * <p>The method must be public and accept no arguments. Its return value is converted with {@link
 * String#valueOf(Object)}; if invocation fails, the runtime's {@code ValueRenderer} fallback rules
 * apply.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NarrativeSummary {}
