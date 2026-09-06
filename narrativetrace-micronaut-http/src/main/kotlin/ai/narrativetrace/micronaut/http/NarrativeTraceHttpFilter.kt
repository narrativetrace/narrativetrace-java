/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.event.ClientIp
import ai.narrativetrace.api.event.EnduserId
import ai.narrativetrace.api.event.HttpRoute
import ai.narrativetrace.api.event.SessionId
import ai.narrativetrace.api.event.TenantId
import ai.narrativetrace.api.event.Traceparent
import ai.narrativetrace.api.export.RequestContext
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.core.context.ContextExport
import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.core.render.TraceNamer
import io.micronaut.http.BasicHttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.MDC
import reactor.core.publisher.Mono

/**
 * Micronaut HTTP filter that captures one trace tree per request.
 *
 * INTENT: Equivalent of the servlet `NarrativeTraceFilter` but using Micronaut's reactive
 * [HttpServerFilter] API. Populates persistent MDC with trace-level fields (traceId, httpMethod,
 * httpRoute, clientIp, enduserId, sessionId, tenantId) so all downstream log messages carry them.
 *
 * Lifecycle:
 * 1. Reset the narrative context (clear stale state)
 * 2. Adopt an inbound W3C `traceparent` so a call from another service continues its trace
 * 3. Stamp request metadata and user identity onto the context
 * 4. Populate persistent MDC with trace-level fields
 * 5. Proceed through the filter chain via [Mono.from]
 * 6. Capture the accumulated trace tree
 * 7. Export via the configured [TraceExporter]
 * 8. Clear MDC and reset context (cleanup)
 *
 * @edgeCase Capture, export, MDC cleanup, context reset and [RequestContextProvider] failures are
 * each swallowed independently, `Error` subclasses included — observability must never fail
 * requests. Cleanup runs in `doFinally`, where a throw would alter the terminal signal the
 * application produced, so every step there is total.
 * @edgeCase Empty traces are not exported (no-op optimization).
 * @edgeCase An absent, malformed or forbidden `traceparent` is ignored and the request gets a
 * freshly generated trace id — a stranger's bad header must never fail a request.
 * @llmNote MDC is ThreadLocal. In reactive contexts where Reactor switches threads, MDC fields
 * set here may not be visible on other threads. Use Micrometer context-propagation for cross-thread
 * MDC if needed.
 */
@Filter("/**")
class NarrativeTraceHttpFilter(
    private val context: NarrativeContext,
    private val exporter: TraceExporter,
) : HttpServerFilter {
    @Inject
    var provider: RequestContextProvider? = null

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        val startTime = System.currentTimeMillis()
        prepareContext(request)

        return Mono
            .from(chain.proceed(request))
            .doOnSuccess { response ->
                exportTrace(response?.status?.code ?: 200, startTime)
            }.doOnError {
                exportTrace(500, startTime)
            }.doFinally {
                clearMdc()
                resetContext()
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun prepareContext(request: HttpRequest<*>) {
        try {
            context.reset()
            stampContext(request)
        } catch (_: Throwable) {
            // Observability failure must never become a request failure
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun clearMdc() {
        try {
            MDC.clear()
        } catch (_: Throwable) {
            // Observability failure must never become a request failure
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resetContext() {
        try {
            context.reset()
        } catch (_: Throwable) {
            // Observability failure must never become a request failure
        }
    }

    private fun stampContext(request: HttpRequest<*>) {
        context.adoptTraceparent(
            Traceparent.parse(request.headers.get(Traceparent.HEADER_NAME)),
        )
        val method = ContextExport.normalized(request.method.toString())
        val remoteAddress = request.remoteAddress.address?.hostAddress ?: ""
        val httpRoute = HttpRoute.of(ContextExport.normalized(routeOf(request)))
        val clientIp = ClientIp.of(ContextExport.normalized(remoteAddress))
        context.setRequestContext(method, httpRoute, clientIp)
        applyUserContext(request)
        populateMdc(method, httpRoute, clientIp)
    }

    /**
     * The route template when Micronaut has matched one, else the raw path.
     *
     * INTENT: `/orders/{id}` is one route; `/orders/1`, `/orders/2`, … are as many routes as there
     * are orders. Exported as an OTel attribute and an MDC field, the raw path is unbounded
     * cardinality in the telemetry backend, and it carries whatever identifiers the path segments
     * happen to be. An adversarial review asked for the template where one is available.
     *
     * @llmNote Falls back to `request.path` rather than to nothing: route resolution and filter
     * order are not something this filter controls, and a raw path is a worse label than a template
     * but a much better one than an absent route. Both branches are normalised by the caller.
     */
    private fun routeOf(request: HttpRequest<*>): String = BasicHttpAttributes.getUriTemplate(request).orElse(null) ?: request.path

    private fun applyUserContext(request: HttpRequest<*>) {
        val userContext = provider?.resolveUserContext(request) ?: return
        context.setUserContext(
            userContext.enduserId?.let { EnduserId.of(ContextExport.normalized(it)) },
            userContext.sessionId?.let { SessionId.of(ContextExport.normalized(it)) },
            userContext.tenantId?.let { TenantId.of(ContextExport.normalized(it)) },
        )
        userContext.enduserId?.let { MDC.put("enduserId", ContextExport.normalized(it)) }
        userContext.sessionId?.let { MDC.put("sessionId", ContextExport.normalized(it)) }
        userContext.tenantId?.let { MDC.put("tenantId", ContextExport.normalized(it)) }
    }

    /**
     * @edgeCase Values reaching MDC are already normalised by [stampContext]; MDC is printed by
     * whatever layout the host configured, and a raw newline there forges a log line a line-based
     * parser or SIEM cannot distinguish from a real one (CWE-117).
     */
    private fun populateMdc(
        method: String,
        httpRoute: HttpRoute,
        clientIp: ClientIp,
    ) {
        MDC.put("traceId", context.traceId().toString())
        MDC.put("traceName", TraceNamer.name(context.traceId().value()))
        MDC.put("httpMethod", method)
        MDC.put("httpRoute", httpRoute.toString())
        MDC.put("clientIp", clientIp.toString())
    }

    @Suppress("TooGenericExceptionCaught")
    private fun exportTrace(
        statusCode: Int,
        startTime: Long,
    ) {
        try {
            val tree = context.captureTrace()
            if (tree.isEmpty()) return
            val duration = System.currentTimeMillis() - startTime
            exporter.export(tree, RequestContext(statusCode, duration))
        } catch (_: Throwable) {
            // Observability failure must never become a request failure —
            // and this runs inside doOnSuccess/doOnError, where a throw would
            // turn the application's own terminal signal into a different one
        }
    }
}
