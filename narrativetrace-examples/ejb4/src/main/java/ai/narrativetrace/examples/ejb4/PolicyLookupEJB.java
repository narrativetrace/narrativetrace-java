/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory policy registry standing in for the policy master database. The "EJB" suffix is
 * deliberate EJB-era naming — this example's clarity report later flags it as a rename candidate.
 * Deployed as a startup singleton so the demo policies are on file before the first request.
 */
@Singleton
@Startup
public class PolicyLookupEJB {

  private final Map<String, Policy> policiesByNumber = new HashMap<>();

  /** Seeds the policies the container demo works against. */
  @PostConstruct
  public void seedDemoPolicies() {
    register(new Policy("POL-1001", "Ana Suarez", 100_000_00L, 500_00L, true));
    register(new Policy("POL-2002", "Marko Ilic", 50_000_00L, 250_00L, false));
    register(new Policy("POL-3003", "Wei Chen", 900_000_00L, 1_000_00L, true));
  }

  /** Puts {@code policy} on file; re-registering a policy number replaces the earlier version. */
  public void register(Policy policy) {
    if (policy == null) {
      throw new IllegalArgumentException("policy must not be null");
    }
    policiesByNumber.put(policy.policyNumber(), policy);
  }

  /** Returns the policy on file for {@code policyNumber}, or empty when none exists. */
  public Optional<Policy> findPolicyByNumber(String policyNumber) {
    return Optional.ofNullable(policiesByNumber.get(policyNumber));
  }
}
