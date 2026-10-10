/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.frameworks;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One thing in a project's source or configuration text that proves a row's wiring was applied.
 *
 * <p>Two shapes: a pattern over the text ({@link Matching}), and a key nested under a top-level
 * YAML key ({@link YamlKey}). The second is a line scan rather than a pattern on purpose — a
 * pattern that walks "the indented lines under a key" needs nested quantifiers, the shape a ReDoS
 * gate rightly refuses on text a project supplies.
 */
public sealed interface Evidence {

  /** Whether the text shows the wiring. */
  boolean foundIn(String text);

  /** A pattern over the text; any match is evidence. */
  static Evidence matching(String regex) {
    return new Matching(Pattern.compile(regex));
  }

  /** Evidence that a pattern finds anywhere in the text. */
  record Matching(Pattern pattern) implements Evidence {
    @Override
    public boolean foundIn(String text) {
      return pattern.matcher(text).find();
    }
  }

  /**
   * {@code key} set to a non-empty value directly under the top-level YAML key {@code parent}: an
   * inline value other than {@code []}, {@code ""} or {@code ''}, or a first list item on the next
   * content line. Comment lines and trailing comments are YAML's, not keys; a {@code key} nested
   * one level further down (under another child of {@code parent}) is somebody else's setting.
   */
  record YamlKey(String parent, String key) implements Evidence {
    @Override
    public boolean foundIn(String text) {
      List<String> lines = text.lines().filter(YamlKey::isContent).toList();
      boolean underParent = false;
      int childIndent = -1;
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        int indent = indentOf(line);
        String content = withoutComment(line.strip());
        if (indent == 0) {
          underParent = content.equals(parent + ":");
          childIndent = -1;
        } else if (underParent && (childIndent < 0 || indent == childIndent)) {
          childIndent = indent;
          if (content.startsWith(key + ":")) {
            return hasValue(content.substring(key.length() + 1).strip(), lines, i + 1);
          }
        }
      }
      return false;
    }

    private static boolean isContent(String line) {
      return !line.isBlank() && !line.strip().startsWith("#");
    }

    private static int indentOf(String line) {
      int i = 0;
      while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
        i++;
      }
      return i;
    }

    /** A YAML comment starts at a {@code #} preceded by whitespace. */
    private static String withoutComment(String content) {
      int hash = content.indexOf(" #");
      int tab = content.indexOf("\t#");
      int at = hash < 0 ? tab : tab < 0 ? hash : Math.min(hash, tab);
      return at < 0 ? content : content.substring(0, at).strip();
    }

    private static boolean hasValue(String inline, List<String> lines, int next) {
      if (!inline.isEmpty()) {
        String compact = inline.replace(" ", "");
        return !compact.equals("[]") && !compact.equals("\"\"") && !compact.equals("''");
      }
      return next < lines.size()
          && lines.get(next).strip().startsWith("- ")
          && !withoutComment(lines.get(next).strip()).substring(1).isBlank();
    }
  }
}
