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
package ai.narrativetrace.tooling.init;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a carrier's {@code catalogue.json} into a {@link SkillCatalogue}.
 *
 * <p>INTENT: one place knows the catalogue's shape. Every field is mandatory and every type is
 * checked here, so a malformed carrier is refused at open time with a message naming the entry —
 * never half-installed.
 *
 * <p><b>@llmNote</b> The catalogue carries no version by design; do not add one here. The stamp is
 * the jar's own coordinate, which {@link Carrier} derives separately.
 */
final class CatalogueReader {

  private CatalogueReader() {}

  /**
   * @param json the whole {@code catalogue.json} text
   * @throws IllegalArgumentException when the document is malformed, a field is missing or wrongly
   *     typed, or a skill name repeats
   */
  static SkillCatalogue read(String json) {
    Map<String, Object> root = asObject(JsonReader.parse(json), "catalogue");
    String runtime = string(root, "runtime", "catalogue");
    List<SkillEntry> skills = new ArrayList<>();
    for (Object element : array(root, "skills")) {
      skills.add(entry(asObject(element, "skill")));
    }
    return new SkillCatalogue(runtime, skills);
  }

  private static SkillEntry entry(Map<String, Object> skill) {
    String name = string(skill, "name", "skill");
    return new SkillEntry(
        name,
        string(skill, "description", name),
        string(skill, "agents", name),
        string(skill, "claude", name));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String what) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("catalogue: " + what + " must be a JSON object");
    }
    return (Map<String, Object>) value;
  }

  private static List<Object> array(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("catalogue: \"" + key + "\" must be a JSON array");
    }
    return List.copyOf(list);
  }

  private static String string(Map<String, Object> owner, String key, String what) {
    Object value = owner.get(key);
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException(
          "catalogue: " + what + " has no \"" + key + "\" string field");
    }
    return text;
  }
}
