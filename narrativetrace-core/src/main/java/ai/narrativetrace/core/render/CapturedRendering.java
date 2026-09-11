/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.RenderedValue;

/**
 * Result of {@link ValueRenderer#renderForCapture(Object)}: the flat and structured renderings a
 * capture site needs, paired with whether the value-shape axis withheld the whole value.
 *
 * <p>INTENT: {@link ai.narrativetrace.api.event.ParameterCapture#redacted()} must be {@code true}
 * whenever a parameter's whole value was withheld — by name or annotation (decided by the caller
 * before rendering ever runs) or by the value's own shape (decided here, inside the renderer, at
 * the point that already knows it replaced the value with {@link RedactionPolicy#MARKER}).
 * Reporting the fact from the match site is deliberate: a caller that instead compared {@link
 * #rendered()} back against the marker string would be inferring a decision the renderer already
 * made, one string comparison away from a false match against an unrelated value that merely
 * renders the same text.
 *
 * @param rendered the flat rendered text, exactly as {@link ValueRenderer#render(Object)} answers
 *     for the same value
 * @param structured the structured form, exactly as {@link ValueRenderer#renderStructured(Object)}
 *     answers for the same value
 * @param shapeRedacted {@code true} only when the value passed to {@code renderForCapture} was
 *     itself a {@link String} and {@link RedactionPolicy#shouldRedactValue} matched it. A shape
 *     match on a member nested inside a complex value (a JWT inside a DTO field, a PAN inside a
 *     list) masks that member's text but leaves this {@code false} — the flag is per-parameter and
 *     a nested match withheld one member, not the whole value.
 */
public record CapturedRendering(String rendered, RenderedValue structured, boolean shapeRedacted) {}
