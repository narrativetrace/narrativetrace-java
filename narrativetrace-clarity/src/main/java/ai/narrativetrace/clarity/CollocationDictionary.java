/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Maps domain nouns to their preferred verb collocations for naming suggestions. */
public final class CollocationDictionary {

  // --- Finance & Banking ---
  private static final Map<String, Set<String>> FINANCE =
      Map.ofEntries(
          Map.entry(
              "account", Set.of("debit", "credit", "balance", "close", "reconcile", "freeze")),
          Map.entry("ledger", Set.of("reconcile", "balance", "post", "close")),
          Map.entry(
              "payment",
              Set.of("authorize", "capture", "disburse", "remit", "settle", "refund", "void")),
          Map.entry("loan", Set.of("originate", "underwrite", "amortize", "service", "default")),
          Map.entry("invoice", Set.of("issue", "settle", "void", "dispute")),
          Map.entry(
              "transaction",
              Set.of("commit", "rollback", "authorize", "settle", "void", "reverse")),
          Map.entry("portfolio", Set.of("rebalance", "diversify", "hedge", "liquidate")),
          Map.entry("bond", Set.of("issue", "mature", "redeem", "yield", "coupon")),
          Map.entry("tax", Set.of("withhold", "file", "remit", "assess", "levy", "exempt")),
          Map.entry("budget", Set.of("allocate", "forecast", "reconcile", "approve")),
          Map.entry("asset", Set.of("value", "revalue", "impair", "liquidate")),
          Map.entry("collateral", Set.of("pledge", "release", "haircut")));

  // --- E-Commerce & Retail ---
  private static final Map<String, Set<String>> ECOMMERCE =
      Map.ofEntries(
          Map.entry("order", Set.of("place", "fulfill", "cancel", "ship", "return", "backorder")),
          Map.entry("cart", Set.of("add", "remove", "empty", "checkout", "abandon")),
          Map.entry("inventory", Set.of("replenish", "reserve", "deplete", "count", "restock")),
          Map.entry("product", Set.of("list", "delist", "discount", "bundle", "feature")),
          Map.entry(
              "subscription",
              Set.of("activate", "cancel", "renew", "pause", "upgrade", "downgrade")),
          Map.entry("coupon", Set.of("apply", "redeem", "expire", "validate")),
          Map.entry("price", Set.of("set", "reprice", "discount", "markdown")),
          Map.entry("return", Set.of("authorize", "receive", "refund", "restock")));

  // --- Healthcare & Medical ---
  private static final Map<String, Set<String>> HEALTHCARE =
      Map.ofEntries(
          Map.entry(
              "patient", Set.of("admit", "discharge", "refer", "triage", "diagnose", "treat")),
          Map.entry(
              "medication",
              Set.of("prescribe", "administer", "dispense", "discontinue", "titrate")),
          Map.entry(
              "appointment", Set.of("schedule", "cancel", "reschedule", "confirm", "checkin")),
          Map.entry("diagnosis", Set.of("confirm", "rule", "differential", "code")),
          Map.entry("record", Set.of("chart", "amend", "seal", "release")),
          Map.entry("vaccine", Set.of("administer", "store", "discard")),
          Map.entry("specimen", Set.of("collect", "label", "process")));

  // --- Hospitality & Travel ---
  private static final Map<String, Set<String>> HOSPITALITY =
      Map.ofEntries(
          Map.entry(
              "reservation", Set.of("book", "confirm", "cancel", "modify", "honor", "overbook")),
          Map.entry("room", Set.of("assign", "vacate", "upgrade", "block", "service")),
          Map.entry("guest", Set.of("checkin", "checkout", "accommodate", "bill", "comp")),
          Map.entry("booking", Set.of("rebook", "confirm", "cancel")),
          Map.entry("seat", Set.of("assign", "upgrade", "downgrade")));

