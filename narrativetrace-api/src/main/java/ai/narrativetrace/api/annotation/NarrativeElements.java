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
 * Marks a type's {@link Iterable#iterator()} as safe to enumerate for rendering — the third
 * sanctioned hook, alongside {@code @NarrativeSummary} and a stateless leaf's own {@code
 * toString()}, through which rendering ever runs a value's own code.
 *
 * <p>Rendering enumerates a {@link Iterable} only when the type is platform-defined (a plain {@code
 * ArrayList}, a plain {@code HashMap}) or declares {@code @NarrativeElements}. Every other {@code
 * Iterable} — including a hand-rolled {@code Collection}/{@code Map} implementation, and a bare
 * {@code Iterable} with no platform origin — is rendered without ever calling {@code iterator()}: a
 * platform-collection subclass falls back to the platform ancestor's own state, a hand-rolled
 * collection is introspected like any other object, and a bare {@code Iterable} renders a bounded
 * type marker. {@code @NarrativeElements} is the author's opt-in past all of that, for a type whose
 * iteration is known to be pure.
 *
 * <pre>{@code
 * @NarrativeElements
 * public final class RecentOrders implements Iterable<Order> {
 *     public Iterator<Order> iterator() {
 *         return orders.iterator(); // pure — no lazy load, no counter, no I/O
 *     }
 * }
 * }</pre>
 *
 * <p>The elements are enumerated under the same rendering guard every other hook runs behind (so a
 * traced accessor reached from inside {@code iterator()} opens no spurious span), bounded by the
 * same collection-item cap as every other element walk, and a throwing {@code iterator()} degrades
 * to the typed failure marker rather than propagating. Declaring this annotation is a promise about
 * the iterator's purity, exactly the promise a JDK collection's own iterator already keeps by
 * construction — see the Purity Contract in {@code annotations-guide.md}.
 *
 * <p><b>@llmNote</b> Only {@link Iterable#iterator()} is invoked; {@code size()} and any other
 * member are never called because of this annotation. It applies to the exact annotated type, not
 * to its subclasses — a subtype must declare it again to be trusted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NarrativeElements {}
