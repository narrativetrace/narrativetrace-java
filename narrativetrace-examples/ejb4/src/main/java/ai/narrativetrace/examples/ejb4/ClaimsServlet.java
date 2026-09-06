/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import jakarta.ejb.EJB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP entry point of the legacy demo: {@code GET /claims?policy=POL-1001&amountCents=800000
 * &description=...} files a claim and answers with the decision as plain text. The java agent
 * narrates the servlet → EJB-proxy → bean → service chain without this WAR referencing
 * NarrativeTrace anywhere.
 */
@WebServlet(urlPatterns = "/claims")
public class ClaimsServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @EJB transient ClaimsProcessorBean claimsProcessor;

  private final AtomicLong claimSequence = new AtomicLong();

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      var claim = claimFromRequest(request);
      var decision = claimsProcessor.processClaim(claim);
      response.setContentType("text/plain");
      response.getWriter().println(decisionLine(claim, decision));
    } catch (IllegalArgumentException invalidRequest) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidRequest.getMessage());
    }
  }

  private Claim claimFromRequest(HttpServletRequest request) {
    var policyNumber = Objects.requireNonNullElse(request.getParameter("policy"), "");
    var description = Objects.requireNonNullElse(request.getParameter("description"), "");
    var claimId = "CLM-" + claimSequence.incrementAndGet();
    return new Claim(claimId, policyNumber, amountCentsFrom(request), description);
  }

  private long amountCentsFrom(HttpServletRequest request) {
    var amount = request.getParameter("amountCents");
    try {
      return Long.parseLong(Objects.requireNonNullElse(amount, ""));
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException("amountCents must be a whole number of cents", notANumber);
    }
  }

  private static String decisionLine(Claim claim, ClaimDecision decision) {
    if (decision.status() == ClaimDecision.Status.APPROVED) {
      return "claim %s on %s: APPROVED, payout $%d.%02d"
          .formatted(
              claim.claimId(),
              claim.policyNumber(),
              decision.payoutCents() / 100,
              decision.payoutCents() % 100);
    }
    return "claim %s on %s: REJECTED (%s)"
        .formatted(claim.claimId(), claim.policyNumber(), decision.reason());
  }
}