  // --- Telecommunications ---
  private static final Map<String, Set<String>> TELECOM =
      Map.ofEntries(
          Map.entry(
              "call", Set.of("route", "drop", "forward", "transfer", "mute", "hold", "record")),
          Map.entry(
              "signal", Set.of("amplify", "attenuate", "modulate", "demodulate", "broadcast")),
          Map.entry("channel", Set.of("allocate", "multiplex", "tune", "scramble")),
          Map.entry("subscriber", Set.of("provision", "suspend", "activate", "port", "throttle")),
          Map.entry("session", Set.of("originate", "terminate", "handoff")),
          Map.entry("bandwidth", Set.of("allocate", "shape", "throttle")));

  // --- Gaming ---
  private static final Map<String, Set<String>> GAMING =
      Map.ofEntries(
          Map.entry("player", Set.of("spawn", "respawn", "ban", "kick", "matchmake", "rank")),
          Map.entry("item", Set.of("equip", "loot", "craft", "enchant", "disenchant", "trade")),
          Map.entry("character", Set.of("level", "buff", "debuff", "heal", "revive", "nerf")),
          Map.entry("match", Set.of("start", "pause", "forfeit", "abandon", "spectate")),
          Map.entry("queue", Set.of("join", "leave", "matchmake")),
          Map.entry("lobby", Set.of("create", "join", "leave")));

  // --- Logistics & Supply Chain ---
  private static final Map<String, Set<String>> LOGISTICS =
      Map.ofEntries(
          Map.entry(
              "shipment", Set.of("dispatch", "track", "reroute", "deliver", "return", "insure")),
          Map.entry("cargo", Set.of("load", "unload", "stow", "manifest", "inspect", "clear")),
          Map.entry("route", Set.of("plan", "optimize", "divert", "schedule")),
          Map.entry("container", Set.of("load", "seal", "unseal", "transload")),
          Map.entry("dock", Set.of("assign", "slot", "release")));

  // --- Insurance ---
  private static final Map<String, Set<String>> INSURANCE =
      Map.ofEntries(
          Map.entry(
              "policy",
              Set.of("underwrite", "issue", "renew", "cancel", "lapse", "reinstate", "endorse")),
          Map.entry("claim", Set.of("file", "adjust", "settle", "deny", "subrogate", "appeal")),
          Map.entry("premium", Set.of("quote", "calculate", "collect", "waive", "refund")),
          Map.entry("endorsement", Set.of("add", "remove", "amend")),
          Map.entry("deductible", Set.of("apply", "waive")));

  // --- Education ---
  private static final Map<String, Set<String>> EDUCATION =
      Map.ofEntries(
          Map.entry(
              "student", Set.of("enroll", "expel", "graduate", "mentor", "counsel", "assess")),
          Map.entry("course", Set.of("register", "audit", "drop", "complete", "accredit")),
          Map.entry("grade", Set.of("assign", "appeal", "curve", "post", "withhold")),
          Map.entry("attendance", Set.of("record", "audit", "verify")),
          Map.entry("exam", Set.of("schedule", "proctor", "grade")));

  // --- Real Estate & Property ---
  private static final Map<String, Set<String>> REAL_ESTATE =
      Map.ofEntries(
          Map.entry(
              "property", Set.of("list", "appraise", "inspect", "close", "escrow", "foreclose")),
          Map.entry("lease", Set.of("sign", "renew", "terminate", "sublease", "amend")),
          Map.entry("tenant", Set.of("screen", "evict", "accommodate", "bill")),
          Map.entry("title", Set.of("search", "clear", "record")),
          Map.entry("showing", Set.of("schedule", "cancel")));

  // --- HR & Workforce ---
  private static final Map<String, Set<String>> HR =
      Map.ofEntries(
          Map.entry(
              "employee",
              Set.of("hire", "onboard", "promote", "demote", "terminate", "furlough", "transfer")),
          Map.entry("candidate", Set.of("screen", "interview", "recruit", "reject", "shortlist")),
          Map.entry("position", Set.of("post", "fill", "eliminate", "reclassify")),
          Map.entry("headcount", Set.of("plan", "reduce", "increase")),
          Map.entry("compensation", Set.of("benchmark", "adjust", "approve")));

