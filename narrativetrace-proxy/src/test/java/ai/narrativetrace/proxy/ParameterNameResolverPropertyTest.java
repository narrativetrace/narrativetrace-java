/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ParameterNameResolverPropertyTest {

  private final ValueRenderer renderer = new ValueRenderer();

  @Property
  void redactedParametersNeverExposeOriginalValue(@ForAll @IntRange(min = 1, max = 10) int count) {
    var names = new String[count];
    var redacted = new boolean[count];
    var args = new Object[count];
    for (int i = 0; i < count; i++) {
      names[i] = "param" + i;
      redacted[i] = true;
      args[i] = "secret-" + i;
    }

    var captures = ParameterNameResolver.resolve(names, redacted, args, renderer);

    for (int i = 0; i < count; i++) {
      assertThat(captures.get(i).renderedValue()).isEqualTo("[REDACTED]");
      assertThat(captures.get(i).renderedValue()).doesNotContain("secret");
    }
  }

  @Property
  void outputSizeEqualsInputSize(@ForAll @IntRange(min = 0, max = 10) int count) {
    var names = new String[count];
    var redacted = new boolean[count];
    var args = new Object[count];
    for (int i = 0; i < count; i++) {
      names[i] = "p" + i;
      redacted[i] = false;
      args[i] = i;
    }

    var captures = ParameterNameResolver.resolve(names, redacted, args, renderer);
    assertThat(captures).hasSize(count);
  }
}
