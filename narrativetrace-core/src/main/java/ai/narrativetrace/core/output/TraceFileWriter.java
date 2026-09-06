/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal file writer for trace artifacts.
 *
 * <p>INTENT: Centralize directory creation and string writing so higher-level test helpers stay
 * focused on rendering decisions.
 *
 * <p><b>@edgeCase</b> Encoding never fails the caller. {@code Files.writeString} rejects a string
 * holding an unpaired surrogate with {@link CharacterCodingException}, so a value the application
 * merely returned — or an exception message it merely threw — could fail the run that traced it.
 * {@link ai.narrativetrace.core.render.ControlEscape} escapes those at capture, and this is the
 * last line behind it: anything still unencodable is written as the replacement character rather
 * than raised. An artifact with one U+FFFD in it is a readable artifact; a failed write is a failed
 * build.
 */
public final class TraceFileWriter {

  public void write(String content, Path file) throws IOException {
    var parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(file, encode(content));
  }

  /** UTF-8 bytes, substituting rather than refusing what the charset cannot represent. */
  private static byte[] encode(String content) throws IOException {
    var encoder =
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    var buffer = encoder.encode(java.nio.CharBuffer.wrap(content));
    var bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}
