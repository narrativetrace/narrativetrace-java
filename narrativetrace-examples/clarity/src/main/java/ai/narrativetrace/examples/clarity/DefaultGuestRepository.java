/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

public class DefaultGuestRepository implements GuestRepository {

  @Override
  public Guest findGuestById(String guestId) {
    return new Guest(guestId, "Jane Smith", "jane@example.com");
  }

  @Override
  public String renderReport() {
    return "<html><body>Guest Report</body></html>";
  }

  @Override
  public boolean dispatchEmail(String guestId, String message) {
    return true;
  }
}
