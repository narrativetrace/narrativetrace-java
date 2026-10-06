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
package ai.narrativetrace.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * The launcher half of the same invariant {@code narrativetrace-tooling}'s own {@code
 * PrivateConstructorsTest} holds over the doctor library: {@link Cli} and {@link Main} are utility
 * classes that hide their constructors, and reflection is what exercises the hidden constructor
 * rather than a jacoco exclusion papering over it.
 */
class PrivateConstructorsTest {

  private static final Class<?>[] UTILITY_CLASSES = {Cli.class, Main.class};

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
