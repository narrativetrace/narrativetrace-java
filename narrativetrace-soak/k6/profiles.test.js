/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Run with: node --test k6/profiles.test.js (see ../README.md "k6/").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PROFILES, totalSeconds } from './profiles.js';

// run-soak.sh's `<profile>) TOTAL_SECONDS=<n> ;;` case arms — the phase lengths and the poison
// count summarize.py expects are derived from these, not from the k6 stages themselves.
function runnerTotalSeconds() {
  const runner = readFileSync(new URL('../run-soak.sh', import.meta.url), 'utf8');
  const arms = runner.matchAll(/^\s*([\w-]+)\) TOTAL_SECONDS=(\d+) ;;$/gm);
  return Object.fromEntries([...arms].map(([, profile, seconds]) => [profile, Number(seconds)]));
}

test('every k6 profile lasts exactly as long as run-soak.sh budgets for it, and no profile is missing', () => {
  const k6Totals = Object.fromEntries(
    Object.entries(PROFILES).map(([name, options]) => [
      name,
      totalSeconds(Object.values(options.scenarios)[0].stages),
    ])
  );
  assert.deepEqual(k6Totals, runnerTotalSeconds());
});

test('totalSeconds sums minute, second and mixed stage durations', () => {
  assert.equal(totalSeconds([{ duration: '1m' }, { duration: '45s' }, { duration: '2m30s' }]), 255);
});

test('totalSeconds rejects a duration that is not wholly h/m/s components', () => {
  for (const malformed of ['10', '5x', '', '1m 30s', 'm', '1.5m']) {
    assert.throws(() => totalSeconds([{ duration: malformed }]), {
      name: 'Error',
      message: `Unparseable stage duration '${malformed}'`,
    });
  }
});
