/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.agentfixture;

import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.concurrency.support.Observed;
import ai.narrativetrace.concurrency.support.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The forked JVM's exporter: one file per exported request, named for its phase and request id. */
final class FileExporter implements TraceExporter {

  private final Path outDir;
  private volatile String phase = "none";

  FileExporter(Path outDir) {
    this.outDir = outDir;
  }

  void phase(String name) {
    phase = name;
  }

  @Override
  public void export(TraceTree tree, RequestContext requestContext) {
    var exported = RecordingExporter.describe(tree);
    try {
      Files.writeString(
          outDir.resolve(phase + "-" + exported.requestId() + ".txt"), Observed.write(exported));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
