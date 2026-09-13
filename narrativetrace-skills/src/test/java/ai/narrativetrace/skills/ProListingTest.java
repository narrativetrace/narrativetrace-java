/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
