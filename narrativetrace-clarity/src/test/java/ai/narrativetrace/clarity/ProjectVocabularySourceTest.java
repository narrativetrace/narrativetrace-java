/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectVocabularySourceTest {

  @Test
  void noneResolvesToTheEmptyVocabularyForAnyDirectory() {
    var source = ProjectVocabularySource.none();

    assertThat(source.resolve(Path.of("anywhere"))).isEqualTo(DomainVocabulary.empty());
    assertThat(source.resolve(null)).isEqualTo(DomainVocabulary.empty());
  }

  @Test
  void anImplementationSeesTheDirectoryItWasGiven() {
    var seen = new Path[1];
    ProjectVocabularySource source =
        dir -> {
          seen[0] = dir;
          return DomainVocabulary.of(Set.of("fold"), Set.of());
        };

    var vocabulary = source.resolve(Path.of("repo-root"));

    assertThat(seen[0]).isEqualTo(Path.of("repo-root"));
    assertThat(vocabulary.isDomainVerb("fold")).isTrue();
  }
}
