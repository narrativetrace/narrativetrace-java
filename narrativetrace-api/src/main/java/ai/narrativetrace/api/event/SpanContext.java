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
 * Immutable span correlation record attached to enter and exit events.
 *
 * <p>INTENT: This is the interoperability boundary between NarrativeTrace and downstream systems
 * such as exporters or OpenTelemetry bridges. It packages span identity together with service,
 * request, and user fields known at capture time.
 *
 * <p><b>@llmNote</b> Values are captured when the span is created. Changing request or user context
 * later does not retroactively mutate existing {@code SpanContext} instances. Identity fields use
 * typed records ({@link TraceId}, {@link SpanId}). Request/user context fields use typed records
 * ({@link HttpRoute}, {@link ClientIp}, {@link EnduserId}, {@link SessionId}, {@link TenantId})
 * that serve as extension points for pro-tier privacy operations (IP anonymization, erasure,
 * pseudonymization).
 *
 * @param traceId W3C trace-id as a validated {@link TraceId}.
 * @param spanId W3C span id as a validated {@link SpanId}.
 * @param parentSpanId Parent span id, or {@code null} for a root span.
 * @param traceFlags Raw W3C trace-flags byte.
 * @param traceState Optional W3C tracestate header value.
 * @param serviceName Logical service name to attribute the span to.
 * @param serviceVersion Deployment or artifact version associated with the span.
 * @param environment Environment label such as {@code production} or {@code test}.
 * @param httpMethod Request verb copied onto the span at creation time.
 * @param httpRoute Request route or path copied onto the span at creation time.
 * @param clientIp Client network address copied onto the span at creation time.
 * @param enduserId End-user identity copied onto the span at creation time.
 * @param sessionId Session identity copied onto the span at creation time.
 * @param tenantId Tenant or account scope copied onto the span at creation time.
 * @param spanName Optional display name for exporters that want one.
 * @param storyId Story identifier derived from the root method call (e.g. {@code
 *     "OrderService.placeOrder"}), or {@code null} when not yet derived.
 * @param chapterId Chapter identifier for this service's contribution to the story (e.g. {@code
 *     "OrderService.placeOrder:PaymentService.charge"}), or {@code null} when not yet derived.
 * @param traceAnchor Wall-clock anchor captured when this span's trace started locally, or {@code
 *     null} when the creating context predates anchoring. Shared by every span of one trace so
 *     mapped timestamps drift by at most the trace's duration, not the process uptime.
 * @param hostName Auto-detected host name (resource tier), or {@code null} when {@code
 *     narrativetrace.capture.resource} is disabled or detection failed.
 * @param processPid Auto-detected process id (resource tier), or {@code null} when resource capture
 *     is disabled.
 * @param runtimeVersion Auto-detected Java runtime version (resource tier), or {@code null} when
 *     resource capture is disabled.
 */
