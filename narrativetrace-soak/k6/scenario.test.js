/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Run with: node --test k6/scenario.test.js (see ../README.md "k6/").
//
// D7 (../../reports/soak/2026-09-16-one-hour.md): the run's only unexpected-failure threshold is
// `http_req_failed{expected_failure:false}` (profiles.js) — that only covers the requests scenario.js
// actually tags. scenario.js imports k6 APIs (k6/http, k6, k6/data) that do not run under plain
// Node — like mix.js/profiles.js, it can't be imported directly here — so this test parses its
// source text instead: every site that issues an HTTP request must supply an `expected_failure`
// tag in the SAME call (balanced parens from `http.get(`/`http.post(` to the matching `)`), or the
// threshold silently stops covering it (a 5xx storm on an untagged endpoint would fail checks,
// which have no threshold, and pass the run).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PROFILES } from './profiles.js';

const SCENARIO_SRC = readFileSync(new URL('./scenario.js', import.meta.url), 'utf8');

// Finds every `http.get(`/`http.post(` call site in the source and returns, for each, the call's
// own argument text (balanced parens starting right after the opening one) — so a `tags:` object
// nested inside that same call is captured, and one belonging to an unrelated later call is not.
function requestCallSites(src) {
  const sites = [];
  const callStart = /http\.(get|post)\(/g;
  let match;
  while ((match = callStart.exec(src)) !== null) {
    const argsStart = match.index + match[0].length;
    let depth = 1;
    let i = argsStart;
    while (i < src.length && depth > 0) {
      if (src[i] === '(') depth++;
      else if (src[i] === ')') depth--;
      i++;
    }
    sites.push({ method: match[1], args: src.slice(argsStart, i - 1) });
  }
  return sites;
}

test('every k6 request site tags expected_failure, so http_req_failed{expected_failure:false} covers all requests', () => {
  const sites = requestCallSites(SCENARIO_SRC);

  // Sanity on the parser itself: scenario.js is known to issue at least these request kinds
  // (GET /catalog, GET /orders/{id}, POST /orders via jsonPost, POST /orders/{id}/cancel) — if
  // this drops to zero the regex/paren-matcher broke, not the production code.
  assert.ok(sites.length >= 4, `expected to find at least 4 http.get/http.post call sites, found ${sites.length}`);

  const untagged = sites.filter((site) => !/tags\s*:\s*\{[^}]*expected_failure/.test(site.args));
  assert.deepEqual(
    untagged,
    [],
    `every http.get/http.post call site must carry a tags:{expected_failure:...} option, ` +
      `untagged sites: ${JSON.stringify(untagged)}`
  );

  // The threshold that is supposed to be watching every one of those tagged requests.
  const thresholdKeys = Object.keys(PROFILES.smoke.thresholds);
  assert.ok(
    thresholdKeys.some((key) => key.includes('expected_failure')),
    `expected a threshold referencing the expected_failure tag, got: ${JSON.stringify(thresholdKeys)}`
  );
});
