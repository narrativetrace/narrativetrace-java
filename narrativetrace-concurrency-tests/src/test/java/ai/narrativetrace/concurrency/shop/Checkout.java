/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import java.util.List;

/**
 * The request-side service: it is called on the request thread and hands its work to others. The
 * span it opens is the "submitting span" every worker's calls must nest under.
 */
public interface Checkout {

  String place(String requestId, List<String> skus);
}
