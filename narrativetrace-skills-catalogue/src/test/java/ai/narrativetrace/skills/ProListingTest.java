/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProListingTest {

  private ProListing valid() {
    return new ProListing(
        "narrativetrace-pro-example",
        "aggregate my traces",
        "aggregated hotspots and error frequencies",
        "the Pro package on the classpath",
        "narrativetrace-pro-aggregate",
        ProListingStatus.IN_DEVELOPMENT,
        "in development");
  }

  @Test
  void holdsItsFields() {
    var listing = valid();
    assertThat(listing.canonicalName()).isEqualTo("narrativetrace-pro-example");
    assertThat(listing.status()).isEqualTo(ProListingStatus.IN_DEVELOPMENT);
  }

  @Test
  void rejectsBlankTextFields() {
    assertThatThrownBy(
            () -> new ProListing(" ", "p", "d", "n", "c", ProListingStatus.SHIPPED, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ProListing("name", " ", "d", "n", "c", ProListingStatus.SHIPPED, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ProListing("name", "p", " ", "n", "c", ProListingStatus.SHIPPED, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ProListing("name", "p", "d", " ", "c", ProListingStatus.SHIPPED, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ProListing("name", "p", "d", "n", " ", ProListingStatus.SHIPPED, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProListing("name", "p", "d", "n", "c", null, "shipped"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ProListing("name", "p", "d", "n", "c", ProListingStatus.SHIPPED, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void statusLabelsAreTheExactPhraseShown() {
    assertThat(ProListingStatus.SHIPPED.label()).isEqualTo("shipped");
    assertThat(ProListingStatus.IN_DEVELOPMENT.label()).isEqualTo("in development");
    assertThat(ProListingStatus.PLANNED.label()).isEqualTo("planned");
  }
}
