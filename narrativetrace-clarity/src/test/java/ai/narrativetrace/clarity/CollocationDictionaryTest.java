/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollocationDictionaryTest {

  private final CollocationDictionary dictionary = new CollocationDictionary();

  @Test
  void returnsPreferredVerbsForFinanceNoun() {
    var verbs = dictionary.preferredVerbs("ledger");
    assertThat(verbs).containsExactlyInAnyOrder("reconcile", "balance", "post", "close");
  }

  @Test
  void returnsEmptySetForUnknownNoun() {
    assertThat(dictionary.preferredVerbs("quuxbaz")).isEmpty();
  }

  @Test
  void isPreferredReturnsTrueForKnownCollocation() {
    assertThat(dictionary.isPreferred("reconcile", "ledger")).isTrue();
  }

  @Test
  void isPreferredReturnsFalseForNonPreferredVerb() {
    assertThat(dictionary.isPreferred("check", "ledger")).isFalse();
  }

  @Test
  void isCaseInsensitive() {
    assertThat(dictionary.preferredVerbs("Ledger"))
        .containsExactlyInAnyOrder("reconcile", "balance", "post", "close");
    assertThat(dictionary.isPreferred("Reconcile", "LEDGER")).isTrue();
  }

  @Test
  void financeAccountHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("account");
    assertThat(verbs)
        .containsExactlyInAnyOrder("debit", "credit", "balance", "close", "reconcile", "freeze");
  }

  @Test
  void financePaymentHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("payment");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "authorize", "capture", "disburse", "remit", "settle", "refund", "void");
  }

  @Test
  void financeLoanHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("loan");
    assertThat(verbs)
        .containsExactlyInAnyOrder("originate", "underwrite", "amortize", "service", "default");
  }

  @Test
  void ecommerceOrderHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("order");
    assertThat(verbs)
        .containsExactlyInAnyOrder("place", "fulfill", "cancel", "ship", "return", "backorder");
  }

  @Test
  void ecommerceCartHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("cart");
    assertThat(verbs).containsExactlyInAnyOrder("add", "remove", "empty", "checkout", "abandon");
  }

  @Test
  void ecommerceInventoryHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("inventory");
    assertThat(verbs)
        .containsExactlyInAnyOrder("replenish", "reserve", "deplete", "count", "restock");
  }

  @Test
  void healthcarePatientHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("patient");
    assertThat(verbs)
        .containsExactlyInAnyOrder("admit", "discharge", "refer", "triage", "diagnose", "treat");
  }

  @Test
  void healthcareMedicationHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("medication");
    assertThat(verbs)
        .containsExactlyInAnyOrder("prescribe", "administer", "dispense", "discontinue", "titrate");
  }

  @Test
  void hospitalityReservationHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("reservation");
    assertThat(verbs)
        .containsExactlyInAnyOrder("book", "confirm", "cancel", "modify", "honor", "overbook");
  }

  @Test
  void telecomCallHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("call");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "route", "drop", "forward", "transfer", "mute", "hold", "record");
  }

  @Test
  void gamingPlayerHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("player");
    assertThat(verbs)
        .containsExactlyInAnyOrder("spawn", "respawn", "ban", "kick", "matchmake", "rank");
  }

  @Test
  void logisticsShipmentHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("shipment");
    assertThat(verbs)
        .containsExactlyInAnyOrder("dispatch", "track", "reroute", "deliver", "return", "insure");
  }

  @Test
  void insurancePolicyHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("policy");
    assertThat(verbs)
        .contains("underwrite", "issue", "renew", "cancel", "lapse", "reinstate", "endorse");
  }

  @Test
  void educationStudentHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("student");
    assertThat(verbs)
        .containsExactlyInAnyOrder("enroll", "expel", "graduate", "mentor", "counsel", "assess");
  }

  @Test
  void realEstatePropertyHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("property");
    assertThat(verbs)
        .containsExactlyInAnyOrder("list", "appraise", "inspect", "close", "escrow", "foreclose");
  }

  @Test
  void hrEmployeeHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("employee");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "hire", "onboard", "promote", "demote", "terminate", "furlough", "transfer");
  }

  @Test
  void securityAndBlockchainTokenVerbsAreMerged() {
    var verbs = dictionary.preferredVerbs("token");
    // Security: issue, revoke, refresh, rotate, invalidate, blacklist
    // Blockchain: mint, burn, stake, transfer, vest, lock
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "issue",
            "revoke",
            "refresh",
            "rotate",
            "invalidate",
            "blacklist",
            "mint",
            "burn",
            "stake",
            "transfer",
            "vest",
            "lock");
  }

  @Test
  void devopsInstanceHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("instance");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "provision", "deploy", "scale", "terminate", "snapshot", "migrate");
  }

  @Test
  void dataDatasetHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("dataset");
    assertThat(verbs)
        .containsExactlyInAnyOrder("ingest", "cleanse", "partition", "sample", "anonymize");
  }

  @Test
  void contentArticleHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("article");
    assertThat(verbs)
        .containsExactlyInAnyOrder("draft", "publish", "archive", "retract", "syndicate");
  }

  @Test
  void socialUserHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("user");
    assertThat(verbs)
        .containsExactlyInAnyOrder("follow", "unfollow", "block", "mute", "report", "verify");
  }

  @Test
  void legalAndBlockchainContractVerbsAreMerged() {
    var verbs = dictionary.preferredVerbs("contract");
    // Legal: draft, sign, amend, terminate, enforce, breach
    // Blockchain: deploy, verify, audit, upgrade, pause
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "draft",
            "sign",
            "amend",
            "terminate",
            "enforce",
            "breach",
            "deploy",
            "verify",
            "audit",
            "upgrade",
            "pause");
  }

  @Test
  void manufacturingBatchHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("batch");
    assertThat(verbs)
        .containsExactlyInAnyOrder("start", "inspect", "reject", "release", "quarantine");
  }

  @Test
  void energyGridHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("grid");
    assertThat(verbs)
        .containsExactlyInAnyOrder("balance", "stabilize", "shed", "curtail", "interconnect");
  }

  // --- Programming & Software Engineering ---

  @Test
  void programmingNodeHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("node");
    // DevOps: cordon, drain, uncordon — merged with programming verbs
    assertThat(verbs)
        .contains(
            "create",
            "build",
            "render",
            "visit",
            "traverse",
            "remove",
            "insert",
            "find",
            "update",
            "delete",
            "cordon",
            "drain",
            "uncordon");
  }

  @Test
  void programmingTreeHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("tree");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "build", "render", "traverse", "walk", "flatten", "prune", "create", "parse");
  }

  @Test
  void programmingScoreHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("score");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "compute", "calculate", "normalize", "compare", "update", "aggregate");
  }

  @Test
  void programmingValueHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("value");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "get", "set", "render", "format", "parse", "validate", "compute", "convert");
  }

  @Test
  void programmingTextHasExpectedVerbs() {
    var verbs = dictionary.preferredVerbs("text");
    assertThat(verbs)
        .containsExactlyInAnyOrder(
            "render", "format", "parse", "tokenize", "trim", "split", "join", "encode");
  }

  @Test
  void programmingErrorAndResultHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("error"))
        .containsExactlyInAnyOrder(
            "handle", "throw", "catch", "log", "report", "wrap", "format", "recover");
    assertThat(dictionary.preferredVerbs("result"))
        .containsExactlyInAnyOrder(
            "compute", "build", "format", "render", "aggregate", "merge", "collect");
  }

  @Test
  void programmingStateAndContextHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("state"))
        .containsExactlyInAnyOrder(
            "update", "reset", "restore", "save", "load", "merge", "initialize", "validate");
    assertThat(dictionary.preferredVerbs("context"))
        .containsExactlyInAnyOrder(
            "create", "build", "enter", "exit", "restore", "save", "capture", "wrap");
  }

  @Test
  void programmingSourceAndMethodHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("source"))
        .containsExactlyInAnyOrder(
            "read", "parse", "scan", "analyze", "load", "validate", "compile", "transform");
    assertThat(dictionary.preferredVerbs("method"))
        .containsExactlyInAnyOrder(
            "score", "analyze", "extract", "invoke", "call", "find", "validate");
  }

  @Test
  void programmingListNameParameterIssueHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("list"))
        .containsExactlyInAnyOrder(
            "create", "build", "render", "filter", "sort", "append", "remove", "clear", "find");
    assertThat(dictionary.preferredVerbs("name"))
        .containsExactlyInAnyOrder(
            "parse", "validate", "format", "generate", "resolve", "normalize", "tokenize", "score");
    assertThat(dictionary.preferredVerbs("parameter"))
        .containsExactlyInAnyOrder(
            "validate", "parse", "capture", "render", "extract", "format", "score");
    assertThat(dictionary.preferredVerbs("issue"))
        .containsExactlyInAnyOrder(
            "collect", "detect", "report", "create", "resolve", "format", "filter");
  }

  @Test
  void programmingClassAndObjectHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("class"))
        .containsExactlyInAnyOrder(
            "define", "instantiate", "extend", "load", "inspect", "serialize");
    assertThat(dictionary.preferredVerbs("object"))
        .containsExactlyInAnyOrder(
            "create", "clone", "serialize", "deserialize", "validate", "merge");
  }

  @Test
  void apiPlatformEndpointAndWebhookHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("endpoint"))
        .containsExactlyInAnyOrder(
            "expose", "secure", "version", "deprecate", "throttle", "document");
    assertThat(dictionary.preferredVerbs("webhook"))
        .containsExactlyInAnyOrder("register", "deliver", "sign", "verify", "retry", "disable");
  }

  @Test
  void observabilityMetricTraceAndSpanHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("metric"))
        .containsExactlyInAnyOrder("record", "aggregate", "export", "tag", "sample", "reset");
    assertThat(dictionary.preferredVerbs("trace"))
        .containsExactlyInAnyOrder("start", "annotate", "propagate", "sample", "flush", "end");
    assertThat(dictionary.preferredVerbs("span"))
        .containsExactlyInAnyOrder("start", "annotate", "tag", "link", "flush", "finish");
  }

  @Test
  void mlModelEmbeddingAndPromptHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("model"))
        .containsExactlyInAnyOrder("train", "validate", "evaluate", "serve", "deploy", "retrain");
    assertThat(dictionary.preferredVerbs("embedding"))
        .containsExactlyInAnyOrder("generate", "index", "normalize", "store", "cache", "search");
    assertThat(dictionary.preferredVerbs("prompt"))
        .containsExactlyInAnyOrder(
            "compose", "template", "ground", "evaluate", "sanitize", "version");
  }

  @Test
  void eventCollocationsMergeAcrossDomains() {
    var verbs = dictionary.preferredVerbs("event");
    // Messaging: emit, publish, replay, fanout, route
    // Programming: emit, publish, consume, handle, replay, enrich
    assertThat(verbs)
        .contains("emit", "publish", "replay", "fanout", "route", "consume", "handle", "enrich");
  }

  @Test
  void qualityGovernanceNounsHaveExpectedVerbs() {
    assertThat(dictionary.preferredVerbs("testcase"))
        .containsExactlyInAnyOrder(
            "define", "execute", "assert", "parameterize", "isolate", "stabilize");
    assertThat(dictionary.preferredVerbs("fixture"))
        .containsExactlyInAnyOrder("prepare", "seed", "reset", "load", "teardown", "reuse");
    assertThat(dictionary.preferredVerbs("coverage"))
        .containsExactlyInAnyOrder("measure", "report", "increase", "enforce", "track", "gate");
    assertThat(dictionary.preferredVerbs("auditlog"))
        .containsExactlyInAnyOrder("capture", "append", "seal", "query", "retain", "export");
  }

  @Test
  void policyCollocationsMergeAcrossDomains() {
    var verbs = dictionary.preferredVerbs("policy");
    // Insurance: underwrite, issue, renew, cancel, lapse, reinstate, endorse
    // Quality/Governance: evaluate, enforce, apply, override, simulate, attest
    assertThat(verbs)
        .contains(
            "underwrite",
            "issue",
            "renew",
            "cancel",
            "lapse",
            "reinstate",
            "endorse",
            "evaluate",
            "enforce",
            "apply",
            "override",
            "simulate",
            "attest");
  }

  @Test
  void preferredVerbsReturnsEmptyForNull() {
    assertThat(dictionary.preferredVerbs(null)).isEmpty();
  }

  @Test
  void preferredVerbsReturnsEmptyForBlank() {
    assertThat(dictionary.preferredVerbs("  ")).isEmpty();
  }

  @Test
  void isPreferredReturnsFalseForNullVerb() {
    assertThat(dictionary.isPreferred(null, "ledger")).isFalse();
  }

  @Test
  void isPreferredReturnsFalseForBlankVerb() {
    assertThat(dictionary.isPreferred("  ", "ledger")).isFalse();
  }
}