public record SpanContext(
    TraceId traceId,
    SpanId spanId,
    SpanId parentSpanId,
    int traceFlags,
    String traceState,
    String serviceName,
    String serviceVersion,
    String environment,
    String httpMethod,
    HttpRoute httpRoute,
    ClientIp clientIp,
    EnduserId enduserId,
    SessionId sessionId,
    TenantId tenantId,
    String spanName,
    String storyId,
    String chapterId,
    TraceAnchor traceAnchor,
    String hostName,
    Long processPid,
    String runtimeVersion) {

  /** Compatibility constructor for call sites that carry no trace anchor. */
  public SpanContext(
      TraceId traceId,
      SpanId spanId,
      SpanId parentSpanId,
      int traceFlags,
      String traceState,
      String serviceName,
      String serviceVersion,
      String environment,
      String httpMethod,
      HttpRoute httpRoute,
      ClientIp clientIp,
      EnduserId enduserId,
      SessionId sessionId,
      TenantId tenantId,
      String spanName,
      String storyId,
      String chapterId) {
    this(
        traceId,
        spanId,
        parentSpanId,
        traceFlags,
        traceState,
        serviceName,
        serviceVersion,
        environment,
        httpMethod,
        httpRoute,
        clientIp,
        enduserId,
        sessionId,
        tenantId,
        spanName,
        storyId,
        chapterId,
        null,
        null,
        null,
        null);
  }

  /**
   * Validates the required identity fields. {@link TraceId} and {@link SpanId} self-validate at
   * construction, so this compact constructor only enforces non-null.
   */
  public SpanContext {
    if (traceId == null) {
      throw new IllegalArgumentException("traceId must not be null");
    }
    if (spanId == null) {
      throw new IllegalArgumentException("spanId must not be null");
    }
  }

  /** Creates a builder with the required trace id and span id. */
  public static Builder builder(TraceId traceId, SpanId spanId) {
    return new Builder(traceId, spanId);
  }

  /** Returns whether the W3C sampled flag (bit 0 of {@link #traceFlags()}) is set. */
  public boolean sampled() {
    return (traceFlags & 1) != 0;
  }

  /** Builder for {@link SpanContext} with required ids and optional metadata fields. */
  public static final class Builder {

    private final TraceId traceId;
    private final SpanId spanId;
    private SpanId parentSpanId;
    private int traceFlags;
    private String traceState;
    private String serviceName;
    private String serviceVersion;
    private String environment;
    private String httpMethod;
    private HttpRoute httpRoute;
    private ClientIp clientIp;
    private EnduserId enduserId;
    private SessionId sessionId;
    private TenantId tenantId;
    private String spanName;
    private String storyId;
    private String chapterId;
    private TraceAnchor traceAnchor;
    private String hostName;
    private Long processPid;
    private String runtimeVersion;

    Builder(TraceId traceId, SpanId spanId) {
      this.traceId = traceId;
      this.spanId = spanId;
    }

    public Builder parentSpanId(SpanId parentSpanId) {
      this.parentSpanId = parentSpanId;
      return this;
    }

    public Builder traceFlags(int traceFlags) {
      this.traceFlags = traceFlags;
      return this;
    }

    public Builder traceState(String traceState) {
      this.traceState = traceState;
      return this;
    }

    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Builder serviceVersion(String serviceVersion) {
      this.serviceVersion = serviceVersion;
      return this;
    }

    public Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    public Builder httpMethod(String httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    public Builder httpRoute(HttpRoute httpRoute) {
      this.httpRoute = httpRoute;
      return this;
    }

    public Builder clientIp(ClientIp clientIp) {
      this.clientIp = clientIp;
      return this;
    }

    public Builder enduserId(EnduserId enduserId) {
      this.enduserId = enduserId;
      return this;
    }

    public Builder sessionId(SessionId sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    public Builder tenantId(TenantId tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder spanName(String spanName) {
      this.spanName = spanName;
      return this;
    }

    public Builder storyId(String storyId) {
      this.storyId = storyId;
      return this;
    }

    public Builder chapterId(String chapterId) {
      this.chapterId = chapterId;
      return this;
    }

    public Builder traceAnchor(TraceAnchor traceAnchor) {
      this.traceAnchor = traceAnchor;
      return this;
    }

    /** Stamps the auto-detected process resource identity onto the span. */
    public Builder resourceIdentity(ResourceIdentity resourceIdentity) {
      if (resourceIdentity != null) {
        this.hostName = resourceIdentity.hostName();
        this.processPid = resourceIdentity.processPid();
        this.runtimeVersion = resourceIdentity.runtimeVersion();
      }
      return this;
    }

    /** Builds the {@link SpanContext}, validating trace and span ids. */
    public SpanContext build() {
      return new SpanContext(
          traceId,
          spanId,
          parentSpanId,
          traceFlags,
          traceState,
          serviceName,
          serviceVersion,
          environment,
          httpMethod,
          httpRoute,
          clientIp,
          enduserId,
          sessionId,
          tenantId,
          spanName,
          storyId,
          chapterId,
          traceAnchor,
          hostName,
          processPid,
          runtimeVersion);
    }
  }
}
