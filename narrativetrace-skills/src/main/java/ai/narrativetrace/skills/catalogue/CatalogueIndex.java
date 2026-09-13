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
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.Skill;
import java.util.List;

/** The assembled free catalogue — every {@link Skill} this repo ships, in rendering order. */
public final class CatalogueIndex {

  public static final List<Skill> ALL =
      List.of(NarrativeTraceDoctorSkill.build(), AddNarrativeTracingSkill.build());

  private CatalogueIndex() {}
}
