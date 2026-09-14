/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Run with: node --test k6/mix.test.js (see ../README.md "k6/").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { OPERATION_MIX, totalPercent, pickOperation } from './mix.js';

test('operation mix sums to 100 percent', () => {
  assert.equal(totalPercent(OPERATION_MIX), 100);
});

test('pickOperation returns getCatalog for draws at the start of its band', () => {
  assert.equal(pickOperation(OPERATION_MIX, 0), 'getCatalog');
  assert.equal(pickOperation(OPERATION_MIX, 59.99), 'getCatalog');
});

test('pickOperation returns getOrderById for the next band', () => {
  assert.equal(pickOperation(OPERATION_MIX, 60), 'getOrderById');
  assert.equal(pickOperation(OPERATION_MIX, 79.99), 'getOrderById');
});

test('pickOperation returns postOrder, then cancelOrder, then businessFailure in order', () => {
  assert.equal(pickOperation(OPERATION_MIX, 80), 'postOrder');
  assert.equal(pickOperation(OPERATION_MIX, 94.99), 'postOrder');
  assert.equal(pickOperation(OPERATION_MIX, 95), 'cancelOrder');
  assert.equal(pickOperation(OPERATION_MIX, 97.99), 'cancelOrder');
  assert.equal(pickOperation(OPERATION_MIX, 98), 'businessFailure');
  assert.equal(pickOperation(OPERATION_MIX, 99.99), 'businessFailure');
});

test('pickOperation covers every band without a gap across a fine sweep', () => {
  const seen = new Set();
  for (let i = 0; i < 10000; i++) {
    seen.add(pickOperation(OPERATION_MIX, (i / 10000) * 100));
  }
  assert.deepEqual([...seen].sort(), Object.keys(OPERATION_MIX).sort());
});
