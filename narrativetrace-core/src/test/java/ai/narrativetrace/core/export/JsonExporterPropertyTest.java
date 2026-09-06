/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class JsonExporterPropertyTest {

  private final JsonExporter exporter = new JsonExporter();

  @Property
  void exportProducesBalancedBracesAndRequiredFields(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String methodName) {
    var sig = new MethodSignature(className, methodName, List.of());
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("ok"));
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.export(tree);

    assertThat(countChar(json, '{')).isEqualTo(countChar(json, '}'));
    assertThat(countChar(json, '[')).isEqualTo(countChar(json, ']'));
    assertThat(json).contains("\"className\": \"" + className + "\"");
    assertThat(json).contains("\"methodName\": \"" + methodName + "\"");
    assertThat(json).contains("\"type\": \"enter\"");
    assertThat(json).contains("\"type\": \"exit\"");
  }

  @Property
  void paramValueEscapingRoundTrips(@ForAll @StringLength(min = 1, max = 30) String value)
      throws Exception {
    var param = new ParameterCapture("p", value, false);
    var sig = new MethodSignature("C", "m", List.of(param));
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("ok"));
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.export(tree);

    // A real JSON parser is the round-trip oracle: the document must be valid JSON and the
    // parsed value must recover the original string exactly, whatever characters it contains.
    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/events/0/parameters/0/value").asText()).isEqualTo(value);
  }

  private static long countChar(String s, char target) {
    return s.chars().filter(c -> c == target).count();
  }
}