  // --- Security & Authentication ---
  private static final Map<String, Set<String>> SECURITY =
      Map.ofEntries(
          Map.entry(
              "token", Set.of("issue", "revoke", "refresh", "rotate", "invalidate", "blacklist")),
          Map.entry("credential", Set.of("verify", "revoke", "hash", "store", "rotate")),
          Map.entry("session", Set.of("create", "invalidate", "extend", "hijack", "terminate")),
          Map.entry("certificate", Set.of("sign", "revoke", "renew", "chain", "pin")),
          Map.entry("key", Set.of("generate", "rotate", "revoke", "archive")),
          Map.entry("acl", Set.of("enforce", "evaluate", "audit")));

  // --- DevOps & Infrastructure ---
  private static final Map<String, Set<String>> DEVOPS =
      Map.ofEntries(
          Map.entry(
              "instance",
              Set.of("provision", "deploy", "scale", "terminate", "snapshot", "migrate")),
          Map.entry("container", Set.of("build", "deploy", "kill", "restart", "orchestrate")),
          Map.entry("pipeline", Set.of("trigger", "run", "abort", "retry", "promote")),
          Map.entry("cache", Set.of("warm", "invalidate", "evict", "flush", "populate")),
          Map.entry("node", Set.of("cordon", "drain", "uncordon")),
          Map.entry("release", Set.of("promote", "rollback", "rollout")));

  // --- Data & Analytics ---
  private static final Map<String, Set<String>> DATA =
      Map.ofEntries(
          Map.entry("dataset", Set.of("ingest", "cleanse", "partition", "sample", "anonymize")),
          Map.entry("schema", Set.of("migrate", "validate", "version", "evolve", "normalize")),
          Map.entry("index", Set.of("build", "rebuild", "drop", "optimize", "shard")),
          Map.entry("query", Set.of("execute", "optimize", "cache", "paginate", "throttle")),
          Map.entry("feature", Set.of("derive", "normalize", "vectorize")),
          Map.entry("window", Set.of("slide", "aggregate", "rank")));

  // --- Content & Media ---
  private static final Map<String, Set<String>> CONTENT =
      Map.ofEntries(
          Map.entry("article", Set.of("draft", "publish", "archive", "retract", "syndicate")),
          Map.entry("comment", Set.of("post", "moderate", "flag", "delete", "pin")),
          Map.entry("media", Set.of("upload", "transcode", "stream", "caption", "watermark")),
          Map.entry("subtitle", Set.of("generate", "sync", "translate")),
          Map.entry("transcript", Set.of("generate", "edit", "publish")));

  // --- Social & Community ---
  private static final Map<String, Set<String>> SOCIAL =
      Map.ofEntries(
          Map.entry("user", Set.of("follow", "unfollow", "block", "mute", "report", "verify")),
          Map.entry("post", Set.of("publish", "pin", "boost", "archive", "flag")),
          Map.entry("thread", Set.of("start", "lock", "archive")),
          Map.entry("message", Set.of("send", "delete", "unsend")));

  // --- Messaging & Events ---
  private static final Map<String, Set<String>> MESSAGING =
      Map.ofEntries(
          Map.entry(
              "message",
              Set.of("enqueue", "dequeue", "acknowledge", "nack", "retry", "deadletter")),
          Map.entry("event", Set.of("emit", "publish", "replay", "fanout", "route")),
          Map.entry("queue", Set.of("drain", "purge", "park", "resume")));

  // --- IoT & Embedded ---
  private static final Map<String, Set<String>> IOT =
      Map.ofEntries(
          Map.entry(
              "device",
              Set.of("provision", "commission", "decommission", "pair", "unpair", "reboot")),
          Map.entry("sensor", Set.of("calibrate", "sample", "poll", "stream")),
          Map.entry("telemetry", Set.of("capture", "ingest", "aggregate")));

