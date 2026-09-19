/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

/**
 * A Kotlin data class as the scanner sees it on the classpath, written in Java.
 *
 * <p>A data class synthesizes {@code copy} and one {@code componentN} per property, and compiles
 * every property to a {@code getX} accessor. None of them is JVM-synthetic, so only a rule that
 * names their shape keeps them out of the vocabulary.
 */
public class DataClassFixture {

  private final String author;

  public DataClassFixture(String author) {
    this.author = author;
  }

  public String getAuthor() {
    return author;
  }

  public String component1() {
    return author;
  }

  public DataClassFixture copy(String author) {
    return new DataClassFixture(author);
  }

  /** The near miss: a {@code copy} that copies nothing of its own is still ordinary vocabulary. */
  public void copyLedger() {
    // a genuine verb phrase whose first token happens to be "copy"
  }
}
