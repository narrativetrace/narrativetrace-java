/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

/**
 * One cart line. {@code quantity} deliberately carries no invariant here — see {@link
 * OrderPlacementService} and README.md "the open-gate design": the edge validates everything else
 * and passes this value through unchecked.
 */
public record CartLine(String productId, int quantity) {}
