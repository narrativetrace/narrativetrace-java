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
package ai.narrativetrace.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.init.InitOptions.Scope;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Argument-parsing boundaries beyond {@code InstallerArgumentsTest}: a flag repeated with two
 * different values, a value that itself looks like another flag, an empty-string element, and help
 * arriving alongside an error the reader has already recorded.
 */
class InstallerArgumentsAdversarialM3Test {

  private static InstallerArguments parse(String... args) {
    return InstallerArguments.parse(List.of(args));
  }

  @Test
  void aRepeatedOnlyFlagLetsTheLastOccurrenceWin() {
    InstallerArguments parsed = parse("--only", "skills", "--only", "agents-md");

    assertThat(parsed.error()).isNull();
    assertThat(parsed.options().scope()).isEqualTo(Scope.AGENTS_MD);
  }

  /**
   * {@code --vendor}'s value reading does not check whether the value looks like a flag; {@code
   * --force} is consumed AS THE VALUE, fails validation, and is never re-read as its own switch —
   * so {@code force} stays {@code false} despite the token appearing in the argument list.
   */
  @Test
  void aFlagShapedValueIsConsumedAsTheValueRatherThanReparsedAsAFlag() {
    InstallerArguments parsed = parse("--vendor", "--force");

    assertThat(parsed.error()).contains("--vendor takes claude or none").contains("--force");
    assertThat(parsed.options().force()).isFalse();
  }

  /**
   * An empty argument — what a shell hands over for an unquoted, unset variable — has to read as
   * something. Quoted, so the message says which token it could not read instead of trailing off
   * after the colon.
   */
  @Test
  void anEmptyStringArgumentIsAnUnknownOptionRatherThanACrash() {
    assertThat(parse("").error()).isEqualTo("unknown option: \"\"");
  }

  /**
   * The reader itself sets both fields when they both occur; it is {@code Cli} that decides help
   * wins. This pins the state {@code Cli} relies on rather than the caller-side decision.
   */
  @Test
  void helpAndAnEarlierErrorCanBothBeSetAtOnceSoTheCallerDecidesWhichWins() {
    InstallerArguments parsed = parse("--only", "bogus", "-h");

    assertThat(parsed.help()).isTrue();
    assertThat(parsed.error()).contains("bogus");
  }
}