  // --- Legal & Compliance ---
  private static final Map<String, Set<String>> LEGAL =
      Map.ofEntries(
          Map.entry("contract", Set.of("draft", "sign", "amend", "terminate", "enforce", "breach")),
          Map.entry("case", Set.of("file", "adjudicate", "dismiss", "settle", "appeal")),
          Map.entry("verdict", Set.of("deliver", "appeal", "overturn", "uphold")),
          Map.entry("motion", Set.of("file", "argue", "grant", "deny")),
          Map.entry("brief", Set.of("draft", "file", "amend")));

  // --- Manufacturing ---
  private static final Map<String, Set<String>> MANUFACTURING =
      Map.ofEntries(
          Map.entry("batch", Set.of("start", "inspect", "reject", "release", "quarantine")),
          Map.entry("component", Set.of("assemble", "solder", "weld", "test", "certify")),
          Map.entry("line", Set.of("start", "stop", "balance", "retool")),
          Map.entry("workorder", Set.of("create", "schedule", "close")));

  // --- Agriculture & Food ---
  private static final Map<String, Set<String>> AGRICULTURE =
      Map.ofEntries(
          Map.entry("field", Set.of("plant", "irrigate", "fertilize", "harvest")),
          Map.entry("crop", Set.of("sow", "spray", "prune", "harvest")),
          Map.entry("livestock", Set.of("feed", "breed", "vaccinate", "wean")));

  // --- Advertising & Marketing ---
  private static final Map<String, Set<String>> ADVERTISING =
      Map.ofEntries(
          Map.entry("campaign", Set.of("launch", "pause", "optimize", "target", "remarket")),
          Map.entry("audience", Set.of("segment", "target", "exclude", "expand")),
          Map.entry("creative", Set.of("draft", "review", "approve", "rotate")));

  // --- Transportation ---
  private static final Map<String, Set<String>> TRANSPORTATION =
      Map.ofEntries(
          Map.entry("flight", Set.of("schedule", "delay", "depart", "arrive", "reroute")),
          Map.entry("vessel", Set.of("berth", "moor", "unmoor", "dock")),
          Map.entry("vehicle", Set.of("dispatch", "refuel", "reroute", "park")));

  // --- Energy & Utilities ---
  private static final Map<String, Set<String>> ENERGY =
      Map.ofEntries(
          Map.entry("grid", Set.of("balance", "stabilize", "shed", "curtail", "interconnect")),
          Map.entry("meter", Set.of("read", "calibrate", "install", "replace", "tamper")),
          Map.entry("feeder", Set.of("energize", "deenergize", "switch", "island")),
          Map.entry("plant", Set.of("dispatch", "ramp", "derate", "blackstart")));

  // --- Blockchain & Crypto ---
  private static final Map<String, Set<String>> BLOCKCHAIN =
      Map.ofEntries(
          Map.entry("token", Set.of("mint", "burn", "stake", "transfer", "vest", "lock")),
          Map.entry("contract", Set.of("deploy", "verify", "audit", "upgrade", "pause")),
          Map.entry("validator", Set.of("delegate", "redelegate", "slash", "unbond")),
          Map.entry("bridge", Set.of("lock", "mint", "burn", "release")));

  // --- Government & Public Sector ---
  private static final Map<String, Set<String>> PUBLIC_SECTOR =
      Map.ofEntries(
          Map.entry("permit", Set.of("issue", "renew", "revoke", "approve")),
          Map.entry("license", Set.of("issue", "renew", "suspend", "revoke")),
          Map.entry("ordinance", Set.of("draft", "enact", "amend", "repeal")));

  // --- Pharmaceuticals & Biotechnology ---
  private static final Map<String, Set<String>> PHARMA_BIOTECH =
      Map.ofEntries(
          Map.entry("assay", Set.of("run", "validate", "repeat")),
          Map.entry("sample", Set.of("aliquot", "dilute", "incubate", "analyze")),
          Map.entry("compound", Set.of("synthesize", "formulate", "stabilize")));

