#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# See ../shop/entrypoint.sh for the phase-switch and flat-classpath-launch rationale.
set -e

JAVA_OPTS=""
if [ "${TRACING_ENABLED:-true}" = "true" ]; then
  AGENT_JAR=$(ls /agent/*-standalone.jar 2>/dev/null | head -1)
  if [ -z "$AGENT_JAR" ]; then
    echo "TRACING_ENABLED=true but no standalone agent jar found under /agent" >&2
    exit 1
  fi
  PACKAGES="${SOAK_AGENT_PACKAGES:-ai.narrativetrace.soak.notify.domain}"
  JAVA_OPTS="-javaagent:${AGENT_JAR}=packages=${PACKAGES}"
fi

exec java $JAVA_OPTS -cp "/app/extracted/app.jar:/app/extracted/lib/*" \
  ai.narrativetrace.soak.notify.NotifyApplication
