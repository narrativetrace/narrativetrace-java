/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.MethodSignature;

/**
 * The mark a rendered line carries when its call is an internal (private) one.
 *
 * <p>INTENT: The deep mode records private methods, and a reader must be able to tell them from the
 * class's observable boundary at a glance. The mark is a leading {@code ·} (middle dot) before the
 * call, in text, Markdown and the structural {@code .nt} artifact alike. It is a rendering decision
 * only: the canonical entry and the wire form do not carry it, and a call that is not internal
 * renders exactly as it always did.
 */
final class InternalMarker {

  static final String MARK = "· ";

  private InternalMarker() {}

  /** {@code "· "} for an internal call, the empty string otherwise. */
  static String of(MethodSignature signature) {
    return signature.internal() ? MARK : "";
  }
}
