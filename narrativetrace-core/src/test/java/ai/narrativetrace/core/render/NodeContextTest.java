/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

class NodeContextTest {

  @org.junit.jupiter.api.Test
  void pushThenPopReturnsTheSameValue() {
    var ctx = new NodeContext<String, Integer>();
    ctx.push("a", 1);

    assertThat(ctx.pop("a")).isEqualTo(1);
  }

  @org.junit.jupiter.api.Test
  void peekDoesNotConsumeTheValue() {
    var ctx = new NodeContext<String, Integer>();
    ctx.push("a", 1);

    assertThat(ctx.peek("a")).isEqualTo(1);
    assertThat(ctx.pop("a")).as("peek must not have consumed it").isEqualTo(1);
  }

  /**
   * The shape a genuine cycle produces: the outer occurrence is pushed, then peeked (entered) but
   * not yet popped, when the cycle-closing occurrence of the <em>same key</em> is pushed and popped
   * entirely before the outer occurrence is finally popped. The outer context must survive intact.
   */
  @org.junit.jupiter.api.Test
  void aNestedPushAndPopForTheSameKeyDoesNotDisturbTheOuterOccurrence() {
    var ctx = new NodeContext<String, Integer>();

    ctx.push("a", 100); // outer occurrence pushed
    assertThat(ctx.peek("a")).isEqualTo(100); // outer occurrence entered

    ctx.push("a", 200); // cycle-closing occurrence of the same key, pushed while outer is open
    assertThat(ctx.pop("a")).isEqualTo(200); // cycle-closing occurrence consumed first

    assertThat(ctx.pop("a")).as("outer occurrence's own context, still underneath").isEqualTo(100);
  }

  @org.junit.jupiter.api.Test
  void distinctKeysDoNotInterfere() {
    var ctx = new NodeContext<String, Integer>();
    ctx.push("a", 1);
    ctx.push("b", 2);

    assertThat(ctx.pop("a")).isEqualTo(1);
    assertThat(ctx.pop("b")).isEqualTo(2);
  }

  @org.junit.jupiter.api.Test
  void keysAreComparedByIdentityNotEquals() {
    var ctx = new NodeContext<String, Integer>();
    var a1 = new String("shared"); // NOPMD - deliberately distinct instance for the identity check
    var a2 = new String("shared"); // NOPMD - deliberately distinct instance for the identity check
    assertThat(a1).isEqualTo(a2).isNotSameAs(a2);

    ctx.push(a1, 1);
    ctx.push(a2, 2);

    // Distinct instances get distinct slots even though they are `.equals()` — popping one must not
    // silently return the other's value.
    assertThat(ctx.pop(a1)).isEqualTo(1);
    assertThat(ctx.pop(a2)).isEqualTo(2);
  }
}
