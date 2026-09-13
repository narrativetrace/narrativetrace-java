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
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.ProListing;
import ai.narrativetrace.skills.Skill;
import java.util.List;

/**
 * Renders the {@code AGENTS.md} managed section (Discovery Channel 3): a compressed catalogue index
 * plus an {@code llms.txt} pointer, spliced between {@link #BEGIN_MARKER} and {@link #END_MARKER}.
 * Passive context that removes the "should I look this up?" decision agents demonstrably fail at —
 * installed once, read by every agent session after.
 */
public final class AgentsMdRenderer {

  public static final String BEGIN_MARKER = "<!-- narrativetrace:skills:start -->";
  public static final String END_MARKER = "<!-- narrativetrace:skills:end -->";

  private AgentsMdRenderer() {}

  public static String renderSection(List<Skill> skills, List<ProListing> proListings) {
    StringBuilder out = new StringBuilder();
    out.append(BEGIN_MARKER).append('\n');
    out.append("## NarrativeTrace agent skills\n\n");
    for (Skill skill : skills) {
      out.append("- `")
          .append(skill.canonicalName())
          .append("` — ")
          .append(oneLine(skill.description()))
          .append('\n');
    }
    for (ProListing listing : proListings) {
      out.append("- `")
          .append(listing.canonicalName())
          .append("` (Pro, ")
          .append(listing.status().label())
          .append(") — ")
          .append(listing.delivers())
          .append('\n');
    }
    out.append("\nSee documentation/llms.txt for the full doc index.\n");
    out.append(END_MARKER).append('\n');
    return out.toString();
  }

  /** Splices the rendered section into {@code agentsMd}, replacing any prior managed section. */
  public static String splice(String agentsMd, String renderedSection) {
    int begin = agentsMd.indexOf(BEGIN_MARKER);
    int end = agentsMd.indexOf(END_MARKER);
    if (begin < 0 || end < 0) {
      String separator = agentsMd.endsWith("\n") ? "\n" : "\n\n";
      return agentsMd + separator + renderedSection.stripTrailing() + "\n";
    }
    String before = agentsMd.substring(0, begin);
    String after = agentsMd.substring(end + END_MARKER.length());
    String afterTrimmedLeadingNewline = after.startsWith("\n") ? after.substring(1) : after;
    return before + renderedSection.stripTrailing() + "\n" + afterTrimmedLeadingNewline;
  }

  private static String oneLine(String text) {
    return text.replace('\n', ' ').strip();
  }
}
