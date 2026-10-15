/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The writer behind {@code ./gradlew :narrativetrace-skills-catalogue:renderPromotion}: regenerates
 * {@code ledger/promotion.md} from {@code ledger/runs.jsonl} and the typed catalogue — the same
 * rendering {@code PromotionDriftTest} compares against, so a session that landed a ledger row has
 * one command to run instead of a one-off program.
 *
 * <p>A test-source main on purpose: the renderer is production code, but regenerating a committed
 * file is a build-time act, like {@code RenderPlatformsMain}.
 */
public final class RenderPromotionMain {

  private RenderPromotionMain() {}

  public static void main(String[] args) throws IOException {
    Path ledger = Path.of(args[0]).resolve("narrativetrace-skills-catalogue/ledger");
    String rendered =
        PromotionRenderer.render(
            CatalogueIndex.ALL,
            RunLedgerRow.parseJsonl(Files.readString(ledger.resolve("runs.jsonl"))));
    Path promotion = ledger.resolve("promotion.md");
    Files.writeString(promotion, rendered);
    System.out.println("renderPromotion: rewrote " + promotion);
  }
}
