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
package ai.narrativetrace.api.event;

/**
 * Service metadata stamped onto newly created spans.
 *
 * <p>INTENT: Supply this when traces leave the local process and need enough identity to be
 * correlated across services or environments.
 *
 * @param serviceName Stable logical name such as {@code order-service}.
 * @param serviceVersion Deployment or artifact version such as {@code 1.2.3}.
 * @param environment Environment label such as {@code production}, {@code staging}, or {@code
 *     test}.
 */
public record ServiceIdentity(String serviceName, String serviceVersion, String environment) {}
