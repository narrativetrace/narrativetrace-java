/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.ProListing;
import java.util.List;

/**
 * Pro skill listings surfaced from the free catalogue (ruled 2026-09-12: visible, marked "Pro",
 * status ∈ {shipped, in development, planned} agreeing with the Pro runtime's own feature guide).
 *
 * <p>Deliberately empty in this free-repo port: a listing's status must agree with the Pro
 * repository's {@code feature-guide.md} (Tier A lint), and this session's authority was
 * read-only/free-repo-only — it never opened the Pro repo to source or verify a current status.
 * Shipping a guessed "shipped"/"in development" here would be exactly the dishonest listing the
 * design forbids ("never name an unbuilt skill"). Populate this once the Pro repo's own port lands
 * and its feature guide can be cross-checked in the same change.
 */
public final class ProListings {

  public static final List<ProListing> ALL = List.of();

  private ProListings() {}
}
