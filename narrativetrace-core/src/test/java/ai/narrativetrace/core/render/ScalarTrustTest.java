/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ScalarTrustTest {

  @Test
  void trustsEveryJdkBoxedNumericType() {
    assertThat(ScalarTrust.isTrustedNumeric(Byte.valueOf((byte) 1))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(Short.valueOf((short) 1))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(Integer.valueOf(1))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(Long.valueOf(1L))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(Float.valueOf(1f))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(Double.valueOf(1d))).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(BigInteger.ONE)).isTrue();
    assertThat(ScalarTrust.isTrustedNumeric(BigDecimal.ONE)).isTrue();
  }

  static class CustomNumber extends Number {
    @Override
    public int intValue() {
      return 0;
    }

    @Override
    public long longValue() {
      return 0;
    }

    @Override
    public float floatValue() {
      return 0;
    }

    @Override
    public double doubleValue() {
      return 0;
    }
  }

  @Test
  void distrustsAnyOtherNumberSubclass() {
    assertThat(ScalarTrust.isTrustedNumeric(new CustomNumber())).isFalse();
  }

  static class HostileBigDecimal extends BigDecimal {
    HostileBigDecimal() {
      super(0);
    }
  }

  static class HostileBigInteger extends BigInteger {
    HostileBigInteger() {
      super("0");
    }
  }

  @Test
  void distrustsSubclassesOfNonFinalJdkNumericTypesByExactClass() {
    // BigInteger/BigDecimal are not final; matching by exact class (not instanceof) is what
    // closes the subclass hole for them.
    assertThat(ScalarTrust.isTrustedNumeric(new HostileBigDecimal())).isFalse();
    assertThat(ScalarTrust.isTrustedNumeric(new HostileBigInteger())).isFalse();
  }
}
