/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import io.micronaut.http.HttpRequest

/**
 * SPI for resolving user identity fields from a Micronaut HTTP request.
 *
 * INTENT: Implement this interface and register as a Micronaut bean to populate enduserId,
 * sessionId, and tenantId on the narrative context and MDC for each request.
 *
 * @llmNote Deliberately a separate type from the API jar's generic
 * `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>`, which the servlet filter and the
 * Spring Web wiring bind to `HttpServletRequest`. This one is typed on Micronaut's [HttpRequest]
 * and its [UserContext] is a Kotlin data class, so folding it into the generic type would change
 * this integration's public shape for no gain to a third party — the deferral recorded in
 * `documentation/api-surface.md`.
 */
fun interface RequestContextProvider {
    /**
     * Resolves user identity from the given HTTP request.
     *
     * @param request the current Micronaut HTTP request
     * @return user context fields, or null if identity cannot be resolved
     */
    fun resolveUserContext(request: HttpRequest<*>): UserContext?

    /**
     * User identity fields extracted from an HTTP request.
     *
     * @param enduserId stable user identifier, or null
     * @param sessionId session correlation identifier, or null
     * @param tenantId tenant or account scope, or null
     */
    data class UserContext(
        val enduserId: String?,
        val sessionId: String?,
        val tenantId: String?,
    )
}
