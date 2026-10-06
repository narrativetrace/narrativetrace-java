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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The provenance line decides, years later, whether a skill directory is ours. These cases pin
 * where it goes (after the frontmatter, never before), what counts as one (column 0, whole line),
 * and that stamping is idempotent.
 */
class ProvenanceTest {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static final String PAGE =
      "---\nname: narrativetrace-doctor\ndescription: d\n---\n\nBody\n";

  @Test
  void readsBackTheCoordinateItWrote() {
    String stamped = Provenance.stamp(PAGE, COORDINATE);

    assertThat(Provenance.coordinateIn(stamped)).hasValue(COORDINATE);
  }

  @Test
  void putsTheLineDirectlyAfterTheFrontmatterNeverBeforeIt() {
    String stamped = Provenance.stamp(PAGE, COORDINATE);

    assertThat(stamped)
        .isEqualTo(
            "---\nname: narrativetrace-doctor\ndescription: d\n---\n"
                + Provenance.line(COORDINATE)
                + "\n\nBody\n");
    assertThat(stamped).startsWith("---\n");
  }

  @Test
  void putsTheLineAtTheTopOfAPageWithoutFrontmatter() {
    String stamped = Provenance.stamp("Body only\n", COORDINATE);

    assertThat(stamped).isEqualTo(Provenance.line(COORDINATE) + "\n\nBody only\n");
    assertThat(Provenance.coordinateIn(stamped)).hasValue(COORDINATE);
  }

  @Test
  void treatsAnUnclosedFrontmatterFenceAsNoFrontmatter() {
    String stamped = Provenance.stamp("---\nname: x\nbody\n", COORDINATE);

    assertThat(stamped).startsWith(Provenance.line(COORDINATE));
  }

  @Test
  void stampingTwiceIsStampingOnce() {
    String once = Provenance.stamp(PAGE, COORDINATE);

    assertThat(Provenance.stamp(once, COORDINATE)).isEqualTo(once);
  }

  @Test
  void restampingReplacesAnOlderCoordinate() {
    String older = Provenance.stamp(PAGE, "ai.narrativetrace:narrativetrace-skills:0.0.9");

    String newer = Provenance.stamp(older, COORDINATE);

    assertThat(Provenance.coordinateIn(newer)).hasValue(COORDINATE);
    assertThat(newer).doesNotContain("0.0.9");
    assertThat(newer).isEqualTo(Provenance.stamp(PAGE, COORDINATE));
  }

  @Test
  void keepsTheFilesLineEndings() {
    String stamped = Provenance.stamp(PAGE.replace("\n", "\r\n"), COORDINATE);

    assertThat(stamped).doesNotContain("\r\r");
    assertThat(stamped).contains("---\r\n" + Provenance.line(COORDINATE) + "\r\n");
    assertThat(Provenance.coordinateIn(stamped)).hasValue(COORDINATE);
  }

  @Test
  void ignoresALineThatOnlyLooksLikeProvenance() {
    String indented = "---\nx\n---\n  " + Provenance.line(COORDINATE) + "\n";
    String truncated = "---\nx\n---\n" + Provenance.PREFIX + COORDINATE + "\n";
    String empty = "---\nx\n---\n" + Provenance.PREFIX + Provenance.SUFFIX.trim() + "\n";

    assertThat(Provenance.coordinateIn(indented)).isEmpty();
    assertThat(Provenance.coordinateIn(truncated)).isEmpty();
    assertThat(Provenance.coordinateIn(empty)).isEmpty();
    assertThat(Provenance.coordinateIn("")).isEmpty();
  }

  /** Prefix and suffix meet with nothing between them: a provenance line naming no carrier. */
  @Test
  void readsNoCoordinateFromALineThatNamesNone() {
    String empty = Provenance.PREFIX + Provenance.SUFFIX;

    assertThat(Provenance.coordinateIn("---\nx\n---\n" + empty + "\n")).isEmpty();
    assertThat(Provenance.stamp("---\nx\n---\n" + empty + "\n", COORDINATE))
        .isEqualTo("---\nx\n---\n" + Provenance.line(COORDINATE) + "\n");
  }

  @Test
  void stampsAnEmptyPage() {
    assertThat(Provenance.stamp("", COORDINATE)).isEqualTo(Provenance.line(COORDINATE) + "\n\n");
  }

  /**
   * A page somebody concatenated carries two provenance lines. The FIRST one wins, and that is the
   * coordinate staleness is judged against — so a second, older line appended below cannot make a
   * current install look stale, or an older one look current.
   */
  @Test
  void readsTheFirstCoordinateWhenAPageCarriesTwo() {
    String second = "ai.narrativetrace:narrativetrace-skills:0.0.1";
    String page =
        "---\nx\n---\n" + Provenance.line(COORDINATE) + "\nbody\n" + Provenance.line(second) + "\n";

    assertThat(Provenance.coordinateIn(page)).hasValue(COORDINATE);
    assertThat(Provenance.stamp(page, second))
        .as("stamping leaves exactly one line, whichever it replaced")
        .isEqualTo("---\nx\n---\n" + Provenance.line(second) + "\nbody\n");
  }

  @Test
  void findsTheLineInAPageWhoseLastLineHasNoTerminator() {
    assertThat(Provenance.coordinateIn("---\nx\n---\n" + Provenance.line(COORDINATE)))
        .hasValue(COORDINATE);
  }
}
