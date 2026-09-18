/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Pure, framework-free load profiles (see ../README.md "k6/") — no k6-specific API here, for the
// same reason as mix.js: scenario.js imports this file inside k6, profiles.test.js inside Node.

const UNIT_SECONDS = { h: 3600, m: 60, s: 1 };

function durationSeconds(duration) {
  if (!/^(\d+[hms])+$/.test(duration)) {
    throw new Error(`Unparseable stage duration '${duration}'`);
  }
  let seconds = 0;
  for (const [, amount, unit] of duration.matchAll(/(\d+)([hms])/g)) {
    seconds += Number(amount) * UNIT_SECONDS[unit];
  }
  return seconds;
}

export function totalSeconds(stages) {
  return stages.reduce((sum, stage) => sum + durationSeconds(stage.duration), 0);
}

const THRESHOLDS = {
  // Deliberate failures (business failures, the poison path) are tagged expected_failure:true
  // at the point each request is fired — this threshold only watches the rest.
  'http_req_failed{expected_failure:false}': ['rate==0'],
};

// Every profile's stage total must equal run-soak.sh's TOTAL_SECONDS for it — the baseline length
// and summarize.py's expected poison count come from the runner, not from these stages
// (profiles.test.js holds the two together).
export const PROFILES = {
  // Smoke profile (P1): 1 min ramp to 20 req/s, 8 min steady, 1 min down.
  smoke: {
    scenarios: {
      smoke: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 50,
        maxVUs: 200,
        stages: [
          { target: 20, duration: '1m' },
          { target: 20, duration: '8m' },
          { target: 0, duration: '1m' },
        ],
      },
    },
    thresholds: THRESHOLDS,
  },

  // One-hour profile — the two-hour shape at half scale, same rates: 5 min ramp, 45 min steady,
  // two 2.5-min 3x spikes (20 -> 60 req/s), 5 min recovery.
  'one-hour': {
    scenarios: {
      oneHour: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 100,
        maxVUs: 500,
        // 5 + 20 + 2.5 + 5 + 15 + 2.5 + 5 + 5 = 60 minutes; steady-state minutes (20+5+15+5) = 45.
        stages: [
          { target: 20, duration: '5m' },
          { target: 20, duration: '20m' },
          { target: 60, duration: '2m30s' },
          { target: 20, duration: '5m' },
          { target: 20, duration: '15m' },
          { target: 60, duration: '2m30s' },
          { target: 20, duration: '5m' },
          { target: 0, duration: '5m' },
        ],
      },
    },
    thresholds: THRESHOLDS,
  },

  // Two-hour profile — exported, not run in P1 (see README.md "k6/"): 10 min ramp, 90 min steady,
  // two 5-min 3x spikes (20 -> 60 req/s), 10 min recovery.
  'two-hour': {
    scenarios: {
      twoHour: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 100,
        maxVUs: 500,
        // 10 + 40 + 5 + 10 + 30 + 5 + 10 + 10 = 120 minutes; steady-state minutes (40+10+30+10) = 90.
        stages: [
          { target: 20, duration: '10m' },
          { target: 20, duration: '40m' },
          { target: 60, duration: '5m' },
          { target: 20, duration: '10m' },
          { target: 20, duration: '30m' },
          { target: 60, duration: '5m' },
          { target: 20, duration: '10m' },
          { target: 0, duration: '10m' },
        ],
      },
    },
    thresholds: THRESHOLDS,
  },
};
