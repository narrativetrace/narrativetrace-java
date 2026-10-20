/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Builds a minimal, valid, flat-colour PNG byte array for tests — no imaging library needed. */
final class PngFixtures {

  private PngFixtures() {}

  static byte[] flat(int width, int height, byte shade) {
    try {
      var out = new ByteArrayOutputStream();
      out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
      writeChunk(out, "IHDR", ihdr(width, height));
      writeChunk(out, "IDAT", deflatedRows(width, height, shade));
      writeChunk(out, "IEND", new byte[0]);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] ihdr(int width, int height) {
    byte[] data = new byte[13];
    writeInt(data, 0, width);
    writeInt(data, 4, height);
    data[8] = 8; // bit depth
    data[9] = 2; // colour type: RGB
    return data;
  }

  private static byte[] deflatedRows(int width, int height, byte shade) throws IOException {
    byte[] row = new byte[1 + width * 3];
    for (int i = 1; i < row.length; i += 3) {
      row[i] = shade;
      row[i + 1] = shade;
      row[i + 2] = shade;
    }
    var raw = new ByteArrayOutputStream();
    for (int y = 0; y < height; y++) {
      raw.write(row);
    }
    var deflated = new ByteArrayOutputStream();
    try (var zip = new DeflaterOutputStream(deflated)) {
      zip.write(raw.toByteArray());
    }
    return deflated.toByteArray();
  }

  private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
      throws IOException {
    byte[] length = new byte[4];
    writeInt(length, 0, data.length);
    out.write(length);
    byte[] typeAndData = new byte[4 + data.length];
    System.arraycopy(type.getBytes(StandardCharsets.US_ASCII), 0, typeAndData, 0, 4);
    System.arraycopy(data, 0, typeAndData, 4, data.length);
    out.write(typeAndData);
    CRC32 crc = new CRC32();
    crc.update(typeAndData);
    byte[] crcBytes = new byte[4];
    writeInt(crcBytes, 0, (int) crc.getValue());
    out.write(crcBytes);
  }

  private static void writeInt(byte[] target, int offset, int value) {
    target[offset] = (byte) (value >>> 24);
    target[offset + 1] = (byte) (value >>> 16);
    target[offset + 2] = (byte) (value >>> 8);
    target[offset + 3] = (byte) value;
  }
}
