/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class OutputDirectoryResolverPropertyTest {

  private final OutputDirectoryResolver resolver =
      new OutputDirectoryResolver(Path.of("/tmp/test"));

  @Property
  void traceFileSlugIsFilesystemSafe(
      @ForAll @AlphaChars @StringLength(min = 1, max = 40) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 40) String methodName) {
    var path = resolver.traceFile(className, methodName);
    var fileName = path.getFileName().toString();
    assertThat(fileName).matches("[a-z0-9_]+\\.md");
  }
}
