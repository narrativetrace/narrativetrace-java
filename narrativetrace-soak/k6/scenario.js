/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Main soak traffic — see ../README.md "k6/" for the profile design. Run with:
//   k6 run -e SOAK_PROFILE=smoke scenario.js
//   k6 run -e SOAK_PROFILE=one-hour scenario.js
//   k6 run -e SOAK_PROFILE=two-hour scenario.js   (exported, not run in P1)
// poison.js is a SEPARATE script, run alongside this one (see run-soak.sh) — its one-poison-
// request-every-30s traffic is not part of this file's operation mix.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { OPERATION_MIX, pickOperation } from './mix.js';
import { PROFILES } from './profiles.js';

const BASE_URL = __ENV.SHOP_BASE_URL || 'http://shop:8080';
const PROFILE = __ENV.SOAK_PROFILE || 'smoke';
const RESULTS_SUMMARY = __ENV.SOAK_SUMMARY_PATH || '/results/k6-scenario-summary.json';

const piiSeed = new SharedArray('pii-seed', function () {
  return JSON.parse(open('./pii-seed.json'));
});

const CATALOG_PRODUCTS = ['SKU-MECHANICAL-KB', 'SKU-MOUSE-PAD', 'SKU-USB-HUB'];

export const options = PROFILES[PROFILE] || PROFILES.smoke;

function randomPii() {
  return piiSeed[Math.floor(Math.random() * piiSeed.length)];
}

function randomProduct() {
  return CATALOG_PRODUCTS[Math.floor(Math.random() * CATALOG_PRODUCTS.length)];
}

// 1-20 lines, some large enough to hit value-rendering caps (a 20-line cart is the max the edge
// allows — see web.PlaceOrderRequest's @Size(max = 20)).
function randomCart() {
  const lineCount = 1 + Math.floor(Math.random() * 20);
  const lines = [];
  for (let i = 0; i < lineCount; i++) {
    lines.push({ productId: randomProduct(), quantity: 1 + Math.floor(Math.random() * 5) });
  }
  return lines;
}

function orderBody(customerId, lines, pii) {
  return JSON.stringify({
    customerId,
    lines,
    email: pii.email,
    cardNumber: pii.cardNumber,
    sessionCookie: pii.sessionCookie,
    jwt: pii.jwt,
  });
}

function jsonPost(path, body, expectedFailure) {
  return http.post(`${BASE_URL}${path}`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { expected_failure: expectedFailure ? 'true' : 'false' },
  });
}

function placeOrder(pii) {
  return jsonPost('/orders', orderBody('C-1234', randomCart(), pii), false);
}

// Reported-outcome business failures (see README.md's three failure kinds): unknown customer,
// payment decline (customer C-BROKE, seeded in InMemoryPaymentService), out-of-stock (a quantity
// above ShopConfig's restocked level — 1,000,000 per SKU, see its RESTOCK_QUANTITY comment — but
// three orders of magnitude below the poison sentinel, so it never collides with it).
const OUT_OF_STOCK_QUANTITY = 2000000;

function businessFailureRequest(pii) {
  const kind = Math.floor(Math.random() * 3);
  if (kind === 0) {
    return jsonPost('/orders', orderBody('C-UNKNOWN', [{ productId: randomProduct(), quantity: 1 }], pii), true);
  }
  if (kind === 1) {
    return jsonPost('/orders', orderBody('C-BROKE', [{ productId: randomProduct(), quantity: 1 }], pii), true);
  }
  return jsonPost(
    '/orders',
    orderBody('C-1234', [{ productId: 'SKU-USB-HUB', quantity: OUT_OF_STOCK_QUANTITY }], pii),
    true
  );
}

export default function () {
  const pii = randomPii();
  const operation = pickOperation(OPERATION_MIX, Math.random() * 100);

  if (operation === 'getCatalog') {
    check(http.get(`${BASE_URL}/catalog`, { tags: { expected_failure: 'false' } }), {
      'catalog 200': (r) => r.status === 200,
    });
  } else if (operation === 'getOrderById') {
    // ORD-00001 is the first order id the run creates (H2 starts fresh each phase) — a 404 before
    // that order exists is an accepted outcome for this smoke-level check, not a threshold breach.
    check(http.get(`${BASE_URL}/orders/ORD-00001`, { tags: { expected_failure: 'false' } }), {
      'order lookup answered': (r) => r.status === 200 || r.status === 404,
    });
  } else if (operation === 'postOrder') {
    check(placeOrder(pii), { 'order placed': (r) => r.status === 201 });
  } else if (operation === 'cancelOrder') {
    check(
      http.post(`${BASE_URL}/orders/ORD-00001/cancel`, null, { tags: { expected_failure: 'false' } }),
      { 'cancel answered': (r) => [200, 404, 409].includes(r.status) }
    );
  } else {
    check(businessFailureRequest(pii), {
      'business failure answered 4xx': (r) => r.status >= 400 && r.status < 500,
    });
  }

  sleep(0.1);
}

export function handleSummary(data) {
  return { [RESULTS_SUMMARY]: JSON.stringify(data, null, 2) };
}
