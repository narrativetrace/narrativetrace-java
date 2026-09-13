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
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.skills.catalogue.AddNarrativeTracingSkill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.DoctorCommands;
import ai.narrativetrace.skills.catalogue.NarrativeTraceDoctorSkill;
import ai.narrativetrace.skills.catalogue.ProListings;
import ai.narrativetrace.skills.evals.CaseFixture;
import ai.narrativetrace.skills.evals.EvalRunner;
import ai.narrativetrace.skills.evals.PromotionRenderer;
import ai.narrativetrace.skills.evals.QuotaMarkdown;
import ai.narrativetrace.skills.evals.TierPrecondition;
import ai.narrativetrace.skills.lint.Lints;
import ai.narrativetrace.skills.render.AgentsMdRenderer;
import ai.narrativetrace.skills.render.ClaudeSkillRenderer;
import ai.narrativetrace.skills.render.RenderPaths;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * Every utility/registry class in this module hides its constructor to signal "never instantiate
 * me" — a real invariant, but one that leaves the constructor itself unexercised unless a test says
 * so explicitly. Reflection closes that last, honest gap in coverage rather than papering over it
 * with a jacoco exclusion.
 */
class PrivateConstructorsTest {

  private static final Class<?>[] UTILITY_CLASSES = {
    CommandVocabulary.class,
    DoctorCommands.class,
    NarrativeTraceDoctorSkill.class,
    AddNarrativeTracingSkill.class,
    CatalogueIndex.class,
    ProListings.class,
    ClaudeSkillRenderer.class,
    AgentsMdRenderer.class,
    RenderPaths.class,
    Lints.class,
    CaseFixture.class,
    QuotaMarkdown.class,
    TierPrecondition.class,
    PromotionRenderer.class,
    EvalRunner.class,
  };

  @Test
  void everyUtilityClassHidesAPrivateNoArgConstructor() throws Exception {
    for (Class<?> type : UTILITY_CLASSES) {
      Constructor<?> ctor = type.getDeclaredConstructor();
      assertThat(Modifier.isPrivate(ctor.getModifiers()))
          .as(type + " constructor should be private")
          .isTrue();
      ctor.setAccessible(true);
      assertThatCode(ctor::newInstance).doesNotThrowAnyException();
      assertThat(Modifier.isFinal(type.getModifiers())).as(type + " should be final").isTrue();
    }
  }
}
