#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# narrativetrace-verify / verify-unintended-interaction (Phase 7, D6) — the owner's demonstration.
#
# Fixture: existing-service-checkout. Its documented extension point for side effects,
# CheckoutListener.onCheckout, says it runs "once a checkout's payment has gone through" — but it
# runs after PaymentGateway.authorize and BEFORE the settlement that calls PaymentGateway.confirm.
# The task asks for a receipt "once their payment is confirmed"; the natural first solution — a
# listener — passes every test while the structural trace shows NotificationService.send before
# PaymentGateway.confirm.
# Turn 2 is the scripted "yes, pin it".
#
# PASS (every line of grade_the_verify.py --kind interaction): the intent is in the transcript before
# the first traced run and before any .nt is read; a structural trace reached the agent before its
# report, and no rendered narrative before it; nothing was promoted before the yes, and the turn
# before the yes ended on the question; whether every call line of what was promoted was in a reply
# before the yes is REPORTED, not gated, as the feedback grader reports "the whole draft was shown"; the suite passes and, in the grader's own final run, the
# receipt follows the confirmation; a .approved.nt pins that fixed flow and no .received.nt is left;
# the final report cites a span id that is in the pinned baseline.
# Near misses it fails, each for its own reason: done on green tests; read the trace but did not
# act; values first; never pinned; pinned before the yes; a report without an id; intent after run.
set -e
python3 "$(dirname "$0")/../../grade_the_verify.py" --kind interaction
