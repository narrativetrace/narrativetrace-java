/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

import java.util.ArrayList;

/**
 * A public method that leans on private helpers and on a public self-call. {@code withTax} computes
 * the wrong value on purpose: it is the call a default trace cannot show.
 */
public class DeepSample {

  public int total(int price, int quantity) {
    return withTax(price * quantity) + this.surcharge(quantity);
  }

  public int surcharge(int quantity) {
    return doubled(quantity);
  }

  public String label(int number) {
    return String.valueOf(number) + new ArrayList<String>().size();
  }

  private int withTax(int net) {
    return net + (net / 10) + 1;
  }

  private static int doubled(int number) {
    return number * 2;
  }
}