  // --- Customer Support & CRM ---
  private static final Map<String, Set<String>> SUPPORT_CRM =
      Map.ofEntries(
          Map.entry("ticket", Set.of("open", "triage", "assign", "escalate", "resolve", "close")),
          Map.entry("case", Set.of("categorize", "prioritize", "reopen", "resolve")),
          Map.entry("customer", Set.of("notify", "update", "verify", "retain")));

  // --- Payments & Fintech ---
  private static final Map<String, Set<String>> PAYMENTS_FINTECH =
      Map.ofEntries(
          Map.entry("payout", Set.of("initiate", "disburse", "settle", "reverse")),
          Map.entry("chargeback", Set.of("file", "dispute", "win", "lose")),
          Map.entry("authorization", Set.of("request", "reauthorize", "decline", "approve")));

  // --- Media AdTech ---
  private static final Map<String, Set<String>> MEDIA_ADTECH =
      Map.ofEntries(
          Map.entry("impression", Set.of("serve", "count", "cap", "pace")),
          Map.entry("bid", Set.of("submit", "win", "lose", "optimize")),
          Map.entry("audience", Set.of("segment", "target", "expand", "suppress")));

  // --- Programming & Software Engineering ---
  private static final Map<String, Set<String>> PROGRAMMING =
      Map.ofEntries(
          Map.entry(
              "node",
              Set.of(
                  "create",
                  "build",
                  "render",
                  "visit",
                  "traverse",
                  "remove",
                  "insert",
                  "find",
                  "update",
                  "delete")),
          Map.entry(
              "tree",
              Set.of("build", "render", "traverse", "walk", "flatten", "prune", "create", "parse")),
          Map.entry(
              "list",
              Set.of(
                  "create", "build", "render", "filter", "sort", "append", "remove", "clear",
                  "find")),
          Map.entry(
              "score",
              Set.of("compute", "calculate", "normalize", "compare", "update", "aggregate")),
          Map.entry(
              "value",
              Set.of("get", "set", "render", "format", "parse", "validate", "compute", "convert")),
          Map.entry(
              "text",
              Set.of("render", "format", "parse", "tokenize", "trim", "split", "join", "encode")),
          Map.entry(
              "name",
              Set.of(
                  "parse",
                  "validate",
                  "format",
                  "generate",
                  "resolve",
                  "normalize",
                  "tokenize",
                  "score")),
          Map.entry(
              "error",
              Set.of("handle", "throw", "catch", "log", "report", "wrap", "format", "recover")),
          Map.entry(
              "result",
              Set.of("compute", "build", "format", "render", "aggregate", "merge", "collect")),
          Map.entry(
              "state",
              Set.of(
                  "update", "reset", "restore", "save", "load", "merge", "initialize", "validate")),
          Map.entry(
              "context",
              Set.of("create", "build", "enter", "exit", "restore", "save", "capture", "wrap")),
          Map.entry(
              "source",
              Set.of(
                  "read", "parse", "scan", "analyze", "load", "validate", "compile", "transform")),
          Map.entry(
              "parameter",
              Set.of("validate", "parse", "capture", "render", "extract", "format", "score")),
          Map.entry(
              "issue",
              Set.of("collect", "detect", "report", "create", "resolve", "format", "filter")),
          Map.entry(
              "method",
              Set.of("score", "analyze", "extract", "invoke", "call", "find", "validate")),
          Map.entry(
              "class", Set.of("define", "instantiate", "extend", "load", "inspect", "serialize")),
          Map.entry(
              "object", Set.of("create", "clone", "serialize", "deserialize", "validate", "merge")),
          Map.entry(
              "module", Set.of("load", "initialize", "configure", "wire", "register", "reload")),
          Map.entry("package", Set.of("publish", "install", "resolve", "upgrade", "sign", "scan")),
          Map.entry("field", Set.of("read", "write", "map", "validate", "serialize", "redact")),
          Map.entry("exception", Set.of("throw", "catch", "wrap", "propagate", "log", "map")),
          Map.entry("buffer", Set.of("allocate", "fill", "flush", "drain", "resize", "slice")),
          Map.entry("stream", Set.of("open", "read", "write", "flush", "close", "pipe")),
          Map.entry(
              "payload", Set.of("build", "parse", "validate", "sanitize", "sign", "compress")),
          Map.entry("request", Set.of("build", "send", "retry", "cancel", "validate", "throttle")),
          Map.entry(
              "response", Set.of("return", "serialize", "parse", "cache", "stream", "validate")),
          Map.entry(
              "schema", Set.of("define", "validate", "migrate", "evolve", "generate", "infer")),
          Map.entry("event", Set.of("emit", "publish", "consume", "handle", "replay", "enrich")),
          Map.entry(
              "command", Set.of("dispatch", "execute", "validate", "queue", "retry", "cancel")),
          Map.entry(
              "handler", Set.of("register", "resolve", "invoke", "chain", "decorate", "replace")),
          Map.entry("factory", Set.of("create", "build", "configure", "wire", "cache", "resolve")));

