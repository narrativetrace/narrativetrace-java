/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

/**
 * Reads a PNG's width and height straight out of its {@code IHDR} chunk — no imaging library, zero
 * dependencies, the same contract this module holds everywhere else. {@code checkOpenAiPluginZip}
 * uses this to verify the two listing icons are square without decoding a single pixel.
 */
final class PngDimensions {

  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
  private static final int IHDR_WIDTH_OFFSET = 16;
  private static final int IHDR_HEIGHT_OFFSET = 20;

  private PngDimensions() {}

  static int width(byte[] png) {
    checkSignature(png);
    return readInt(png, IHDR_WIDTH_OFFSET);
  }

  static int height(byte[] png) {
    checkSignature(png);
    return readInt(png, IHDR_HEIGHT_OFFSET);
  }

  private static void checkSignature(byte[] png) {
    if (png.length < IHDR_HEIGHT_OFFSET + 4) {
      throw new IllegalArgumentException("not a PNG: too short to hold a signature and IHDR");
    }
    for (int i = 0; i < SIGNATURE.length; i++) {
      if (png[i] != SIGNATURE[i]) {
        throw new IllegalArgumentException("not a PNG: bad signature");
      }
    }
  }

  private static int readInt(byte[] png, int offset) {
    return ((png[offset] & 0xff) << 24)
        | ((png[offset + 1] & 0xff) << 16)
        | ((png[offset + 2] & 0xff) << 8)
        | (png[offset + 3] & 0xff);
  }
}
