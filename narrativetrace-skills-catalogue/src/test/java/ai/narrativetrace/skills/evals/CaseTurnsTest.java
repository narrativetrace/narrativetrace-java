/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A case declares the scripted user replies that drive it past its first turn, and the turn each
 * one is given at, in its own {@code case.json}. Most cases declare none and are one turn, which is
 * what every case before the feedback ones was.
 */
class CaseTurnsTest {

  private static Path caseWith(Path dir, String manifest) throws IOException {
    Files.writeString(dir.resolve("case.json"), manifest);
    return dir;
  }

  @Test
  void aCaseWithNoManifestAtAllIsOneTurn(@TempDir Path dir) {
    assertThat(CaseTurns.scriptedRepliesFor(dir)).isEmpty();
  }

  @Test
  void aCaseThatDeclaresNoTurnsIsOneTurn(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"fixture\": \"evals/fixtures/empty-project\" }");

    assertThat(CaseTurns.scriptedRepliesFor(caseDir)).isEmpty();
  }

  @Test
  void readsTheReplyADeclaredSecondTurnIsGiven(@TempDir Path dir) throws IOException {
    Path caseDir =
        caseWith(
            dir,
            "{ \"fixture\": \"evals/fixtures/feedback-false-positive\","
                + " \"turns\": { \"2\": \"yes, file it\" } }");

    assertThat(CaseTurns.scriptedRepliesFor(caseDir)).containsExactly("yes, file it");
  }

  /** Declared out of order in the file; a conversation is still driven in turn order. */
  @Test
  void readsEveryReplyInTurnOrderWhateverOrderTheFileListedThemIn(@TempDir Path dir)
      throws IOException {
    Path caseDir =
        caseWith(dir, "{ \"turns\": { \"3\": \"no, stop there\", \"2\": \"show me\" } }");

    assertThat(CaseTurns.scriptedRepliesFor(caseDir)).containsExactly("show me", "no, stop there");
  }

  /**
   * Turn 1 is the case's own {@code prompt.md}. A reply declared "at turn 1" would replace it, so
   * the case would run a prompt nobody could read out of the case directory.
   */
  @Test
  void refusesAReplyDeclaredAtTheFirstTurn(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { \"1\": \"do it\" } }");

    assertThatThrownBy(() -> CaseTurns.scriptedRepliesFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("turn 1 is the case's own prompt.md");
  }

  /**
   * A gap is a turn with no input, which cannot be driven at all — and silently closing it would
   * hand turn 3's reply to turn 2, which for an approval case is the difference between a question
   * answered and a question skipped.
   */
  @Test
  void refusesAGapInTheTurnNumbers(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { \"2\": \"yes\", \"4\": \"and again\" } }");

    assertThatThrownBy(() -> CaseTurns.scriptedRepliesFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3");
  }

  @Test
  void refusesATurnKeyThatIsNotATurnNumber(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { \"second\": \"yes\" } }");

    assertThatThrownBy(() -> CaseTurns.scriptedRepliesFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("second");
  }

  /** A field somebody meant to fill in is an error, exactly as an empty "fixture" field is. */
  @Test
  void refusesADeclaredTurnsObjectThatListsNoReply(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { } }");

    assertThatThrownBy(() -> CaseTurns.scriptedRepliesFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no reply");
  }

  @Test
  void refusesABlankReplyBecauseATurnWithNothingToSayCannotBeDriven(@TempDir Path dir)
      throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { \"2\": \"   \" } }");

    assertThatThrownBy(() -> CaseTurns.scriptedRepliesFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  /**
   * A scripted reply is the USER's words, so it is ordinary text: it may carry the quotes and the
   * line breaks any sentence carries, and a grader compares the agent's behaviour against exactly
   * these bytes.
   */
  @Test
  void unescapesWhatJsonHadToEscapeInTheUsersOwnWords(@TempDir Path dir) throws IOException {
    Path caseDir =
        caseWith(
            dir, "{ \"turns\": { \"2\": \"yes, file it \\\"publicly\\\"\\nunder my name\" } }");

    assertThat(CaseTurns.scriptedRepliesFor(caseDir))
        .containsExactly("yes, file it \"publicly\"\nunder my name");
  }

  /** A reply pasted from a Windows terminal carries CRLF; both halves are one line break. */
  @Test
  void unescapesACarriageReturnAsWell(@TempDir Path dir) throws IOException {
    Path caseDir = caseWith(dir, "{ \"turns\": { \"2\": \"yes\\r\\nfile it\" } }");

    assertThat(CaseTurns.scriptedRepliesFor(caseDir)).containsExactly("yes\r\nfile it");
  }
}
