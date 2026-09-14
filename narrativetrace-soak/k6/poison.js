/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// The open-gate probe — a SEPARATE scenario file from scenario.js, run alongside it (see
// ../README.md "k6/" and run-soak.sh): one poison request every 30s for the whole run, at a
// quantity that reaches the domain unchecked and throws deep in InventoryService.reserve. Run
// with:
//   k6 run -e SOAK_POISON_DURATION=10m poison.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.SHOP_BASE_URL || 'http://shop:8080';
// Must match the shop's soak.poison.value (default 2_000_000_000, see PoisonProperties).
const POISON_VALUE = Number(__ENV.SOAK_POISON_VALUE || 2000000000);
const DURATION = __ENV.SOAK_POISON_DURATION || '10m';
const RESULTS_SUMMARY = __ENV.SOAK_POISON_SUMMARY_PATH || '/results/k6-poison-summary.json';

export const options = {
  scenarios: {
    poison: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '30s',
      duration: DURATION,
      preAllocatedVUs: 1,
      maxVUs: 2,
    },
  },
};

export default function () {
  const body = JSON.stringify({
    customerId: 'C-1234',
    lines: [{ productId: 'SKU-MECHANICAL-KB', quantity: POISON_VALUE }],
    email: 'poison-probe@example.com',
    cardNumber: '4111111111111111',
    sessionCookie: 'sess-poison-probe',
    jwt: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwb2lzb24ifQ.fake-signature-poison',
  });
  const res = http.post(`${BASE_URL}/orders`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { expected_failure: 'true', poison: 'true' },
  });
  // The shop's ShopExceptionHandler answers a poison exception with 500 and a "POISON" body code
  // — run-soak.sh's oracle counts these against /soak/stats' poisonExceptionCount.
  check(res, { 'poison request answered 500': (r) => r.status === 500 });
}

export function handleSummary(data) {
  return { [RESULTS_SUMMARY]: JSON.stringify(data, null, 2) };
}
