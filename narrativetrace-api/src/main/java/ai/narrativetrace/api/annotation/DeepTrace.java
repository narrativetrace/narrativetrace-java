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
package ai.narrativetrace.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Scopes a deeper trace to one test (or one test class): a test marked {@code @DeepTrace} has its
 * trace — and, in approval mode, its {@code .approved.nt} baseline — include the private methods of
 * the classes under test, marked with a leading {@code ·}.
 *
 * <p>Off by default, always. The annotation is necessary but not sufficient: the Java agent must
 * also have been started with {@code depth=internal}, because private methods are woven when a
 * class loads. A test that asks for depth without that agent option runs at the default depth and
 * says so once on standard error.
 *
 * <p>Never a production setting: it is read by the test integrations only, and an approval baseline
 * cut under it declares {@code depth: internal}, so a shallow run is never judged against it as if
 * the two were the same thing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DeepTrace {}
