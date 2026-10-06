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

import java.util.Optional;

/**
 * Where a {@link Carrier}'s bytes come from: a jar entry, a file under an exploded directory.
 *
 * <p>INTENT: keeps the carrier's VALIDATION — the part worth testing — in one place, independent of
 * whether the carrier is a jar or a directory. The two readers differ by four lines; the rules
 * about what a carrier must contain do not differ at all.
 *
 * <p><b>@llmNote</b> Paths are carrier-relative and always use {@code /}: they are jar entry names
 * first and filesystem paths second.
 */
@FunctionalInterface
interface CarrierSource {

  /** The text at a carrier-relative path, or empty when the carrier has no such entry. */
  Optional<String> read(String carrierRelativePath);
}
