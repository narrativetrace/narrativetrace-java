/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@code probed-default}: a field named {@code token} inside a record this repository's deny-list
 * vocabulary has never heard of — {@code PaymentInstrument} names no pattern itself, and the
 * enclosing parameter is named {@code details}, which is not deny-listed either — is still redacted
 * at capture. Only the field name drives it: a sibling component ({@code last4}) that does not
 * match any pattern stays visible, proving the deny-list walked the record component by component
 * rather than blanking the whole parameter because of its own name (that path is {@link
 * ParameterNameRedactionProbe}).
 */
public final class FieldNameRedactionOnCustomClassProbe {

  private FieldNameRedactionOnCustomClassProbe() {}

  /**
   * A record type the deny-list vocabulary has never named; only its {@code token} component is.
   */
  record PaymentInstrument(String token, String last4) {}

  interface ChargeService {
    String charge(String orderId, PaymentInstrument details);
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    ChargeService service =
        NarrativeTraceProxy.trace(
            (orderId, details) -> "ok", ChargeService.class, new ThreadLocalNarrativeContext());
    service.charge("order-1", new PaymentInstrument("tok_live_51Hxyz", "4242"));
    String text = SlfCapture.allMessages(capture);
    if (text.contains("tok_live_51Hxyz")) {
      return "leaked";
    }
    if (!text.contains("4242")) {
      return "over-redacted";
    }
    return text.contains("[REDACTED]") ? "[REDACTED]" : "no-marker";
  }
}
