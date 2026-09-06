/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScaffoldingBundleTest {

  @Test
  void spanishBundleTranslatesRendererScaffolding() {
    var bundle = ScaffoldingBundle.forLocale("es");

    assertThat(bundle.returns()).isEqualTo("devuelve");
    assertThat(bundle.throwsLabel()).isEqualTo("lanza");
    assertThat(bundle.gapsHeading()).isEqualTo("Vacíos del glosario");
  }

  @Test
  void chineseBundleTranslatesRendererScaffolding() {
    var bundle = ScaffoldingBundle.forLocale("zh-CN");

    assertThat(bundle.returns()).isEqualTo("返回");
    assertThat(bundle.background()).isEqualTo("在后台:");
  }

  @Test
  void unknownLocaleFallsBackToEnglishNotTheHostDefaultLocale() {
    var bundle = ScaffoldingBundle.forLocale("de");

    assertThat(bundle.returns()).isEqualTo("returns");
    assertThat(bundle.throwsLabel()).isEqualTo("throws");
    assertThat(bundle.incomplete()).isEqualTo("incomplete");
    assertThat(bundle.fork()).isEqualTo("fork");
    assertThat(bundle.join()).isEqualTo("join");
    assertThat(bundle.background()).isEqualTo("In the background:");
    assertThat(bundle.gapsHeading()).isEqualTo("Glossary gaps");
  }

  @Test
  void rejectsABlankLocale() {
    assertThatThrownBy(() -> ScaffoldingBundle.forLocale(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
