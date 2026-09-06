/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class TemplateParserPropertyTest {

  @Property
  void resolvedOutputContainsAllLiteralSegments(
      @ForAll @AlphaChars @StringLength(min = 1, max = 10) String prefix,
      @ForAll @AlphaChars @StringLength(min = 1, max = 10) String middle,
      @ForAll @AlphaChars @StringLength(min = 1, max = 10) String suffix) {
    var template = prefix + "{a}" + middle + "{b}" + suffix;
    var result = TemplateParser.resolve(template, Map.of("a", "X", "b", "Y"));

    assertThat(result).isEqualTo(prefix + "X" + middle + "Y" + suffix);
  }

  @Property
  void unresolvedPlaceholdersPreservedVerbatim(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String key) {
    var template = "before {" + key + "} after";
    var result = TemplateParser.resolve(template, Map.of());

    assertThat(result).isEqualTo("before {" + key + "} after");
  }
}
