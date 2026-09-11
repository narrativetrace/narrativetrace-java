/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.render.ValueRenderer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that converts reflective method parameters into eager captures.
 *
 * <p>INTENT: Proxy code uses this to keep parameter-name lookup and redaction handling out of the
 * invocation path logic.
 *
 * <p><b>@llmNote</b> Name/annotation-redacted parameters are stored as {@code [REDACTED]} and
 * marked redacted=true without ever reaching {@link ValueRenderer}. Every other parameter is
 * rendered through {@link ValueRenderer#renderForCapture(Object)}, whose {@code shapeRedacted}
 * answer becomes {@code redacted} here too — a value whose own shape matched a credential pattern
 * (a bearer token under an innocuous name) is flagged exactly like a name match, per {@link
 * ParameterCapture#redacted()}.
 */
public final class ParameterNameResolver {

  private ParameterNameResolver() {}

  /**
   * Resolves captures with values rendered — the ordinary call.
   *
   * @param paramNames parameter names
   * @param redacted per-parameter redaction flags
   * @param args argument values
   * @param valueRenderer renderer for non-redacted values
   * @return the resolved captures
   */
  public static List<ParameterCapture> resolve(
      String[] paramNames, boolean[] redacted, Object[] args, ValueRenderer valueRenderer) {
    return resolve(paramNames, redacted, args, valueRenderer, true);
  }

  /**
   * Resolves captures, optionally skipping value rendering.
   *
   * <p>When {@code renderValues} is {@code false}, parameter values are not passed through {@link
   * ValueRenderer} and are captured as empty strings — callers use this when the active tracing
   * level would suppress the values anyway, avoiding the reflective render cost.
   *
   * @param paramNames parameter names
   * @param redacted per-parameter redaction flags
   * @param args argument values
   * @param valueRenderer renderer for non-redacted values
   * @param renderValues whether to render values; when {@code false} values are captured empty
   * @return the resolved captures
   */
  public static List<ParameterCapture> resolve(
      String[] paramNames,
      boolean[] redacted,
      Object[] args,
      ValueRenderer valueRenderer,
      boolean renderValues) {
    return resolve(paramNames, null, redacted, args, valueRenderer, renderValues);
  }

  /**
   * Resolves captures with declared parameter types. {@code paramTypes} entries use {@link
   * Class#getTypeName()} form; the array may be {@code null} (no types captured) but never
   * per-element null.
   *
   * @param paramNames parameter names
   * @param paramTypes declared type names, or {@code null} when no types were captured
   * @param redacted per-parameter redaction flags
   * @param args argument values
   * @param valueRenderer renderer for non-redacted values
   * @param renderValues whether to render values; when {@code false} values are captured empty
   * @return the resolved captures
   */
  public static List<ParameterCapture> resolve(
      String[] paramNames,
      String[] paramTypes,
      boolean[] redacted,
      Object[] args,
      ValueRenderer valueRenderer,
      boolean renderValues) {
    var captures = new ArrayList<ParameterCapture>(paramNames.length);
    for (int i = 0; i < paramNames.length; i++) {
      var type = paramTypes != null ? paramTypes[i] : null;
      if (redacted[i]) {
        captures.add(new ParameterCapture(paramNames[i], "[REDACTED]", true, null, type));
      } else if (!renderValues) {
        captures.add(new ParameterCapture(paramNames[i], "", false, null, type));
      } else {
        var capture = valueRenderer.renderForCapture(args[i]);
        captures.add(
            new ParameterCapture(
                paramNames[i],
                capture.rendered(),
                capture.shapeRedacted(),
                capture.structured(),
                type));
      }
    }
    return List.copyOf(captures);
  }

  /**
   * Resolves captures by reading the method's own parameters reflectively — names, declared types
   * and {@link NotTraced} flags all come from the {@link Method}.
   *
   * @param method the method whose parameters describe the capture
   * @param args argument values, positionally matching the method's parameters
   * @param valueRenderer renderer for non-redacted values
   * @return the resolved captures
   */
  public static List<ParameterCapture> resolve(
      Method method, Object[] args, ValueRenderer valueRenderer) {
    var parameters = method.getParameters();
    var captures = new ArrayList<ParameterCapture>(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      boolean isRedacted = parameters[i].isAnnotationPresent(NotTraced.class);
      var type = parameters[i].getType().getTypeName();
      if (isRedacted) {
        captures.add(new ParameterCapture(parameters[i].getName(), "[REDACTED]", true, null, type));
      } else {
        var capture = valueRenderer.renderForCapture(args[i]);
        captures.add(
            new ParameterCapture(
                parameters[i].getName(),
                capture.rendered(),
                capture.shapeRedacted(),
                capture.structured(),
                type));
      }
    }
    return List.copyOf(captures);
  }
}
