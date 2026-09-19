/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Package-private because {@link PlatformTypes} itself is: its origin decision is internal to
 * {@code render}, never a public API a caller reasons about directly.
 */
class PlatformTypesTest {

  /**
   * A platform-defined class that is neither an exact {@code STATELESS_LEAVES} entry nor an
   * instance of a {@code STATELESS_LEAF_FAMILIES} member — the fall-through {@code false} at the
   * bottom of {@link PlatformTypes#isStatelessLeaf}, distinct from the earlier {@code
   * !isPlatformDefined} short-circuit.
   */
  @Test
  void aPlatformCollectionIsNotAStatelessLeaf() {
    assertThat(PlatformTypes.isStatelessLeaf(ArrayList.class)).isFalse();
  }

  @Test
  void anExactStatelessLeafIsRecognized() {
    assertThat(PlatformTypes.isStatelessLeaf(UUID.class)).isTrue();
  }

  /**
   * {@link PlatformTypes#overrides} answers {@code true} (do not trust it) when the reflective
   * lookup cannot resolve the method at all — the safe reading for "unknown" is the same as
   * "overridden."
   */
  @Test
  void overridesAnswersTrueWhenTheMethodCannotBeResolvedAtAll() {
    assertThat(
            PlatformTypes.overrides(ArrayList.class, Collection.class, "noSuchMethodOnAnyOfThese"))
        .isTrue();
  }
}
