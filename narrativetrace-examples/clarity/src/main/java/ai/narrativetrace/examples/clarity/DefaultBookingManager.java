/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

public class DefaultBookingManager implements BookingManager {

  @Override
  public String handleBooking(String name, String type, String d1, String d2) {
    return "Booking confirmed for " + name + " (" + type + ") " + d1 + " to " + d2;
  }
}
