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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exception-specific narrative template for a traced method.
 *
 * <p>INTENT: Use this when the same method can fail for several domain reasons and you want traces
 * to preserve that distinction without post-processing exception messages.
 *
 * <p>When the method throws an exception matching {@link #exception()}, the template in {@link
 * #value()} becomes the node's error context. It uses the same placeholder rules as {@link
 * Narrated}.
 *
 * <pre>{@code
 * @OnError(value = "order {orderId} was rejected: insufficient stock",
 *          exception = InsufficientStockException.class)
 * @OnError(value = "order {orderId} failed: payment declined",
 *          exception = PaymentDeclinedException.class)
 * OrderResult placeOrder(String orderId, int quantity);
 * }</pre>
 *
 * <p><b>@llmNote</b> Matching uses assignability. A broad handler such as {@code RuntimeException}
 * can catch many failures, but a more specific matching exception wins when several annotations
 * apply.
 *
 * <p>This annotation is {@link java.lang.annotation.Repeatable @Repeatable}; multiple instances are
 * collected into {@link OnErrors}.
 *
 * @see Narrated
 * @see OnErrors
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(OnErrors.class)
public @interface OnError {
  /**
   * The narrative template for this error case.
   *
   * @return Template text describing the failure in domain language.
   */
  String value();

  /**
   * The exception type this template applies to.
   *
   * @return Exception class used for assignable matching. The default, {@link Throwable}, matches
   *     everything.
   */
  Class<? extends Throwable> exception() default Throwable.class;
}
