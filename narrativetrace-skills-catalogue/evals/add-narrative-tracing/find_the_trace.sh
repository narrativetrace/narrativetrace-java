#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# The trace guard shared by every program runner (run_the_program.sh, run_the_server.sh): does a
# captured piece of program output carry a rendered trace naming $1? Exit 0 = it does.
#
#   find_the_trace.sh <TracedServiceName>                         — validate the name only (fail fast,
#                                                                    before a runner spends a build)
#   find_the_trace.sh <TracedServiceName> <output-file> [<runner>] — grade the output; <runner> names
#                                                                    the caller in the verdict line
set -e

service="$1"
output="$2"
runner="${3:-run_the_program.sh}"
if [ -z "$service" ]; then
  echo "usage: find_the_trace.sh <TracedServiceName> [<output-file> [<runner>]]" >&2
  exit 1
fi
# The name goes into a grep pattern below, so it has to BE a Java identifier and not a regex: a `.`
# would become a wildcard and match `OrderaService.placeOrder(` for `Order.Service`, which is the
# exact near miss the guard's own boundary check cannot see. InitPromptFixtureShapeTest already
# constrains the name by reading it as an identifier out of the case's grader, so this refuses at the
# point of USE rather than trusting a caller two files away (adversarial pass, milestone 5).
case "$service" in
  *[!A-Za-z0-9_]* | [0-9]*)
    echo "the graded service name must be a Java identifier, not \"$service\" — it goes into a" >&2
    echo "grep pattern, where a regex metacharacter silently widens the match" >&2
    exit 1
    ;;
esac

[ -n "$output" ] || exit 0
if [ ! -f "$output" ]; then
  echo "find_the_trace.sh: no captured output at $output" >&2
  exit 1
fi
unmarked=$(mktemp)
trap 'rm -f "$unmarked"' EXIT

# A rendered trace line is `<Service>.<method>(…)` — from IndentedTextRenderer, MarkdownRenderer or
# StructuralTraceRenderer alike, which is what "whatever renderer produced it" can honestly mean
# here: the sequence-diagram renderers (`OrderService->>OrderService: placeOrder(…)`) and
# ProseRenderer ("The order service place order for …") carry no service-qualified call token at
# all, and a guard loose enough to accept those would accept any line merely naming the service.
#
# Markdown wraps the call in emphasis and its values in code spans — `- **OrderService.placeOrder**(
# customerId: `"C-1234"`, …)` — so the markup sits between the method name and its paren and the
# match below fails on a perfectly correct trace (round 4, 2026-09-25). Strip the markup first.
# Only `*` and a backtick: neither can occur inside a Java identifier, so removing them can only
# rejoin fragments the guard already treated as separated. `_` is deliberately NOT stripped even
# though Markdown can emphasise with it — `_` IS an identifier character, so stripping it would let
# `Order_Service.placeOrder(` pass for `OrderService`, and no renderer here emits `_` emphasis.
# Both halves of the guard therefore still hold exactly as rehearsed when it was added: the leading
# `(^|[^A-Za-z0-9_])` rejects `MyOrderService.placeOrder(`, the literal name rejects
# `IOrderService.placeOrder(`. The failure message prints the ORIGINAL output, not the stripped one.
#
# TWO accepted shapes, because the library has two sanctioned output formats and the prompt names
# neither: "run the program, paste the trace". A trial on 2026-09-25 printed a perfectly good trace
# through the canonical JSON exporter — every call, the redacted parameter marked `"redacted": true`
# — and this guard rejected it, which measured the grader rather than the agent.
#   text/markdown: a service-qualified call token, `<Service>.<method>(`
#   canonical JSON: the service as a WHOLE quoted string under "className", beside a "methodName"
# The JSON form's near-miss protection is stronger than the text form's, not weaker: an exact quoted
# string cannot match `IOrderService` or `MyOrderService` at all. Requiring "methodName" beside it is
# what keeps the shape a TRACE rather than any JSON that happens to name a class.
LC_ALL=C tr -d '*`' <"$output" >"$unmarked"
if LC_ALL=C grep -Eq "(^|[^A-Za-z0-9_])${service}\.[A-Za-z_][A-Za-z0-9_]*\(" "$unmarked"; then
  echo "$runner: the program ran and printed a rendered trace naming $service"
elif LC_ALL=C grep -Eq "\"className\"[[:space:]]*:[[:space:]]*\"${service}\"" "$unmarked" &&
  LC_ALL=C grep -q '"methodName"' "$unmarked"; then
  echo "$runner: the program ran and printed a canonical-JSON trace naming $service"
else
  echo "expected the program's own output to carry a rendered trace naming $service — either a" >&2
  echo "$service.<method>( call token or a canonical-JSON event naming it in \"className\"" >&2
  echo "--- what it printed instead ---" >&2
  cat "$output" >&2
  exit 1
fi
