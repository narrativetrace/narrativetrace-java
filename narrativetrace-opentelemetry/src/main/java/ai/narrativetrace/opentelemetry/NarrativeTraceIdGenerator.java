/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenTelemetry SDK {@link IdGenerator} that gives a span NarrativeTrace starts the NarrativeTrace
 * span id it already has, so the two id spaces are one.
 *
 * <p>INTENT: With mirrored ids, any parent NarrativeTrace names — a parent that finished long
 * before its late async child started included — is exactly the OpenTelemetry span of that id, so
 * {@link OtelTraceEventListener} never has to remember finished parents or reconstruct one.
 * NarrativeTrace span ids are W3C 16-hex and trace ids 32-hex, the same formats OpenTelemetry uses.
 *
 * <p><b>@pattern</b> Spans started by {@link OtelTraceEventListener} and {@link TraceSpanExporter}
 * get their NarrativeTrace span id (and, when they have no parent, their NarrativeTrace trace id)
 * through a thread-local hand-off set around {@code startSpan}. Every other span — the
 * application's own, an instrumentation library's — gets the delegate's ids, unchanged.
 *
 * <p><b>@howToUse</b> Installed by default, opt-out {@code -Dnarrativetrace.otel.mirrorIds=false}
 * or {@code NARRATIVETRACE_OTEL_MIRROR_IDS=false}, where NarrativeTrace has a hook: an application
 * that builds its SDK with {@code opentelemetry-sdk-extension-autoconfigure} (the {@link
 * NarrativeTraceAutoConfiguration} service entry), and a Micronaut application using Micronaut's
 * OpenTelemetry tracing (the {@code narrativetrace-micronaut} module). Elsewhere it is one line:
 *
 * <pre>{@code
 * // a hand-built provider
 * SdkTracerProvider.builder().setIdGenerator(new NarrativeTraceIdGenerator()) ...
 *
 * // Spring Boot's OpenTelemetry auto-configuration
 * @Bean SdkTracerProviderBuilderCustomizer narrativeTraceIds() {
 *   return builder -> NarrativeTraceIdGenerator.installOn(builder);
 * }
 * }</pre>
 *
 * <p><b>@edgeCase</b> Not reachable for an application traced by the OpenTelemetry Java agent: the
 * agent keeps its own SDK and loads extensions only from its own extension jars, never from the
 * application classpath. There the listener detects that ids are not mirrored and parents a late
 * child on a remembered finished parent, or, past that bounded memory, on a reconstructed parent it
 * marks as synthetic.
 *
 * <p><b>@edgeCase</b> An id is mirrored once. A second span offering an id already mirrored — the
 * same span exported live by the listener and again from a captured tree by the exporter — gets the
 * delegate's id instead, and when the two came from those two different paths a one-time warning
 * says the setup double-exports. The memory of mirrored ids is bounded ({@value #RECENT_CAPACITY}
 * ids), so a repeat further apart than that is not recognised.
 */
public final class NarrativeTraceIdGenerator implements IdGenerator {

  static final int RECENT_CAPACITY = 4096;
  private static final String BUILDER_GENERATOR_FIELD = "idsGenerator";

  private final IdGenerator delegate;
  private final RecentIds mirrored = new RecentIds(RECENT_CAPACITY);
  private final AtomicBoolean doubleExportWarned = new AtomicBoolean();

  /** Mirrors NarrativeTrace's spans; every other span gets OpenTelemetry's random ids. */
  public NarrativeTraceIdGenerator() {
    this(IdGenerator.random());
  }

  /**
   * Mirrors NarrativeTrace's spans; every other span gets the delegate's ids.
   *
   * @param delegate the generator the application's own spans keep using (e.g. an X-Ray one)
   */
  public NarrativeTraceIdGenerator(IdGenerator delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    this.delegate = delegate;
  }

  /**
   * Installs the generator on a builder, wrapping the generator the builder already holds so the
   * application's own spans keep it.
   *
   * <p><b>@edgeCase</b> Reads the builder's current generator from its state; when that read is not
   * possible on the running SDK version, nothing is installed — an application's own generator is
   * never replaced blind — and the listener's runtime detection takes the not-mirrored path.
   * Idempotent: a builder already holding this generator is left as it is.
   *
   * @param builder the tracer-provider builder being configured
   * @return the same builder
   */
  public static SdkTracerProviderBuilder installOn(SdkTracerProviderBuilder builder) {
    var current = currentGenerator(builder);
    if (current == null || current instanceof NarrativeTraceIdGenerator) {
      return builder;
    }
    return builder.setIdGenerator(new NarrativeTraceIdGenerator(current));
  }

  static IdGenerator currentGenerator(SdkTracerProviderBuilder builder) {
    try {
      Field field = SdkTracerProviderBuilder.class.getDeclaredField(BUILDER_GENERATOR_FIELD);
      field.setAccessible(true);
      return (IdGenerator) field.get(builder);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  @Override
  public String generateSpanId() {
    var offer = IdHandoff.current();
    if (offer == null || offer.spanIdTaken) {
      return delegate.generateSpanId();
    }
    offer.spanIdTaken = true;
    return granted(offer) ? offer.spanId : delegate.generateSpanId();
  }

  @Override
  public String generateTraceId() {
    var offer = IdHandoff.current();
    if (offer == null || offer.traceIdTaken) {
      return delegate.generateTraceId();
    }
    offer.traceIdTaken = true;
    return granted(offer) ? offer.traceId : delegate.generateTraceId();
  }

  @Override
  public boolean generatesRandomTraceIds() {
    return delegate.generatesRandomTraceIds();
  }

  private boolean granted(IdHandoff offer) {
    if (offer.granted == null) {
      var earlier = mirrored.claim(offer.spanId, offer.source);
      offer.granted = earlier == null;
      if (earlier != null && earlier != offer.source) {
        warnDoubleExport();
      }
    }
    return offer.granted;
  }

  private void warnDoubleExport() {
    if (doubleExportWarned.compareAndSet(false, true)) {
      System.err.println( // NOPMD - one-time configuration warning, no logging dependency
          "NarrativeTrace: the same span is exported both live (OtelTraceEventListener) and from a"
              + " captured tree (TraceSpanExporter) into one OpenTelemetry SDK with mirrored ids;"
              + " the repeat got a fresh id so no two spans share one, but the backend now shows"
              + " the work twice. Use one of the two paths per span.");
    }
  }

  boolean doubleExportWarned() {
    return doubleExportWarned.get();
  }
}
