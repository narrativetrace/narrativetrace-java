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
 * Identity of the thread that executed a traced method entry.
 *
 * <p>INTENT: Thread identity is free at the capture site and unrecoverable downstream, so every
 * {@link TraceEvent.EnterEvent} carries it — not only concurrency events (whose {@link
 * ConcurrencyInfo} additionally relates the work to its group). Never rendered into narrative
 * message text; it surfaces as canonical schema fields and (virtual flag only) MDC.
 *
 * @param threadName Human-readable thread name at entry.
 * @param threadId Numeric thread id at entry.
 * @param virtual Whether the method ran on a virtual thread.
 */
public record ThreadInfo(String threadName, long threadId, boolean virtual) {}
