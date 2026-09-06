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
 * The rendering contract: one interface, turning a captured tree into text.
 *
 * <p>INTENT: A third party writes a renderer against this and nothing else. Every shipped
 * implementation lives in the runtime module, so adding one there can never change what an external
 * renderer had to compile against.
 */
package ai.narrativetrace.api.render;
