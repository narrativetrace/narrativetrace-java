/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgsTest {

  @Test
  void requiresVersion() {
    assertThrows(IllegalArgumentException.class, () -> Args.parse(new String[] {}));
  }

  @Test
  void parsesEveryFlag() {
    Args args =
        Args.parse(
            new String[] {
              "--version=0.2.1",
              "--contract=../documentation/contract.yaml",
              "--out=build/result.json",
              "--registry-base=https://example.test/maven2"
            });
    assertEquals("0.2.1", args.version());
    assertEquals("../documentation/contract.yaml", args.contractPath());
    assertEquals("build/result.json", args.outPath());
    assertEquals("https://example.test/maven2", args.registryBase());
  }

  @Test
  void defaultsContractPathAndRegistryBaseAndLeavesOutPathNull() {
    Args args = Args.parse(new String[] {"--version=0.2.1"});
    assertEquals("documentation/contract.yaml", args.contractPath());
    assertEquals("https://repo1.maven.org/maven2", args.registryBase());
    assertNull(args.outPath());
  }
}
