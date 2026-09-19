/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import ai.narrativetrace.core.render.ControlEscape;
import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.render.RenderingGuard;
import ai.narrativetrace.core.render.ScalarTrust;
import ai.narrativetrace.core.render.ValueRenderer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cached parser and resolver for annotation templates.
 *
 * <p>INTENT: Proxy and agent code use this to resolve {@code @Narrated} and {@code @OnError}
 * templates against raw arguments before those arguments are rendered.
 *
 * <p><b>@edgeCase</b> Unresolved placeholders are preserved literally instead of throwing, so
 * callers can surface warnings through {@code TemplateWarningCollector}.
 */
public final class TemplateParser {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

  /**
   * Ceiling on retained parsed templates, across both of {@link BoundedTemplateCache}'s
   * generations.
   *
   * <p>512 is far above any plausible annotation population — it is roughly the number of distinct
   * {@code @Narrated}/{@code @OnError} strings in a large application, all of which stay resident —
   * and small enough that a full cache of hostile templates is a bounded, uninteresting amount of
   * memory rather than the whole heap.
   */
  static final int MAX_CACHED_TEMPLATES = 512;

  private static final BoundedTemplateCache CACHE =
      new BoundedTemplateCache(MAX_CACHED_TEMPLATES / 2);

  sealed interface Segment {
    String resolve(Map<String, Object> values);

    record Literal(String text) implements Segment {
      @Override
      public String resolve(Map<String, Object> values) {
        return text;
      }
    }

    /**
     * A placeholder naming a value directly — {@code {password}}, {@code {token}}, {@code {card}}.
     *
     * <p><b>@llmNote</b> The key <em>is</em> the member's name here: the value map a proxy or agent
     * builds is keyed by parameter name, so {@code {password}} names the parameter {@code
     * password}. That makes the deny-list applicable to exactly the same input {@link
     * RedactedPaths} feeds it for a path segment, and it is asked the same way — {@link
     * RedactionPolicy#isRedacted} — so one rule answers both productions of the grammar. Before
     * 2026-09-04 this production asked nothing at all, and {@code @Narrated("login {password}")}
     * printed the password that {@code {user.password}} beside it answered {@code [REDACTED]} for.
     *
     * <p><b>@edgeCase</b> The name is asked only once a value exists. A placeholder naming no
     * parameter stays literal, redacted-sounding or not: nothing can leak through a name that
     * resolves to nothing, and answering {@code [REDACTED]} there would swallow the
     * unresolved-placeholder warning that catches the typo.
     */
    record SimplePlaceholder(String key) implements Segment {
      @Override
      public String resolve(Map<String, Object> values) {
        var value = values.get(key);
        if (value == null) {
          return "{" + key + "}";
        }
        if (RedactionPolicy.DEFAULT.isRedacted(key, false)) {
          return RedactionPolicy.MARKER;
        }
        return renderValue(value);
      }
    }

    record PropertyPlaceholder(String objectKey, String property) implements Segment {
      @Override
      public String resolve(Map<String, Object> values) {
        var target = values.get(objectKey);
        if (RedactedPaths.redacts(target, property)) {
          return RedactionPolicy.MARKER;
        }
        var propertyValue = accessProperty(target, property);
        return propertyValue != null
            ? renderValue(propertyValue)
            : "{" + objectKey + "." + property + "}";
      }
    }
  }

  private TemplateParser() {}

  public static List<String> findUnresolvedInResult(String resolved) {
    if (resolved == null) {
      return List.of();
    }
    var matcher = PLACEHOLDER.matcher(resolved);
    var unresolved = new ArrayList<String>();
    while (matcher.find()) {
      unresolved.add(matcher.group(1));
    }
    return List.copyOf(unresolved);
  }

  /**
   * How many parsed templates are currently retained.
   *
   * <p>Package-private on purpose: this is a regression-test hook, not a diagnostics API. A public
   * cache-statistics surface would tell a caller how full the cache is, which is the one fact an
   * attacker probing for the eviction boundary would want.
   */
  static int cachedTemplateCount() {
    return CACHE.size();
  }

  /** Whether this exact template is currently retained parsed. Package-private: for tests. */
  static boolean isCached(String template) {
    return CACHE.contains(template);
  }

  public static String resolve(String template, Map<String, Object> values) {
    var segments = CACHE.get(template, TemplateParser::parse);
    var sb = new StringBuilder();
    for (var segment : segments) {
      sb.append(segment.resolve(values));
    }
    return sb.toString();
  }

