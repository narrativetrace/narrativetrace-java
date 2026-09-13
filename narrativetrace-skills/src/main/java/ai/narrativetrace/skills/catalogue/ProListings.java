/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
