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
/**
 * Service-provider interfaces third parties implement to extend NarrativeTrace.
 *
 * <p>INTENT: The extension surface, separated from the mechanism that discovers it. Implementations
 * are found by {@code ServiceLoader}; the registry that does the finding stays in the runtime
 * module, because how extensions are located is a runtime decision, not a contract.
 */
package ai.narrativetrace.api.spi;