  static List<Segment> parse(String template) {
    var segments = new ArrayList<Segment>();
    var matcher = PLACEHOLDER.matcher(template);
    int lastEnd = 0;
    while (matcher.find()) {
      if (matcher.start() > lastEnd) {
        segments.add(new Segment.Literal(template.substring(lastEnd, matcher.start())));
      }
      segments.add(parsePlaceholder(matcher.group(1)));
      lastEnd = matcher.end();
    }
    if (lastEnd < template.length()) {
      segments.add(new Segment.Literal(template.substring(lastEnd)));
    }
    return List.copyOf(segments);
  }

  private static Segment parsePlaceholder(String key) {
    var dotIndex = key.indexOf('.');
    if (dotIndex >= 0) {
      return new Segment.PropertyPlaceholder(
          key.substring(0, dotIndex), key.substring(dotIndex + 1));
    }
    return new Segment.SimplePlaceholder(key);
  }

  /**
   * The renderer consulted before a non-scalar value is stringified. Stateless and thread-safe, so
   * one instance serves every template resolution.
   */
  private static final ValueRenderer SAFE = new ValueRenderer();

  /**
   * Renders a resolved value for substitution, through the one renderer that knows what is hidden.
   *
   * <p>INTENT: narration must never break the call it narrates, and must never out-narrate the
   * redaction policy. A captured value is arbitrary application data — a lazy proxy over a closed
   * session, a half-built entity, a recursive structure — so every non-scalar goes to {@link
   * ValueRenderer}, which is total, bounded, and the single place redaction is decided.
   *
   * <p><b>@edgeCase</b> There is deliberately no fallback to the value's own {@code toString()}.
   * The fallback that used to live here asked whether {@link ValueRenderer}'s output carried the
   * redaction marker and printed the raw {@code toString()} when it did not — but the marker is
   * equally absent when the renderer never saw the whole value: truncated at the field or
   * collection cap, cut at {@code RenderWalk.MAX_DEPTH}, stopped at a cycle, or degraded to a type
   * marker by the renderer's own last-resort catch. "No marker" meant "nothing is hidden" and "I
   * did not look" alike, and the second reading printed the secret in full. Found by {@code
   * fuzzTemplate} on 2026-09-02, where one {@code {a}} out of fifty-nine identical ones leaked
   * because that single render degraded.
   *
   * <p><b>@llmNote</b> This also ends a divergence rather than creating one: a value passed as a
   * traced argument was already rendered structurally, so a record with a hand-written {@code
   * toString()} narrated two different ways in one trace. {@code @NarrativeSummary} is the
   * supported way for an author to choose the bytes, and {@link ValueRenderer} honours it here
   * exactly as it does everywhere else.
   *
   * <p><b>@llmNote</b> Text is not a fast path. A {@link CharSequence} carries the one shape the
   * value axis exists for — a bearer token, a card number, a {@code Set-Cookie} string arriving
   * under a name nothing suspects — so it goes to {@link ValueRenderer#renderNarrationText}, which
   * applies exactly what the renderer applies to a captured {@code String} minus the quotation
   * marks. It used to take the scalar shortcut below, which meant a JWT rendered {@code [REDACTED]}
   * as an argument and in full through {@code @Narrated("issued {token}")}.
   */
  private static String renderValue(Object value) {
    if (value instanceof CharSequence text) {
      return SAFE.renderNarrationText(text);
    }
    return isScalar(value) ? scalarText(value) : SAFE.render(value);
  }

  /**
   * A scalar's own text, sanitized when its {@code toString()} is application code rather than a
   * JDK-fixed format, or its type marker when {@code toString()} throws or answers {@code null}.
   *
   * <p><b>@edgeCase</b> Catches {@link Throwable}, not {@link Exception}: a deep recursive {@code
   * toString()} raises {@link StackOverflowError}, and an observability failure may never become an
   * application failure.
   *
   * <p><b>@llmNote</b> An {@link Enum} constant with an overridden {@code toString()} is
   * application code, exactly like a {@code String} — but unlike {@link
   * ValueRenderer#render(Object)}, this fast path never truncates, so only {@link
   * ControlEscape#sanitize} applies, not length capping. A constant holds no member a walk could
   * reach, which is why sanitizing is enough for it and was never enough for a {@link Number}
   * subclass: that one is a composite, it never arrives here at all, and {@link #isScalar} is where
   * it is turned away.
   */
  private static String scalarText(Object value) {
    var rendered = rawScalarText(value);
    return needsSanitizing(value) ? ControlEscape.sanitize(rendered) : rendered;
  }

  private static String rawScalarText(Object value) {
    try {
      var rendered = value.toString();
      return rendered != null ? rendered : typeMarker(value);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - rogue toString() may throw Error
      return typeMarker(value);
    }
  }

  /**
   * Whether {@code value}'s scalar text came from application code rather than a platform-fixed
   * format — an enum constant's, now that a {@link Number} subclass is no longer a scalar here.
   */
  private static boolean needsSanitizing(Object value) {
    return value instanceof Enum<?>;
  }

