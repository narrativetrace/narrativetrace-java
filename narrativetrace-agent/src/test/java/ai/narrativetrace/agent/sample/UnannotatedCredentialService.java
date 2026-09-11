/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

/**
 * Credential-carrying methods with <em>no</em> annotations at all.
 *
 * <p>INTENT: Every other sample in this package marks its sensitive parameters {@code @NotTraced}.
 * That is what let a real defect hide: the suite proved the annotation worked and never asked
 * whether the name deny-list was consulted for a parameter, which it was not. These methods carry
 * nothing but their names, so only the deny-list can save them.
 *
 * <p><b>@llmNote</b> Do not add {@code @NotTraced} to anything here. It would make a failure go
 * away by restoring exactly the blind spot the accompanying test exists to close.
 */
public class UnannotatedCredentialService {

  /** The textbook case the README has always illustrated, unannotated. */
  public String login(String username, String password) {
    return "token-for-" + username;
  }

  /**
   * The shape that actually leaked: a camel-case compound of a deny-listed word.
   *
   * <p>Found 2026-09-10 when an agent built an application against the published artifacts and its
   * payment token was written in cleartext into twelve trace files.
   */
  public String pay(String policyId, long amountCents, String paymentToken) {
    return "TXN-" + policyId + "-" + amountCents;
  }

  /** A method with nothing sensitive at all — redaction must not touch it. */
  public String describe(String orderNumber, int quantity) {
    return orderNumber + "x" + quantity;
  }
}
