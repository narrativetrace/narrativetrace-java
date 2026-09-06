/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class ReservationServiceTest {

  @Test
  void mixedNamingQualityProducesFullClarityRange(NarrativeContext context) {
    exerciseExcellentNaming(context);
    exerciseAdequateNaming(context);
    exercisePoorNaming(context);
    exerciseCohesionViolation(context);

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(narrative).contains("ReservationService.confirmReservation");
    assertThat(narrative).contains("AvailabilityChecker.findAvailableRooms");
    assertThat(narrative).contains("PaymentGateway.authorizePayment");
    assertThat(narrative).contains("BookingManager.handleBooking");
    assertThat(narrative).contains("DataProcessor.execute");
    assertThat(narrative).contains("GuestRepository.findGuestById");
    assertThat(narrative).contains("GuestRepository.renderReport");
    assertThat(narrative).contains("GuestRepository.dispatchEmail");
  }

  private void exerciseExcellentNaming(NarrativeContext context) {
    var availabilityChecker =
        NarrativeTraceProxy.trace(
            new DefaultAvailabilityChecker(), AvailabilityChecker.class, context);
    var paymentGateway =
        NarrativeTraceProxy.trace(new DefaultPaymentGateway(), PaymentGateway.class, context);
    var reservationService =
        NarrativeTraceProxy.trace(
            new DefaultReservationService(availabilityChecker, paymentGateway),
            ReservationService.class,
            context);
    var reservation =
        reservationService.confirmReservation(
            "G-1001", "deluxe", LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 18));
    assertThat(reservation.guestId()).isEqualTo("G-1001");
  }

  private void exerciseAdequateNaming(NarrativeContext context) {
    var bookingManager =
        NarrativeTraceProxy.trace(new DefaultBookingManager(), BookingManager.class, context);
    var bookingResult =
        bookingManager.handleBooking("Jane Smith", "suite", "2025-07-01", "2025-07-05");
    assertThat(bookingResult).contains("Jane Smith");
  }

  private void exercisePoorNaming(NarrativeContext context) {
    var dataProcessor =
        NarrativeTraceProxy.trace(new DefaultDataProcessor(), DataProcessor.class, context);
    var processed = dataProcessor.execute("room-data", 42);
    assertThat(processed).contains("room-data");
  }

  private void exerciseCohesionViolation(NarrativeContext context) {
    var guestRepository =
        NarrativeTraceProxy.trace(new DefaultGuestRepository(), GuestRepository.class, context);
    var guest = guestRepository.findGuestById("G-1001");
    assertThat(guest.fullName()).isEqualTo("Jane Smith");
    guestRepository.renderReport();
    guestRepository.dispatchEmail("G-1001", "Your reservation is confirmed");
  }
}
