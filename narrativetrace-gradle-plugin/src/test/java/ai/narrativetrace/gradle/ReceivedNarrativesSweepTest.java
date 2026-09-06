/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code approveNarratives} sweep: every reviewed {@code *.received.nt} becomes the committed
 * {@code *.approved.nt} baseline. The file-name pair is the contract with the test-time verifier
 * ({@code NarrativeApproval} in core) — the plugin deliberately has no dependency on core, exactly
 * like the clarity JSON boundary.
 */
class ReceivedNarrativesSweepTest {

  @Test
  void promotesEveryReceivedFileAndReplacesExistingBaselines(@TempDir Path dir) throws Exception {
    var orderDir = Files.createDirectories(dir.resolve("OrderTest"));
    Files.writeString(orderDir.resolve("trip_settles.approved.nt"), "old baseline\n");
    Files.writeString(orderDir.resolve("trip_settles.received.nt"), "new structure\n");
    var billingDir = Files.createDirectories(dir.resolve("BillingTest"));
    Files.writeString(billingDir.resolve("invoice_paid.received.nt"), "first structure\n");

    var promoted = ReceivedNarrativesSweep.promote(dir);

    assertThat(promoted)
        .containsExactlyInAnyOrder(
            orderDir.resolve("trip_settles.approved.nt"),
            billingDir.resolve("invoice_paid.approved.nt"));
    assertThat(Files.readString(orderDir.resolve("trip_settles.approved.nt")))
        .isEqualTo("new structure\n");
    assertThat(orderDir.resolve("trip_settles.received.nt")).doesNotExist();
    assertThat(billingDir.resolve("invoice_paid.received.nt")).doesNotExist();
  }

  @Test
  void promotesNothingWhenTheDirectoryDoesNotExist(@TempDir Path dir) throws Exception {
    assertThat(ReceivedNarrativesSweep.promote(dir.resolve("missing"))).isEmpty();
  }
}
