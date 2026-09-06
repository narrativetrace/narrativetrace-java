/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

public class TypeVariety {

  public boolean isReady() {
    return true;
  }

  public long timestamp() {
    return 1234567890L;
  }

  public double ratio() {
    return 3.14;
  }

  public float weight() {
    return 2.5f;
  }

  public void doNothing() {}

  public String greeting() {
    return "hello";
  }

  public char initial() {
    return 'A';
  }

  public short shortValue() {
    return 42;
  }

  public byte byteValue() {
    return 7;
  }

  public String allPrimitiveParams(boolean flag, char letter, byte b, short s, float f) {
    return "" + flag + letter + b + s + f;
  }

  public int manyParams(int a, int b, int c, int d, int e, int f, int g) {
    return a + b + c + d + e + f + g;
  }
}
