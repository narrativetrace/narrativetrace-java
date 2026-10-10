#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# narrativetrace-verify / happy-path — the loop with no trap in it.
#
# Fixture: existing-service-checkout. The task records LedgerService.recordIssued as soon as the
# invoice is issued; the natural place, right after InvoiceService.issueInvoice, is also the right
# one. The skill still writes the intent first, reads the structural trace, pins the flow behind the
# scripted "yes, pin it" (turn 2) and reports by span id — the same gates as
# verify-unintended-interaction, with recordIssued as the call that must follow issueInvoice.
set -e
python3 "$(dirname "$0")/../../grade_the_verify.py" --kind happy
