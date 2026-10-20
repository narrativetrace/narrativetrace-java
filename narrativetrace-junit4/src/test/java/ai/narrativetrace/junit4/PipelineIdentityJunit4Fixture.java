/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import java.util.ArrayList;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Records which context instance each of its test methods was handed, for {@link
 * PipelineOwnershipFootprintTest} to read back after the nested run completes. Public, as every
 * JUnit 4 test class and {@code @Test} method must be.
 */
public class PipelineIdentityJunit4Fixture {

  static final int TEST_COUNT = 5;
  static final List<Integer> contextIdentities = new ArrayList<>();

  @ClassRule public static final NarrativeTraceClassRule CLASS_RULE = new NarrativeTraceClassRule();

  @Rule public final NarrativeTraceRule rule = CLASS_RULE.testRule();

  @Test
  public void first() {
    contextIdentities.add(System.identityHashCode(rule.context()));
  }

  @Test
  public void second() {
    contextIdentities.add(System.identityHashCode(rule.context()));
  }

  @Test
  public void third() {
    contextIdentities.add(System.identityHashCode(rule.context()));
  }

  @Test
  public void fourth() {
    contextIdentities.add(System.identityHashCode(rule.context()));
  }

  @Test
  public void fifth() {
    contextIdentities.add(System.identityHashCode(rule.context()));
  }
}
