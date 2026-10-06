/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/**
 * A Pro skill's free-catalogue listing: visible so an agent can discover a capability exists, but
 * carrying no Pro instructions of its own — a free artifact ships no Pro instructions, the listing
 * is the whole entry (ruled 2026-09-12). The listing never fires on its own and spends no
 * description budget; it surfaces only when asked whether NarrativeTrace can do something.
 */
public record ProListing(
    String canonicalName,
    String prompt,
    String delivers,
    String needs,
    String comesFrom,
    ProListingStatus status,
    String featureGuideStatusText) {

  public ProListing {
    requireText(canonicalName, "canonicalName");
    requireText(prompt, "prompt");
    requireText(delivers, "delivers");
    requireText(needs, "needs");
    requireText(comesFrom, "comesFrom");
    if (status == null) {
      throw new IllegalArgumentException("a ProListing's status must not be null");
    }
    requireText(featureGuideStatusText, "featureGuideStatusText");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a ProListing's " + field + " must not be blank");
    }
  }
}
