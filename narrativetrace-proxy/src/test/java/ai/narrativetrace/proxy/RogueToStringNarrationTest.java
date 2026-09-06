/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.Test;

/**
 * A parameter whose {@code toString()} throws must never break the traced call.
 *
 * <p>{@link ai.narrativetrace.core.render.ValueRenderer} already treats a rogue {@code toString()}
 * as a known hazard and catches {@link Throwable} for it. These tests hold the template path to the
 * same contract: observability failure must never become application failure.
 */
class RogueToStringNarrationTest {

  /** A value object whose rendering blows up — a lazy proxy with a dead session, say. */
  static final class Rogue {
    @Override
    public String toString() {
      throw new IllegalStateException("toString exploded");
    }
  }

  interface NarratedService {
    @Narrated("Processing {payload}")
    String process(Rogue payload);
  }

  interface PropertyNarratedService {
    @Narrated("Processing {holder.value}")
    String process(Holder holder);
  }

  interface OnErrorService {
    @OnError(value = "Failed while processing {payload}", exception = RuntimeException.class)
    String process(Rogue payload);
  }

  record Holder(Rogue value) {}

  @Test
  void aRogueToStringInANarrationPlaceholderDoesNotBreakTheCall() {
    NarratedService real = payload -> "ok";
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, NarratedService.class, context);

    assertThat(proxy.process(new Rogue())).isEqualTo("ok");
  }

  @Test
  void aRogueToStringBehindAPropertyPlaceholderDoesNotBreakTheCall() {
    PropertyNarratedService real = holder -> "ok";
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, PropertyNarratedService.class, context);

    assertThat(proxy.process(new Holder(new Rogue()))).isEqualTo("ok");
  }

  @Test
  void aRogueToStringInAnOnErrorTemplateDoesNotMaskTheRealException() {
    var business = new IllegalArgumentException("the real failure");
    OnErrorService real =
        payload -> {
          throw business;
        };
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, OnErrorService.class, context);

    assertThatThrownBy(() -> proxy.process(new Rogue())).isSameAs(business);
  }

  @Test
  void theNarrationDegradesToATypeMarkerRatherThanVanishing() {
    NarratedService real = payload -> "ok";
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, NarratedService.class, context);

    proxy.process(new Rogue());

    var narration = context.captureTrace().roots().get(0).signature().narration();
    assertThat(narration).isEqualTo("Processing <Rogue>");
  }

  @Test
  void aToStringReturningNullAlsoDegradesToTheTypeMarker() {
    interfaceCheck();
  }

  private static void interfaceCheck() {
    NullToStringService real = payload -> "ok";
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(real, NullToStringService.class, context);

    assertThat(proxy.process(new NullToString())).isEqualTo("ok");
    assertThat(context.captureTrace().roots().get(0).signature().narration())
        .isEqualTo("Processing <NullToString>");
  }

  static final class NullToString {
    @Override
    @SuppressWarnings("ReturnNull")
    public String toString() {
      return null;
    }
  }

  interface NullToStringService {
    @Narrated("Processing {payload}")
    String process(NullToString payload);
  }
}