  // --- API & Platform Engineering ---
  private static final Map<String, Set<String>> API_PLATFORM =
      Map.ofEntries(
          Map.entry(
              "endpoint",
              Set.of("expose", "secure", "version", "deprecate", "throttle", "document")),
          Map.entry("webhook", Set.of("register", "deliver", "sign", "verify", "retry", "disable")),
          Map.entry("gateway", Set.of("route", "authorize", "throttle", "cache", "rewrite")),
          Map.entry(
              "tenant",
              Set.of("provision", "isolate", "migrate", "suspend", "activate", "offboard")),
          Map.entry(
              "featureflag",
              Set.of("enable", "disable", "rollout", "target", "evaluate", "retire")),
          Map.entry("job", Set.of("schedule", "enqueue", "run", "retry", "cancel", "monitor")),
          Map.entry(
              "workflow", Set.of("start", "advance", "pause", "resume", "cancel", "complete")),
          Map.entry(
              "rule", Set.of("define", "evaluate", "prioritize", "enforce", "override", "disable")),
          Map.entry(
              "template",
              Set.of("render", "compile", "validate", "version", "override", "publish")),
          Map.entry(
              "artifact", Set.of("build", "publish", "sign", "promote", "download", "verify")));

  // --- Observability & Reliability ---
  private static final Map<String, Set<String>> OBSERVABILITY =
      Map.ofEntries(
          Map.entry("metric", Set.of("record", "aggregate", "export", "tag", "sample", "reset")),
          Map.entry("trace", Set.of("start", "annotate", "propagate", "sample", "flush", "end")),
          Map.entry("span", Set.of("start", "annotate", "tag", "link", "flush", "finish")),
          Map.entry("alert", Set.of("trigger", "silence", "acknowledge", "escalate", "resolve")),
          Map.entry(
              "incident",
              Set.of("declare", "triage", "escalate", "mitigate", "resolve", "postmortem")),
          Map.entry(
              "dashboard", Set.of("build", "publish", "share", "refresh", "drilldown", "archive")),
          Map.entry("log", Set.of("write", "parse", "filter", "ship", "redact", "correlate")),
          Map.entry(
              "checkpoint", Set.of("create", "restore", "persist", "prune", "verify", "rotate")));

  // --- Machine Learning & AI ---
  private static final Map<String, Set<String>> ML_AI =
      Map.ofEntries(
          Map.entry("model", Set.of("train", "validate", "evaluate", "serve", "deploy", "retrain")),
          Map.entry(
              "embedding", Set.of("generate", "index", "normalize", "store", "cache", "search")),
          Map.entry(
              "prompt", Set.of("compose", "template", "ground", "evaluate", "sanitize", "version")),
          Map.entry(
              "classifier",
              Set.of("train", "score", "calibrate", "threshold", "evaluate", "serve")),
          Map.entry(
              "prediction", Set.of("generate", "score", "explain", "cache", "serve", "audit")),
          Map.entry(
              "experiment", Set.of("design", "run", "track", "compare", "promote", "archive")),
          Map.entry("label", Set.of("assign", "review", "correct", "merge", "map", "validate")),
          Map.entry(
              "featurestore",
              Set.of("publish", "materialize", "backfill", "serve", "monitor", "deprecate")));

