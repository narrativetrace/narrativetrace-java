/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dictionary of recognized class name suffixes (Service, Repository, Controller, etc.). */
public final class RoleSuffixDictionary {

  public enum Category {
    DESIGN_PATTERN,
    FUNCTIONAL,
    GENERIC,
    UNKNOWN
  }

  public record Result(Category category, double score) {}

  private static final Set<String> DESIGN_PATTERN_SUFFIXES =
      Set.of(
          "service",
          "repository",
          "controller",
          "factory",
          "builder",
          "strategy",
          "observer",
          "adapter",
          "decorator",
          "visitor",
          "command",
          "facade",
          "proxy",
          "gateway",
          "client");

  private static final Set<String> FUNCTIONAL_SUFFIXES =
      Set.of(
          "validator",
          "converter",
          "mapper",
          "resolver",
          "provider",
          "listener",
          "interceptor",
          "filter",
          "scheduler",
          "dispatcher",
          "router",
          "registry",
          "store",
          "cache",
          "engine",
          "renderer",
          "executor",
          "analyzer",
          "scanner",
          "parser",
          "formatter",
          "encoder",
          "decoder",
          "serializer",
          "loader",
          "exporter",
          "importer",
          "inspector",
          "monitor",
          "notifier",
          "publisher",
          "consumer",
          "producer",
          "transformer",
          "translator",
          "generator",
          "authenticator",
          "authorizer",
          "coordinator",
          "aggregator",
          "orchestrator",
          "middleware",
          "evaluator",
          "ranker",
          "reranker",
          "normalizer",
          "enricher",
          "redactor",
          "guard",
          "policy");

  private static final Set<String> GENERIC_SUFFIXES =
      Set.of(
          "manager",
          "handler",
          "processor",
          "helper",
          "utility",
          "utils",
          "util",
          "common",
          "base",
          "default",
          "data",
          "info",
          "impl");

  private static final Map<String, List<String>> EXPECTED_VERBS =
      Map.ofEntries(
          Map.entry("repository", List.of("find", "save", "delete", "exists", "count", "get")),
          Map.entry("factory", List.of("create", "build", "make", "of", "from", "new")),
          Map.entry(
              "builder", List.of("create", "build", "make", "of", "from", "with", "set", "add")),
          Map.entry("validator", List.of("validate", "check", "verify", "ensure", "is")),
          Map.entry("converter", List.of("convert", "transform", "map", "to", "from")),
          Map.entry("mapper", List.of("map", "convert", "transform", "to", "from")),
          Map.entry("parser", List.of("parse", "read", "extract", "tokenize")),
          Map.entry("formatter", List.of("format", "render", "display", "print")),
          Map.entry("renderer", List.of("render", "display", "format", "draw")),
          Map.entry("scheduler", List.of("schedule", "cancel", "reschedule", "delay")),
          Map.entry("dispatcher", List.of("dispatch", "send", "route", "forward")),
          Map.entry("filter", List.of("filter", "accept", "reject", "matches", "test")),
          Map.entry("listener", List.of("on", "handle", "receive", "process")),
          Map.entry(
              "orchestrator", List.of("start", "advance", "pause", "resume", "cancel", "complete")),
          Map.entry("middleware", List.of("intercept", "wrap", "authorize", "validate", "forward")),
          Map.entry("evaluator", List.of("evaluate", "score", "compare", "rank")),
          Map.entry("ranker", List.of("rank", "score", "sort", "rerank")),
          Map.entry("reranker", List.of("rerank", "score", "sort", "rank")),
          Map.entry("normalizer", List.of("normalize", "standardize", "cleanse", "transform")),
          Map.entry("enricher", List.of("enrich", "augment", "hydrate", "join")),
          Map.entry("redactor", List.of("redact", "sanitize", "mask", "scrub")),
          Map.entry("guard", List.of("validate", "enforce", "block", "allow")),
          Map.entry("policy", List.of("evaluate", "enforce", "apply", "override")),
          Map.entry(
              "controller",
              List.of("create", "read", "update", "delete", "list", "get", "find", "save")),
          Map.entry("service", List.of()),
          Map.entry("gateway", List.of("send", "receive", "connect", "disconnect", "forward")),
          Map.entry("cache", List.of("get", "put", "evict", "invalidate", "contains", "clear")),
          Map.entry("store", List.of("get", "put", "save", "delete", "find", "contains", "clear")),
          Map.entry(
              "registry", List.of("register", "unregister", "lookup", "find", "get", "contains")));

  public Result classify(String suffix) {
    var lower = suffix.toLowerCase();

    if (DESIGN_PATTERN_SUFFIXES.contains(lower)) {
      return new Result(Category.DESIGN_PATTERN, 1.0);
    }
    if (FUNCTIONAL_SUFFIXES.contains(lower)) {
      return new Result(Category.FUNCTIONAL, 1.0);
    }
    if (GENERIC_SUFFIXES.contains(lower)) {
      return new Result(Category.GENERIC, 0.3);
    }
    return new Result(Category.UNKNOWN, 0.6);
  }

  public List<String> expectedVerbs(String suffix) {
    return EXPECTED_VERBS.getOrDefault(suffix.toLowerCase(), List.of());
  }
}
