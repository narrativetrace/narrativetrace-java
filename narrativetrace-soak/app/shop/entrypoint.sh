#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Phase switch between the traced and baseline configurations run-soak.sh compares (see
# ../../README.md "phase 1 baseline" / "phase 2 traced"). TRACING_ENABLED=false runs with no
# -javaagent at all — the truest zero-overhead baseline, not narrativetrace.level=OFF (which still
# pays the bytecode-weaving/dispatch-check cost of an attached-but-quiet agent).
#
# Launched via -cp against the exploded jar (see Dockerfile), not `java -jar`: Boot's own fat-jar
# launcher runs application code under a second classloader (LaunchedURLClassLoader) layered over
# the system classloader the -javaagent attaches to. With that split, the agent's bundled
# slf4j-api (found first via parent-first delegation) can never see logback-classic bundled inside
# BOOT-INF/lib, and Spring Boot's logging bootstrap fails with "LoggerFactory is not a Logback
# LoggerContext". A single flat classpath keeps everything — agent classes, slf4j-api,
# logback-classic, the application's own classes — on the one classloader.
set -e

JAVA_OPTS=""
if [ "${TRACING_ENABLED:-true}" = "true" ]; then
  AGENT_JAR=$(ls /agent/*-standalone.jar 2>/dev/null | head -1)
  if [ -z "$AGENT_JAR" ]; then
    echo "TRACING_ENABLED=true but no standalone agent jar found under /agent" >&2
    exit 1
  fi
  PACKAGES="${SOAK_AGENT_PACKAGES:-ai.narrativetrace.examples.ecommerce;ai.narrativetrace.soak.shop.domain}"
  JAVA_OPTS="-javaagent:${AGENT_JAR}=packages=${PACKAGES}"
fi

exec java $JAVA_OPTS -cp "/app/extracted/app.jar:/app/extracted/lib/*" \
  ai.narrativetrace.soak.shop.ShopApplication
