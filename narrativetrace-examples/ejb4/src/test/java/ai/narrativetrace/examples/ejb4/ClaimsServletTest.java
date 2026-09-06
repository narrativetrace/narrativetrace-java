/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaimsServletTest {

  private ClaimsServlet servlet;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private StringWriter responseBody;

  @BeforeEach
  void setUp() throws IOException {
    servlet = new ClaimsServlet();
    var policyLookup = new PolicyLookupEJB();
    policyLookup.seedDemoPolicies();
    servlet.claimsProcessor =
        new ClaimsProcessorBean(policyLookup, new FraudChkMgr(), new CoverageCalcEJB());
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    responseBody = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @Test
  void coveredClaimRespondsWithAnApprovalLine() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-1001");
    when(request.getParameter("amountCents")).thenReturn("800000");
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);

    verify(response).setContentType("text/plain");
    assertThat(responseBody.toString()).contains("APPROVED").contains("$7500.00");
  }

  @Test
  void rejectedClaimRespondsWithTheRejectionReason() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-2002");
    when(request.getParameter("amountCents")).thenReturn("800000");
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);

    assertThat(responseBody.toString()).contains("REJECTED").contains("policy lapsed");
  }

  @Test
  void eachRequestGetsItsOwnClaimId() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-1001");
    when(request.getParameter("amountCents")).thenReturn("800000");
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);
    servlet.doGet(request, response);

    assertThat(responseBody.toString()).contains("CLM-1").contains("CLM-2");
  }

  @Test
  void missingDescriptionDefaultsToEmptyInsteadOfFailing() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-1001");
    when(request.getParameter("amountCents")).thenReturn("800000");
    when(request.getParameter("description")).thenReturn(null);

    servlet.doGet(request, response);

    assertThat(responseBody.toString()).contains("APPROVED");
  }

  @Test
  void nonNumericAmountIsABadRequest() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-1001");
    when(request.getParameter("amountCents")).thenReturn("lots");
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), contains("amountCents"));
  }

  @Test
  void missingAmountIsABadRequest() throws IOException {
    when(request.getParameter("policy")).thenReturn("POL-1001");
    when(request.getParameter("amountCents")).thenReturn(null);
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), contains("amountCents"));
  }

  @Test
  void missingPolicyNumberIsABadRequest() throws IOException {
    when(request.getParameter("policy")).thenReturn(null);
    when(request.getParameter("amountCents")).thenReturn("800000");
    when(request.getParameter("description")).thenReturn("kitchen water damage");

    servlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), contains("policyNumber"));
  }
}
