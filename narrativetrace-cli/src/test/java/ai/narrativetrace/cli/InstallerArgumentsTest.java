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

import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every flag {@code init} and {@code uninstall} accept, and every way one can be written wrong. */
class InstallerArgumentsTest {

  private static InstallerArguments parse(String... args) {
    return InstallerArguments.parse(List.of(args));
  }

  @Test
  void noFlagsMeansTheDefaults() {
    InstallerArguments parsed = parse();

    assertThat(parsed.error()).isNull();
    assertThat(parsed.help()).isFalse();
    assertThat(parsed.json()).isFalse();
    assertThat(parsed.from()).isNull();
    assertThat(parsed.options()).isEqualTo(InitOptions.defaults());
  }

  @Test
  void eachBooleanFlagSetsItsOwnOption() {
    assertThat(parse("--dry-run").options().dryRun()).isTrue();
    assertThat(parse("--write-existing").options().writeExisting()).isTrue();
    assertThat(parse("--force").options().force()).isTrue();
    assertThat(parse("--json").json()).isTrue();
    assertThat(parse("--help").help()).isTrue();
    assertThat(parse("-h").help()).isTrue();
  }

  @Test
  void everyFlagAtOnce() {
    InstallerArguments parsed =
        parse(
            "--dry-run",
            "--write-existing",
            "--force",
            "--json",
            "--only",
            "skills",
            "--vendor",
            "claude",
            "--from",
            "some.jar");

    assertThat(parsed.error()).isNull();
    assertThat(parsed.options())
        .isEqualTo(new InitOptions(true, true, true, Scope.SKILLS, Vendor.ON));
    assertThat(parsed.json()).isTrue();
    assertThat(parsed.from()).isEqualTo("some.jar");
  }

  @Test
  void onlyTakesEitherHalfOfTheInstall() {
    assertThat(parse("--only", "skills").options().scope()).isEqualTo(Scope.SKILLS);
    assertThat(parse("--only", "agents-md").options().scope()).isEqualTo(Scope.AGENTS_MD);
  }

  @Test
  void vendorTurnsTheClaudeFlavourOnOrOff() {
    assertThat(parse("--vendor", "claude").options().vendorClaude()).isEqualTo(Vendor.ON);
    assertThat(parse("--vendor", "none").options().vendorClaude()).isEqualTo(Vendor.OFF);
  }

  @Test
  void aValueMayBeAttachedWithAnEqualsSign() {
    assertThat(parse("--only=agents-md").options().scope()).isEqualTo(Scope.AGENTS_MD);
    assertThat(parse("--vendor=none").options().vendorClaude()).isEqualTo(Vendor.OFF);
    assertThat(parse("--from=some.jar").from()).isEqualTo("some.jar");
  }

  @Test
  void refusesToParseNothingAtAll() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> InstallerArguments.parse(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anUnknownFlagIsAnError() {
    assertThat(parse("--nope").error()).contains("--nope");
  }

  @Test
  void aBareWordIsAnErrorRatherThanASilentlyIgnoredArgument() {
    assertThat(parse("skills").error()).contains("skills");
  }

  @Test
  void aValueFlagWithNothingAfterItIsAnError() {
    assertThat(parse("--only").error()).contains("--only");
    assertThat(parse("--vendor").error()).contains("--vendor");
    assertThat(parse("--from").error()).contains("--from");
  }

  @Test
  void anUnknownValueNamesWhatIsAccepted() {
    assertThat(parse("--only", "everything").error()).contains("skills").contains("agents-md");
    assertThat(parse("--vendor", "cursor").error()).contains("claude").contains("none");
  }

  @Test
  void anEmptyAttachedValueIsAnError() {
    assertThat(parse("--from=").error()).contains("--from");
    assertThat(parse("--only=").error()).contains("--only");
  }

  /** The first problem wins: reporting the second one would name a flag nobody has read yet. */
  @Test
  void theFirstErrorIsTheOneReported() {
    assertThat(parse("--nope", "--also-nope").error()).contains("--nope").doesNotContain("--also");
  }

  @Test
  void helpBeatsAnErrorSoAMistypedCommandCanStillAskHowItWorks() {
    assertThat(parse("--nope", "--help").help()).isTrue();
  }
}