  /**
   * Values that are their own best narration and cannot hide a member: the platform's own numeric
   * leaves, booleans, characters and enum constants. Skipping the renderer for these keeps the
   * common placeholder — {@code {orderId}}, {@code {quantity}} — as cheap as it was.
   *
   * <p><b>@edgeCase</b> {@link CharSequence} was on this list until 2026-09-04 and is deliberately
   * not any more: text is the one scalar whose <em>content</em> can be a credential, so it is
   * answered by {@link ValueRenderer#renderNarrationText} one method up rather than by its own
   * {@code toString()}.
   *
   * <p><b>@edgeCase</b> A {@link Number} is a scalar here only when {@link ScalarTrust} says its
   * own text may be read. A subclass of {@code Number} can hold anything, including a deny-listed
   * field its {@code toString()} prints, and a narration template is rendering like any other — so
   * it goes to the renderer and is walked, exactly as it is when the same value is captured as an
   * argument.
   */
  private static boolean isScalar(Object value) {
    return value instanceof Number number && ScalarTrust.isTrustedNumeric(number)
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>;
  }

  private static String typeMarker(Object value) {
    return "<" + value.getClass().getSimpleName() + ">";
  }

  /**
   * Resolves a property by reading state once — a record's or class's own backing field when one
   * exists — falling back to its accessor method only for a genuinely computed property with no
   * backing field (there is no state to read; the accessor is the only answer there is).
   *
   * <p><b>@llmNote</b> Rendering reads state and never runs a value's own code; a record accessor
   * is code the record's author can override, exactly as {@link ValueRenderer}'s own record
   * component read no longer trusts it. The whole reflective read — field or, failing that,
   * accessor — runs under {@link RenderingGuard}, same as every other reflective read rendering
   * performs, so a woven accessor invoked here opens no spurious span.
   */
  private static Object accessProperty(Object object, String property) {
    if (object == null) {
      return null;
    }
    RenderingGuard.enter();
    try {
      var field = findBackingField(object.getClass(), property);
      if (field != null) {
        field.setAccessible(true); // NOPMD
        return field.get(object);
      }
      // No backing field: a genuinely computed property, answered only by its accessor
      // (direct method first, records and fluent APIs; then the JavaBean getter, getXxx).
      var method = findAccessor(object.getClass(), property);
      if (method == null) {
        return unresolvedProperty();
      }
      // setAccessible needed: public methods on package-private classes are
      // inaccessible via invoke from a different package without it.
      method.setAccessible(true); // NOPMD
      return method.invoke(object);
    } catch (Throwable e) { // NOPMD AvoidCatchingThrowable - a rogue getter may throw Error
      // Property access can fail for many reasons: no such member, module
      // encapsulation (InaccessibleObjectException), SecurityException from
      // setAccessible in restricted environments, or the target method itself
      // throwing (wrapped as InvocationTargetException). In all cases, the
      // template should gracefully preserve the {placeholder} text rather
      // than crash trace rendering.
      return unresolvedProperty();
    } finally {
      RenderingGuard.leave();
    }
  }

  /**
   * The declared field named {@code name} on {@code owner} or an ancestor, or {@code null} when
   * none exists — the same field-finding walk {@link RedactedPaths#redacts} uses to decide whether
   * a path is hidden, so a property that is redacted and a property that is read agree on which
   * member answers a name.
   */
  private static Field findBackingField(Class<?> owner, String name) {
    for (var type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
      for (var field : type.getDeclaredFields()) {
        if (field.getName().equals(name)) {
          return field;
        }
      }
    }
    return null;
  }

  /**
   * The accessor convention templates use: {@code property()} first (records, fluent APIs), then
   * the JavaBean {@code getProperty()}. Package-private because {@link RedactedPaths} must resolve
   * a path segment through exactly the same convention the resolver does.
   *
   * <p><b>@edgeCase</b> An empty segment names nothing, and says so. {@code {card.}} and {@code
   * {card..cvv}} are authoring typos the placeholder grammar happily produces, and the JavaBean
   * branch used to build its getter name with {@code property.charAt(0)} — a {@code
   * StringIndexOutOfBoundsException} raised inside instrumentation. The resolver caught it, but the
   * redaction walk calls this method directly, so it escaped into the traced call.
   */
  static java.lang.reflect.Method findAccessor(Class<?> clazz, String property) {
    if (property == null || property.isEmpty()) {
      return null;
    }
    try {
      return clazz.getMethod(property);
    } catch (NoSuchMethodException ignored) {
      // fall through to JavaBean getter convention
    }
    var getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    try {
      return clazz.getMethod(getter);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static Object unresolvedProperty() {
    return null;
  }
}
