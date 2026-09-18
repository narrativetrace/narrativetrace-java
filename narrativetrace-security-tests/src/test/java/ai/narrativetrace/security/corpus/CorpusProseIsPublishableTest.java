/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The corpus's own PROSE — the fields a reader reads, not the bytes a case plants — must be
 * publishable as it stands.
 *
 * <p>INTENT: The hostile corpus is the cross-port master copy: every runtime mirrors these files
 * byte-identically, and they ship in the public snapshot of each. A row written during private work
 * therefore carries that work's vocabulary straight into four public repositories — a commit SHA
 * nobody outside can resolve, or the name of an internal process — and the publish reference gate
 * rejects the mirrored file downstream, in a repository whose author cannot fix the text. Catching
 * it here, in the master copy, is the only place the fix is one edit rather than five.
 *
 * <p><b>@llmNote</b> Scans {@code id}, {@code kind}, {@code description} and {@code member} ONLY.
 * The payload fields — a canary, a value, a field name — are the hostile data itself: a national-id
 * shape or a token fixture may legitimately be a long run of hex digits, and a deny-list vocabulary
 * row may legitimately name any field an application ever declared. The prose fields are the ones
 * written for a human, and they are the ones that must read as a statement of the rule the row
 * pins.
 *
 * <p><b>@edgeCase</b> The hex rule is deliberately bounded at seven characters, the shortest
 * abbreviated SHA git resolves. Shorter runs — {@code cafe}, {@code dead}, a four-digit year — are
 * ordinary English and ordinary data.
 */
class CorpusProseIsPublishableTest {

  /** An abbreviated or full commit SHA: what a public reader cannot resolve. */
  private static final Pattern COMMIT_SHA = Pattern.compile("\\b[0-9a-f]{7,40}\\b");

  /** Vocabulary of the private process, never of the rule a row pins. */
  private static final Pattern PRIVATE_PROCESS =
      Pattern.compile("pair\\s*#|\\bagent\\b|\\bcoordinator\\b", Pattern.CASE_INSENSITIVE);

  @Test
  void noGraphRowsProseCarriesACommitShaOrTheVocabularyOfThePrivateProcess() {
    for (var graphCase : HostileCorpus.graphs()) {
      assertProseIsPublishable(
          graphCase.id(),
          Arrays.asList(
              graphCase.id(), graphCase.kind(), graphCase.description(), graphCase.member()));
    }
  }

  @Test
  void noRedactionRowsProseCarriesACommitShaOrTheVocabularyOfThePrivateProcess() {
    for (var redactionCase : HostileCorpus.redactions()) {
      assertProseIsPublishable(
          redactionCase.id(),
          Arrays.asList(redactionCase.id(), redactionCase.kind(), redactionCase.description()));
    }
  }

  private static void assertProseIsPublishable(String id, List<String> fields) {
    var offending = new ArrayList<String>();
    for (var field : fields) {
      if (field == null) {
        continue;
      }
      collectMatches(COMMIT_SHA, field, offending);
      collectMatches(PRIVATE_PROCESS, field, offending);
    }
    assertThat(offending)
        .as("%s: corpus prose ships publicly and must name the rule, not the private work", id)
        .isEmpty();
  }

  private static void collectMatches(Pattern pattern, String field, List<String> offending) {
    var matcher = pattern.matcher(field);
    while (matcher.find()) {
      offending.add(matcher.group());
    }
  }
}
