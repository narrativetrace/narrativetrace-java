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
 * The captured trace as a read-only tree.
 *
 * <p>INTENT: {@link ai.narrativetrace.api.tree.TraceTree} is what every renderer, exporter and
 * analysis consumes, so it belongs to the contract. How a tree is assembled from the event trail is
 * runtime behaviour and stays in the runtime module.
 */
package ai.narrativetrace.api.tree;