  // --- Quality, Governance & Compliance Engineering ---
  private static final Map<String, Set<String>> QUALITY_GOVERNANCE =
      Map.ofEntries(
          Map.entry(
              "testcase",
              Set.of("define", "execute", "assert", "parameterize", "isolate", "stabilize")),
          Map.entry("fixture", Set.of("prepare", "seed", "reset", "load", "teardown", "reuse")),
          Map.entry(
              "baseline", Set.of("establish", "compare", "refresh", "approve", "pin", "version")),
          Map.entry(
              "benchmark", Set.of("run", "compare", "profile", "optimize", "track", "report")),
          Map.entry(
              "regression", Set.of("detect", "reproduce", "triage", "fix", "verify", "prevent")),
          Map.entry(
              "coverage", Set.of("measure", "report", "increase", "enforce", "track", "gate")),
          Map.entry(
              "runbook", Set.of("author", "version", "execute", "review", "validate", "retire")),
          Map.entry(
              "playbook", Set.of("draft", "execute", "simulate", "update", "review", "publish")),
          Map.entry("auditlog", Set.of("capture", "append", "seal", "query", "retain", "export")),
          Map.entry(
              "control", Set.of("define", "implement", "test", "enforce", "monitor", "audit")),
          Map.entry(
              "evidence", Set.of("collect", "attach", "review", "retain", "export", "verify")),
          Map.entry(
              "finding", Set.of("record", "triage", "assign", "remediate", "verify", "close")),
          Map.entry(
              "lineage", Set.of("capture", "trace", "visualize", "validate", "repair", "publish")),
          Map.entry(
              "policy", Set.of("evaluate", "enforce", "apply", "override", "simulate", "attest")),
          Map.entry(
              "compliance",
              Set.of("assess", "monitor", "report", "attest", "remediate", "enforce")));

  private static final Map<String, Set<String>> ALL_COLLOCATIONS =
      Stream.of(
              FINANCE,
              ECOMMERCE,
              HEALTHCARE,
              HOSPITALITY,
              TELECOM,
              GAMING,
              LOGISTICS,
              INSURANCE,
              EDUCATION,
              REAL_ESTATE,
              HR,
              SECURITY,
              DEVOPS,
              DATA,
              CONTENT,
              SOCIAL,
              MESSAGING,
              IOT,
              LEGAL,
              MANUFACTURING,
              AGRICULTURE,
              ADVERTISING,
              TRANSPORTATION,
              ENERGY,
              BLOCKCHAIN,
              PUBLIC_SECTOR,
              PHARMA_BIOTECH,
              SUPPORT_CRM,
              PAYMENTS_FINTECH,
              MEDIA_ADTECH,
              PROGRAMMING,
              API_PLATFORM,
              OBSERVABILITY,
              ML_AI,
              QUALITY_GOVERNANCE)
          .flatMap(m -> m.entrySet().stream())
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  e -> new HashSet<>(e.getValue()),
                  (a, b) -> {
                    a.addAll(b);
                    return a;
                  }))
          .entrySet()
          .stream()
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));

  public Set<String> preferredVerbs(String noun) {
    if (noun == null || noun.isBlank()) return Set.of();
    return ALL_COLLOCATIONS.getOrDefault(noun.toLowerCase(Locale.ROOT), Set.of());
  }

  public boolean isPreferred(String verb, String noun) {
    if (verb == null || verb.isBlank()) return false;
    return preferredVerbs(noun).contains(verb.toLowerCase(Locale.ROOT));
  }
}
