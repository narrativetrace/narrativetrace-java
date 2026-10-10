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
package ai.narrativetrace.tooling.frameworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WiringSnippetsTest {

  /**
   * The resource and the table name the same fixtures: one section per source-wired row, in table
   * order, each marker naming exactly the row's fixture and region. {@code snippetCheck} then keeps
   * every block's body equal to that fixture, so the chain table → resource → compiled fixture has
   * no link a person types twice.
   */
  @Test
  void hasOneSectionPerSourceWiredRowNamingTheRowsFixture() {
    List<String> sourceWired =
        FrameworkTable.ROWS.stream()
            .filter(row -> row.wiring() instanceof Wiring.Snippet)
            .map(FrameworkRow::id)
            .toList();
    assertThat(WiringSnippets.entries().keySet()).containsExactlyElementsOf(sourceWired);
    for (FrameworkRow row : FrameworkTable.ROWS) {
      if (row.wiring() instanceof Wiring.Snippet snippet) {
        WiringSnippets.Entry entry = WiringSnippets.entry(row.id());
        assertThat(entry.fixture()).as(row.id()).isEqualTo(snippet.fixture());
        assertThat(entry.region()).as(row.id()).isEqualTo(snippet.region());
        assertThat(entry.body()).as(row.id()).isNotBlank();
      }
    }
  }

  @Test
  void aRowWithoutASectionIsABuildDefectNamedAsSuch() {
    assertThatThrownBy(() -> WiringSnippets.text("agent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("agent");
  }

  @Test
  void parsesTheFirstFencedBlockAfterEachSectionsMarker() {
    String markdown =
        """
        # Title

        ## alpha

        <!-- snippet: a/A.java region=wiring -->
        ```java
        line one

        line three
        ```
        <!-- /snippet -->

        ## beta

        <!-- snippet: b/app.properties -->
        ```properties
        key=value
        ```
        <!-- /snippet -->
        """;
    var entries = WiringSnippets.parse(markdown);
    assertThat(entries.keySet()).containsExactly("alpha", "beta");
    assertThat(entries.get("alpha"))
        .isEqualTo(new WiringSnippets.Entry("a/A.java", "wiring", "line one\n\nline three\n"));
    assertThat(entries.get("beta"))
        .isEqualTo(new WiringSnippets.Entry("b/app.properties", null, "key=value\n"));
  }

  @Test
  void aFenceWithoutAMarkerOrASectionIsNotAnEntry() {
    String markdown =
        """
        ```java
        orphan
        ```
        ## gamma

        ```java
        no marker
        ```
        """;
    assertThat(WiringSnippets.parse(markdown)).isEmpty();
  }

  @Test
  void aHeadingInsideAFenceDoesNotStartASection() {
    String markdown =
        """
        ## delta

        <!-- snippet: d/D.md -->
        ```markdown
        ## not-a-section
        ```
        <!-- /snippet -->
        """;
    var entries = WiringSnippets.parse(markdown);
    assertThat(entries.keySet()).containsExactly("delta");
    assertThat(entries.get("delta").body()).isEqualTo("## not-a-section\n");
  }

  /** An unmarked fence before any section is skipped whole, headings and markers inside it too. */
  @Test
  void anOrphanFenceBeforeTheFirstSectionHidesWhatIsInsideIt() {
    String markdown =
        """
        ```markdown
        ## fake

        <!-- snippet: f/F.java -->
        ```
        ## real

        <!-- snippet: r/R.java -->
        ```java
        body
        ```
        """;
    assertThat(WiringSnippets.parse(markdown).keySet()).containsExactly("real");
  }

  /** A block whose fence never closes is not an entry — snippetCheck would never write one. */
  @Test
  void anUnterminatedBlockIsNotAnEntry() {
    String markdown = "## open\n\n<!-- snippet: o/O.java -->\n```java\nbody\n";
    assertThat(WiringSnippets.parse(markdown)).isEmpty();
  }

  @Test
  void aMarkerOnTheLastLineHasNoBlock() {
    assertThat(WiringSnippets.parse("## last\n<!-- snippet: l/L.java -->")).isEmpty();
  }

  @Test
  void anEmptyBlockIsAnEntryWithAnEmptyBody() {
    var entries = WiringSnippets.parse("## e\n<!-- snippet: e/E.java -->\n```java\n```\n");
    assertThat(entries.get("e")).isEqualTo(new WiringSnippets.Entry("e/E.java", null, ""));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<!-- snippet: -->",
        "<!-- snippets: x/X.java -->",
        "<!-- x/X.java -->",
        "snippet: x/X.java -->",
        "<!-- snippet: x/X.java",
      })
  void aLineThatIsNotASnippetMarkerOpensNoEntry(String line) {
    assertThat(WiringSnippets.parse("## m\n" + line + "\n```java\nbody\n```\n")).isEmpty();
  }

  @Test
  void theRegionIsReadWhereverItSitsAmongTheMarkersWords() {
    var entries =
        WiringSnippets.parse(
            "## r\n<!-- snippet: r/R.java mask=x region=wiring -->\n```java\nb\n```\n");
    assertThat(entries.get("r").region()).isEqualTo("wiring");
  }
}
