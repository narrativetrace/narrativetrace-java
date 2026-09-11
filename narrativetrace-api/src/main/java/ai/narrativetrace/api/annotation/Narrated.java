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
 * Custom narrative template for a traced method.
 *
 * <p>INTENT: Use this when the method name alone does not tell a good story and you want rendered
 * traces to read like domain prose.
 *
 * <p><b>This is an escape hatch, not the default.</b> The standard path is entirely derived — the
 * method's own name, parameters and outcome already tell the story. Reaching for this annotation is
 * a signal, the same one clarity scoring exists to flag: consider fixing the name before writing a
 * sentence.
 *
 * <p>The template replaces the auto-generated description with a hand-written sentence.
 * Placeholders reference method parameters by name and support one-hop property access:
 *
 * <pre>{@code
 * @Narrated("place order for {item} with quantity {quantity}")
 * OrderResult placeOrder(String item, int quantity);
 *
 * @Narrated("transfer {amount} from {source.accountId} to {target.accountId}")
 * TransferResult transfer(Account source, Account target, Money amount);
 * }</pre>
 *
 * <p>Placeholder syntax:
 *
 * <ul>
 *   <li>{@code {paramName}} — renders the parameter value via {@code ValueRenderer}
 *   <li>{@code {param.property}} — calls the getter on the raw object before serialization
 * </ul>
 *
 * <p><b>@llmNote</b> Templates are resolved against raw argument objects before eager
 * serialization. Use this instead of trying to reconstruct domain language later in a renderer.
 *
 * <p><b>@llmNote</b> The runtime's {@code TemplateParser} resolves the placeholders; it lives in
 * the runtime module and is deliberately not part of this contract — the template syntax is, its
 * parser is not.
 *
 * @see OnError
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Narrated {
  /**
   * The narrative template.
   *
   * @return Template text with placeholders such as {@code {orderId}} or {@code {customer.name}}.
   */
  String value();
}
