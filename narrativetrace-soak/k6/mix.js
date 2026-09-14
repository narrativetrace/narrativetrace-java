/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Pure, framework-free operation mix for the smoke profile (see ../README.md "k6/"). Framework-
// free so mix.test.js can verify the shape with `node --test`, without spinning up k6 itself —
// k6's own JS runtime (goja) does not run under plain Node either way, so this file is imported
// by both scenario.js (inside k6) and mix.test.js (inside Node): no k6-specific API here.

export const OPERATION_MIX = {
  getCatalog: 60,
  getOrderById: 20,
  postOrder: 15,
  cancelOrder: 3,
  businessFailure: 2,
};

export function totalPercent(mix) {
  return Object.values(mix).reduce((sum, value) => sum + value, 0);
}

/**
 * Picks an operation name for a uniform random draw in [0, 100). Bands are assigned in
 * Object.entries order, cumulatively — the last band absorbs any floating-point remainder so
 * every draw in [0, 100) resolves to exactly one operation.
 */
export function pickOperation(mix, draw) {
  let cursor = 0;
  for (const [name, percent] of Object.entries(mix)) {
    cursor += percent;
    if (draw < cursor) {
      return name;
    }
  }
  const names = Object.keys(mix);
  return names[names.length - 1];
}
