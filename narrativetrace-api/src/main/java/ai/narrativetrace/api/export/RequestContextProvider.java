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
package ai.narrativetrace.api.export;

/**
 * SPI for deriving user identity fields from an inbound request.
 *
 * <p>INTENT: Implement this when authentication or tenancy data lives outside the trace context and
 * must be copied off the framework's request object once per request. HTTP integrations call it
 * from their filter; the values are stamped onto every span the request goes on to create.
 *
 * <p><b>@llmNote</b> The request type is a type parameter precisely so this interface can live in
 * the zero-dependency API jar: binding it to {@code jakarta.servlet.http.HttpServletRequest} would
 * put a servlet dependency in front of every consumer, including the ones that never serve HTTP.
 * Each integration binds it: the servlet filter and the Spring Web wiring both to {@code
 * jakarta.servlet.http.HttpServletRequest}, so an implementation registered as a Spring bean must
 * declare that binding. The Micronaut HTTP filter is the one exception — it keeps its own Kotlin
 * type on Micronaut's {@code HttpRequest}, as recorded in {@code documentation/api-surface.md}.
 *
 * <p><b>@edgeCase</b> Returning {@code null} means "no identity available for this request", not an
 * error. Integrations treat a {@code null} result and a thrown exception the same way: the request
 * proceeds with no user context, because observability must never fail a request.
 *
 * @param <REQUEST> the framework's request type the implementation reads identity from
 */
@FunctionalInterface
public interface RequestContextProvider<REQUEST> {

  /**
   * Resolves user context from one request.
   *
   * @param request Current request being processed.
   * @return User context to stamp onto future spans for this request, or {@code null} when not
   *     available.
   */
  UserContext resolveUserContext(REQUEST request);

  /**
   * User identity tuple extracted from a request.
   *
   * @param enduserId Stable user identifier, or {@code null}.
   * @param sessionId Session correlation identifier, or {@code null}.
   * @param tenantId Tenant or account scope, or {@code null}.
   */
  record UserContext(String enduserId, String sessionId, String tenantId) {}
}
