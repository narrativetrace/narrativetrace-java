/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

import java.time.LocalDate;
import java.util.UUID;

public class DefaultReservationService implements ReservationService {

  private final AvailabilityChecker availabilityChecker;
  private final PaymentGateway paymentGateway;

  public DefaultReservationService(
      AvailabilityChecker availabilityChecker, PaymentGateway paymentGateway) {
    this.availabilityChecker = availabilityChecker;
    this.paymentGateway = paymentGateway;
  }

  @Override
  public Reservation confirmReservation(
      String guestId, String roomCategory, LocalDate checkInDate, LocalDate checkOutDate) {
    var dateRange = new DateRange(checkInDate, checkOutDate);
    var rooms = availabilityChecker.findAvailableRooms(roomCategory, dateRange);
    var selectedRoom = rooms.get(0);

    var reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8);
    paymentGateway.authorizePayment(reservationId, selectedRoom.pricePerNight());

    return new Reservation(
        reservationId, guestId, selectedRoom.roomNumber(), checkInDate, checkOutDate);
  }
}
