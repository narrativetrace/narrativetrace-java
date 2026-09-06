/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.clarity;

import java.util.List;

public class DefaultAvailabilityChecker implements AvailabilityChecker {

  @Override
  public List<Room> findAvailableRooms(String roomCategory, DateRange dateRange) {
    return List.of(new Room("301", roomCategory, 189.00), new Room("405", roomCategory, 219.00));
  }
}
