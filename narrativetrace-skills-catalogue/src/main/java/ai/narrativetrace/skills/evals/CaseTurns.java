/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * INTENT: the scripted user replies that drive a Tier B case past its first turn — read from the
 * case's own {@code case.json}, beside the fixture and the registry it names.
 *
 * <p>A case without them is one turn, which is every case written before the feedback ones: the
 * agent is handed {@code prompt.md} and whatever it does with that is the whole trial. A case WITH
 * them is a conversation the runner drives, and that is the only way to measure something that must
 * happen in a LATER turn than the one that asked — an approval.
 *
 * <p>The declaration is keyed BY TURN NUMBER ({@code "turns": { "2": "yes, file it" }}) rather than
 * being a bare array, because the turn a reply is given at is the subject of these cases and an
 * array makes it implicit in its own ordering. Turn 1 is always the case's own {@code prompt.md}
 * and may not be declared here.
 *
 * <p><b>@llmNote</b> Every way of being wrong here THROWS rather than degrading to a shorter
 * conversation. A case whose second turn silently did not happen is a case that passes its approval
 * grader for the wrong reason — nothing was filed because nothing was asked — so a malformed
 * declaration must stop the trial, not shorten it.
 */
public final class CaseTurns {

  private static final String FIELD = "turns";

  /** The {@code "turns": { ... }} object, captured without its braces. */
  private static final Pattern TURNS_OBJECT =
      Pattern.compile("\"" + FIELD + "\"\\s*:\\s*\\{([^{}]*)\\}");

  /** One {@code "<key>": "<reply>"} entry; the reply keeps its JSON escapes until unescaped. */
  private static final Pattern ENTRY =
      Pattern.compile("\"([^\"]*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  private CaseTurns() {}

  /** The replies {@code caseDir} declares, in turn order; empty for a single-turn case. */
  public static List<String> scriptedRepliesFor(Path caseDir) {
    return CaseManifest.rawObjectField(caseDir, FIELD, TURNS_OBJECT)
        .map(body -> orderedReplies(caseDir, body))
        .orElseGet(List::of);
  }

  /** The declared entries, read into turn order and checked for every way of being undrivable. */
  private static List<String> orderedReplies(Path caseDir, String body) {
    Map<Integer, String> byTurn = new TreeMap<>();
    Matcher entries = ENTRY.matcher(body);
    while (entries.find()) {
      byTurn.put(turnNumber(caseDir, entries.group(1)), reply(caseDir, entries.group(2)));
    }
    if (byTurn.isEmpty()) {
      throw new IllegalArgumentException(
          CaseManifest.manifestPath(caseDir) + " declares \"turns\" and lists no reply in it");
    }
    requireContiguousFromTheSecondTurn(caseDir, byTurn);
    return List.copyOf(new ArrayList<>(byTurn.values()));
  }

  /** A key is a turn number, and the first turn a reply can be given at is the second. */
  private static int turnNumber(Path caseDir, String key) {
    int turn;
    try {
      turn = Integer.parseInt(key.strip());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          CaseManifest.manifestPath(caseDir)
              + " declares a turn keyed \""
              + key
              + "\", which is not a turn number",
          e);
    }
    if (turn < 2) {
      throw new IllegalArgumentException(
          CaseManifest.manifestPath(caseDir)
              + " declares a reply at turn "
              + turn
              + " — turn 1 is the case's own prompt.md, so the first reply is turn 2");
    }
    return turn;
  }

  private static String reply(Path caseDir, String escaped) {
    String unescaped = unescape(escaped);
    if (unescaped.isBlank()) {
      throw new IllegalArgumentException(
          CaseManifest.manifestPath(caseDir)
              + " declares a blank reply — a turn with nothing to say cannot be driven");
    }
    return unescaped;
  }

  /**
   * Turns 2..n, with nothing missing. A gap is a turn with no input, and closing it quietly would
   * hand a later turn's reply to an earlier turn — for an approval case, the difference between a
   * question answered and a question never asked.
   */
  private static void requireContiguousFromTheSecondTurn(
      Path caseDir, Map<Integer, String> byTurn) {
    int expected = 2;
    for (int declared : byTurn.keySet()) {
      if (declared != expected) {
        throw new IllegalArgumentException(
            CaseManifest.manifestPath(caseDir)
                + " declares no reply for turn "
                + expected
                + ", so turn "
                + declared
                + " could never be reached");
      }
      expected++;
    }
  }

  /**
   * The four JSON string escapes a user's own sentence can need. A reply is text somebody typed, so
   * a quote and a line break are ordinary content here.
   *
   * <p><b>@llmNote</b> Deliberately not a general JSON unescaper: {@code \\uXXXX} is absent because
   * a scripted reply carrying one is a reply written in escapes rather than in words, and this file
   * is UTF-8 — the character itself belongs in it.
   */
  private static String unescape(String escaped) {
    StringBuilder out = new StringBuilder(escaped.length());
    int at = 0;
    while (at < escaped.length()) {
      char c = escaped.charAt(at);
      boolean isEscape = c == '\\' && at + 1 < escaped.length();
      out.append(isEscape ? unescaped(escaped.charAt(at + 1)) : c);
      at += isEscape ? 2 : 1;
    }
    return out.toString();
  }

  private static char unescaped(char escaped) {
    return switch (escaped) {
      case 'n' -> '\n';
      case 't' -> '\t';
      case 'r' -> '\r';
      default -> escaped;
    };
  }
}
