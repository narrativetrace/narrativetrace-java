/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Tutorial example: an unmodified EJB 4 (Jakarta EE) WAR traced zero-code by the java agent.
 *
 * <p>This package is an EJB 4 insurance-claims application in deliberate EJB-2.x-era style: a
 * servlet front door, container-managed session beans, and period naming ({@code FraudChkMgr},
 * {@code chkClaim}, {@code CoverageCalcEJB}) of the kind the clarity analyzer exists to call out.
 * It compiles against container-provided APIs only and is packaged as a WAR with an empty {@code
 * WEB-INF/lib} — no NarrativeTrace dependency of any kind, because the whole point is that the
 * agent narrates classes that never heard of NarrativeTrace.
 *
 * <p>Suggested reading order: {@link ai.narrativetrace.examples.ejb4.ClaimsServlet} (the HTTP entry
 * point), then {@link ai.narrativetrace.examples.ejb4.ClaimsProcessorBean} (the orchestration),
 * then the collaborators it calls — {@link ai.narrativetrace.examples.ejb4.PolicyLookupEJB}, {@link
 * ai.narrativetrace.examples.ejb4.FraudChkMgr}, {@link
 * ai.narrativetrace.examples.ejb4.CoverageCalcEJB} — and finally the domain records. The
 * Docker-tagged {@code WildFlyAgentNarrationTest} shows the WAR narrated inside WildFly with only
 * {@code -javaagent} attached; {@code Ejb4NamingClarityTest} turns the deliberate names into the
 * clarity "rename these" report.
 *
 * <p>INTENT: Use this package to demonstrate agent-only tracing inside an app server and the
 * clarity report's value on period naming.
 */
package ai.narrativetrace.examples.ejb4;
