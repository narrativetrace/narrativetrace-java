/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.MarketplaceListing;
import java.util.List;

/**
 * The one JSON string encoder this module's renderers share: an RFC 8259 quoted string with the two
 * mandatory escapes and every control character as {@code \\uXXXX}.
 *
 * <p>Written by hand because this module takes zero production dependencies, the same contract
 * {@code narrativetrace-api} and {@code narrativetrace-tooling} hold. Shared rather than copied per
 * renderer: two JSON render targets that escape differently would ship one valid file and one that
 * a vendor's parser rejects, from the same catalogue.
 *
 * @llmNote Package-private on purpose — this is an implementation detail of the renderers, not a
 *     JSON library. A third render target reuses it; nothing outside this package should.
 */
final class JsonText {

  private JsonText() {}

  /** {@code text} as a quoted JSON string, escapes included. */
  static String quote(String text) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < text.length(); i++) {
      appendEscaped(out, text.charAt(i));
    }
    return out.append('"').toString();
  }

  /** {@code values} as a one-line JSON array of quoted strings — short, flat lists read better. */
  static String array(List<String> values) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      out.append(i == 0 ? "" : ", ").append(quote(values.get(i)));
    }
    return out.append(']').toString();
  }

  /**
   * The three leading members every plugin manifest of this module opens with, at the top level of
   * an object indented two spaces: {@code name}, {@code description} and {@code author}, each
   * ending in a comma. One writer, so the Claude and Cursor manifests cannot tell a listing
   * differently.
   */
  static void appendManifestIdentity(StringBuilder out, MarketplaceListing listing) {
    out.append("  \"name\": ").append(quote(listing.name())).append(",\n");
    out.append("  \"description\": ").append(quote(listing.pluginDescription())).append(",\n");
    out.append("  \"author\": {\n");
    out.append("    \"name\": ").append(quote(listing.owner().name())).append('\n');
    out.append("  },\n");
  }

  private static void appendEscaped(StringBuilder out, char c) {
    switch (c) {
      case '"' -> out.append("\\\"");
      case '\\' -> out.append("\\\\");
      case '\n' -> out.append("\\n");
      case '\r' -> out.append("\\r");
      case '\t' -> out.append("\\t");
      default -> appendPlainOrUnicode(out, c);
    }
  }

  private static void appendPlainOrUnicode(StringBuilder out, char c) {
    if (c < 0x20) {
      out.append(String.format("\\u%04x", (int) c));
    } else {
      out.append(c);
    }
  }
}
