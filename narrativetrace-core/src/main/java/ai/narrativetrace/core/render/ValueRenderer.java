/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.RenderedValue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * Eager object-to-string renderer used at capture time.
 *
 * <p>INTENT: Use this before values enter the immutable trace model. It produces stable textual
 * snapshots so later renderers and exporters never need to inspect live objects.
 *
 * <p><b>@preferOver</b> Calling {@code toString()} directly in instrumentation code. This renderer
 * handles truncation, records, collections, futures, cycle detection, and {@link
 * ai.narrativetrace.api.annotation.NarrativeSummary}.
 *
 * <p><b>@edgeCase</b> Pending futures render as {@code <pending>}, cancelled futures as {@code
 * <cancelled>}, failed future dereference as {@code <failed>}, and recursive object graphs collapse
 * to a stable identity token.
 *
 * <p><b>@sideEffects</b> Both entry points are <b>total</b>: no value makes them throw, including
 * one whose {@code toString()}, {@code iterator()}, {@code size()} or {@code isDone()} raises an
 * {@link Error}. Rendering runs on the application thread inside proxy and agent entry and exit, so
 * anything escaping here reaches host code — and the business method's result must always win over
 * a rendering failure. The last-resort answer is the value's type marker; the branches that can
 * fail part-way answer with {@code <error>} in that part alone, so one bad element, entry, field or
 * component never discards the rest of the render.
 *
 * <p><b>@edgeCase</b> Depth is capped as well as cycles. A chain never repeats an object, so the
 * identity guard never sees it; a ten-thousand-node linked list was therefore a {@code
 * StackOverflowError} raised inside instrumentation. The walk stops after {@link
 * RenderWalk#MAX_DEPTH} levels of nested complex value and renders {@code <max-depth>} — the fourth
 * cap beside the string, collection and field limits, and per path, so a shallow sibling after a
 * deep one still renders whole.
 */
public final class ValueRenderer {

  private static final int DEFAULT_MAX_STRING_LENGTH = 200;
  private static final int DEFAULT_MAX_COLLECTION_ITEMS = 5;
  private static final int DEFAULT_MAX_OBJECT_FIELDS = 5;

  /**
   * Rendered form of a wrapper that holds nothing. Marker-shaped like {@code <pending>} rather than
   * the literal {@code null}: an absent {@code Optional} is a decision the code made, and a reader
   * who cannot tell it apart from a null field has lost that.
   */
  private static final String ABSENT = "<empty>";

  /**
   * Rendered form of a value the walk refused to follow because it had gone {@link
   * RenderWalk#MAX_DEPTH} levels down. Marker-shaped like {@code <empty>} and {@code <pending>}: a
   * reader who sees it has learned that the graph continued, which is exactly what a truncated
   * narrative should say.
   */
  private static final String TOO_DEEP = "<max-depth>";

  /**
   * Rendered form of a part of a value the renderer could not read: a collection that will not
   * iterate, a map that will not produce its entries, a field or record component whose accessor
   * threw. Deliberately the same marker the field paths have always used — a reader does not care
   * <em>which</em> member of the JDK contract a value broke, only that this slot is missing.
   */
  private static final String RENDER_FAILED = "<error>";

  /** Rendered form of a future that has not completed. */
  private static final String PENDING = "<pending>";

  /** Rendered form of a future that was cancelled. */
  private static final String CANCELLED = "<cancelled>";

  /** Rendered form of a future that failed, or will not answer whether it completed. */
  private static final String FAILED = "<failed>";

  private final int maxStringLength;
  private final int maxCollectionItems;
  private final int maxObjectFields;
  private final RedactionPolicy redactionPolicy;

  public ValueRenderer() {
    this(DEFAULT_MAX_STRING_LENGTH, DEFAULT_MAX_COLLECTION_ITEMS, DEFAULT_MAX_OBJECT_FIELDS);
  }

  public ValueRenderer(int maxStringLength, int maxCollectionItems, int maxObjectFields) {
    this(maxStringLength, maxCollectionItems, maxObjectFields, RedactionPolicy.DEFAULT);
  }

  /**
   * Creates a renderer with an explicit field-name redaction policy.
   *
   * @param maxStringLength maximum characters retained from a rendered string value
   * @param maxCollectionItems maximum elements rendered from a collection, array, or map
   * @param maxObjectFields maximum fields rendered from an introspected object or record
   * @param redactionPolicy deny-list deciding which field values are hidden by name; use {@link
   *     RedactionPolicy#DISABLED} to opt out
   */
  public ValueRenderer(
      int maxStringLength,
      int maxCollectionItems,
      int maxObjectFields,
      RedactionPolicy redactionPolicy) {
    this.maxStringLength = maxStringLength;
    this.maxCollectionItems = maxCollectionItems;
    this.maxObjectFields = maxObjectFields;
    this.redactionPolicy = redactionPolicy;
  }

  /**
   * Renders a value, whatever the value does.
   *
   * @param value any object, including one whose own methods throw
   * @return its rendered form, or its type marker when nothing about it could be read
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // rendering may never fail the traced method
  public String render(Object value) {
    try {
      var scalar = renderScalar(value);
      return scalar != null ? scalar : renderGuarded(value, new RenderWalk());
    } catch (Throwable t) { // NOPMD
      return typeMarker(value);
    }
  }

  /**
   * Renders text that is substituted into prose rather than shown as a value: the same decision
   * {@link #render(Object)} makes for a {@code String}, without the quotation marks.
   *
   * <p>INTENT: {@code @Narrated("issued {token}")} writes its placeholder into a sentence, where a
   * quoted, escaped value would read as a rendering artifact — but the sentence is an output like
   * any other, so the value in it must obey the same rules. Both axes apply here: the value-shape
   * axis of {@link RedactionPolicy#shouldRedactValue} (a JWT is a JWT wherever it is printed), the
   * control-character escape, and the string cap. Only the quotes are dropped.
   *
   * <p><b>@llmNote</b> The one caller is {@code TemplateParser}, which used to answer a text
   * placeholder with the value's own {@code toString()} — no redaction, no escaping, no cap — so
   * {@code @Narrated("issued {token}")} printed a bearer token that the identical value answered
   * {@code [REDACTED]} for as a captured parameter. Keep this method and {@code renderScalar}'s
   * {@code String} branch reading the same two lines: a secret must not depend on whether the value
   * was narrated or captured.
   *
   * <p><b>@edgeCase</b> Total, like every other entry point. {@code CharSequence} is an interface,
   * so {@code toString()} here is application code that may throw or answer {@code null}; either
   * answers the type marker.
   *
   * @param text any character sequence destined for narration; never {@code null}
   * @return the redacted marker, or the sanitized and capped text, unquoted
   */
  public String renderNarrationText(CharSequence text) {
    var raw = scalarText(text);
    return redactionPolicy.shouldRedactValue(raw) ? RedactionPolicy.MARKER : sanitizeAndCap(raw);
  }

  /**
   * Renders a value for a {@code ParameterCapture}, additionally reporting whether the value-shape
   * axis withheld the whole value — the seam {@code ParameterCapture.redacted()} needs to stay true
   * whenever a parameter caught by {@link RedactionPolicy#shouldRedactValue} is rendered.
   *
   * <p>INTENT: The name/annotation axis is decided by the caller before this method is ever reached
   * (a name-denied parameter is never rendered at all — see {@code ParameterNameResolver} and
   * {@code AgentRuntime.buildCaptures}), so every caller of this method only needs the axis it
   * cannot see from a name: whether the runtime value itself is shaped like a credential. Reporting
   * that fact here, at the point the match actually happens, is what keeps a capture site from
   * having to infer it later by comparing rendered text back against {@link RedactionPolicy#MARKER}
   * — a comparison that cannot tell a real match from an unrelated value whose own rendering
   * happens to coincide.
   *
   * <p><b>@sideEffects</b> None beyond {@link #render(Object)} and {@link
   * #renderStructured(Object)}, which this calls once each; total, like every other entry point.
   *
   * @param value the parameter's raw runtime value, exactly what {@link #render(Object)} would take
   * @return the flat and structured renderings plus whether the top-level value's own shape matched
   */
  public CapturedRendering renderForCapture(Object value) {
    return new CapturedRendering(
        render(value), renderStructured(value), topLevelShapeRedacted(value));
  }

  /**
   * Whether the value handed to {@link #renderForCapture} is, itself, a shape-redacted string.
   *
   * <p><b>@edgeCase</b> Deliberately shallow — {@code instanceof String} without unwrapping an
   * {@code Optional}, following a {@code Future}, or descending into a field. Any of those already
   * renders its shape-matched contents as {@link RedactionPolicy#MARKER} through the ordinary walk;
   * what changes here is only whether that fact is also promoted to the parameter-level flag, and
   * only the direct top-level case is documented to do so. See {@link
   * CapturedRendering#shapeRedacted}.
   */
  @SuppressWarnings(
      "PMD.AvoidCatchingThrowable") // this must stay total like every other entry point
  private boolean topLevelShapeRedacted(Object value) {
    try {
      return value instanceof String s && redactionPolicy.shouldRedactValue(s);
    } catch (Throwable t) { // NOPMD
      return false;
    }
  }

  /**
   * Renders a value preserving its original Java type as a {@link RenderedValue}.
   *
   * <p>Mirrors the decision tree of {@link #render(Object)} but returns structured types instead of
   * flat strings. OTel exporters use this for typed span attributes; existing renderers ignore it.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // rendering may never fail the traced method
  public RenderedValue renderStructured(Object value) {
    try {
      var scalar = renderStructuredScalar(value);
      return scalar != null ? scalar : renderStructuredGuarded(value, new RenderWalk());
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(typeMarker(value));
    }
  }

  private RenderedValue renderStructured(Object value, RenderWalk walk) {
    var scalar = renderStructuredScalar(value);
    return scalar != null ? scalar : renderStructuredGuarded(value, walk);
  }

  /**
   * Structured twin of {@link #renderScalar}: the flat forms, none of which reads the walk, so the
   * public entry point renders them without constructing one.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a misbehaving scalar is still just a scalar
  private RenderedValue renderStructuredScalar(Object value) {
    try {
      return structuredScalar(value);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(typeMarker(value));
    }
  }

  /**
   * The structured scalar forms themselves; {@code null} means "not a scalar".
   *
   * <p><b>@llmNote</b> An instance method, not static, only so it can consult {@link
   * RedactionPolicy#shouldRedactValue}. The flat twin {@link #renderScalar} applies the same test
   * at the same point; a secret must not depend on which of the two paths an exporter chose.
   */
  private RenderedValue structuredScalar(Object value) {
    if (value == null) {
      return new RenderedValue.NullVal();
    }
    if (value instanceof String s) {
      return new RenderedValue.StringVal(
          redactionPolicy.shouldRedactValue(s) ? RedactionPolicy.MARKER : s);
    }
    if (value instanceof Boolean b) {
      return new RenderedValue.BooleanVal(b);
    }
    var temporal = renderStructuredTemporal(value);
    if (temporal != null) {
      return temporal;
    }
    var numeric = renderStructuredNumeric(value);
    if (numeric != null) {
      return numeric;
    }
    if (value instanceof Character) {
      return new RenderedValue.StringVal(scalarText(value));
    }
    if (value instanceof Enum<?>) {
      // Enum.toString() is a per-constant overridable method (a constant body can supply one),
      // not a JDK-fixed format — the same reason a non-JDK Number is not trusted below.
      return new RenderedValue.StringVal(ControlEscape.sanitize(scalarText(value)));
    }
    return null;
  }

  /** Structured twin of {@link #renderGuarded}: one level deeper, or the depth marker. */
  private RenderedValue renderStructuredGuarded(Object value, RenderWalk walk) {
    if (!walk.descend()) {
      return new RenderedValue.StringVal(TOO_DEEP);
    }
    try {
      return renderStructuredComplex(value, walk);
    } finally {
      walk.ascend();
    }
  }

  private static RenderedValue renderStructuredTemporal(Object value) {
    if (value instanceof Instant inst) {
      return new RenderedValue.InstantVal(inst.toEpochMilli());
    }
    if (value instanceof ZonedDateTime zdt) {
      return new RenderedValue.InstantVal(zdt.toInstant().toEpochMilli());
    }
    if (value instanceof OffsetDateTime odt) {
      return new RenderedValue.InstantVal(odt.toInstant().toEpochMilli());
    }
    if (value instanceof LocalDateTime ldt) {
      return new RenderedValue.InstantVal(ldt.toInstant(ZoneOffset.UTC).toEpochMilli());
    }
    if (value instanceof Date date) {
      return new RenderedValue.InstantVal(date.getTime());
    }
    return null;
  }

  private static RenderedValue renderStructuredNumeric(Object value) {
    if (value instanceof Integer
        || value instanceof Long
        || value instanceof Short
        || value instanceof Byte) {
      return new RenderedValue.LongVal(((Number) value).longValue());
    }
    if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
      return new RenderedValue.DoubleVal(((Number) value).doubleValue());
    }
    return null;
  }

  private RenderedValue renderStructuredComplex(Object value, RenderWalk walk) {
    var wrapped = renderStructuredWrapper(value, walk);
    if (wrapped != null) {
      return wrapped;
    }
    if (value instanceof Collection<?> c) {
      return renderStructuredCollection(c, walk);
    }
    if (value.getClass().isArray()) {
      return renderStructuredArray(value, walk);
    }
    if (value instanceof Map<?, ?> m) {
      return renderStructuredMap(m, walk);
    }
    var summaryMethod = findNarrativeSummaryMethod(value.getClass());
    if (summaryMethod != null) {
      try {
        return new RenderedValue.StringVal(String.valueOf(summaryMethod.invoke(value)));
      } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - a summary method may throw Error
        // fall through to ordinary rendering
      }
    }
    if (value.getClass().isRecord()) {
      return renderStructuredRecord(value, walk);
    }
    if (!rendersItsOwnString(value.getClass())) {
      return renderStructuredIntrospected(value, walk);
    }
    return new RenderedValue.StringVal(renderWithToString(value));
  }

  /**
   * Structured twin of {@link #renderWrapper}: the payload's own {@link RenderedValue}, so a
   * wrapped record stays an {@code ObjectVal} with its redacted components intact.
   */
  private RenderedValue renderStructuredWrapper(Object value, RenderWalk walk) {
    if (value instanceof Future<?> future) {
      return renderStructuredFuture(future, walk);
    }
    if (value instanceof Optional<?> optional) {
      return optional.map(held -> renderStructured(held, walk)).orElseGet(ValueRenderer::absent);
    }
    if (value instanceof OptionalInt optional) {
      return optional.isPresent() ? renderStructured(optional.getAsInt(), walk) : absent();
    }
    if (value instanceof OptionalLong optional) {
      return optional.isPresent() ? renderStructured(optional.getAsLong(), walk) : absent();
    }
    if (value instanceof OptionalDouble optional) {
      return optional.isPresent() ? renderStructured(optional.getAsDouble(), walk) : absent();
    }
    if (value instanceof AtomicReference<?> reference) {
      return renderStructuredHeld(reference, walk);
    }
    if (value instanceof AtomicReferenceArray<?> array) {
      return renderStructuredIndexed(array, array.length(), array::get, walk);
    }
    if (value instanceof Map.Entry<?, ?> entry) {
      return renderStructuredEntry(entry, walk);
    }
    return null;
  }

  /**
   * Structured twin of {@link #renderEntry}: a one-field {@code ObjectVal} carrying the same type
   * name a {@link Map} produces, so a standalone entry and the single-entry map holding it are the
   * same structured value.
   */
  private RenderedValue renderStructuredEntry(Map.Entry<?, ?> entry, RenderWalk walk) {
    if (!walk.add(entry)) {
      return new RenderedValue.StringVal(identityMarker(entry));
    }
    try {
      var fields = new LinkedHashMap<String, RenderedValue>();
      putStructuredEntry(fields, entry, walk);
      return new RenderedValue.ObjectVal("Map", Collections.unmodifiableMap(fields));
    } finally {
      walk.remove(entry);
    }
  }

  /** The absent marker as a structured value — a string, exactly as {@code <pending>} is. */
  private static RenderedValue absent() {
    return new RenderedValue.StringVal(ABSENT);
  }

  /**
   * Opens a mutable holder under cycle detection: unlike an {@link Optional}, an {@link
   * AtomicReference} can be made to hold itself, and following that without a guard is a {@code
   * StackOverflowError} inside instrumentation.
   */
  private RenderedValue renderStructuredHeld(AtomicReference<?> reference, RenderWalk walk) {
    if (!walk.add(reference)) {
      return new RenderedValue.StringVal(identityMarker(reference));
    }
    try {
      return renderStructured(reference.get(), walk);
    } finally {
      walk.remove(reference);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a rogue Future may throw Error from get()
  private RenderedValue renderStructuredFuture(Future<?> future, RenderWalk walk) {
    var state = futureState(future);
    if (state != null) {
      return new RenderedValue.StringVal(state);
    }
    try {
      return renderStructured(future.get(), walk);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(FAILED);
    }
  }

  /**
   * A future's completion state as a marker, or {@code null} when it completed and holds a value
   * worth dereferencing.
   *
   * <p><b>@edgeCase</b> {@code isDone()} and {@code isCancelled()} are user code in any custom
   * implementation, and both sat outside the guard that wrapped {@code get()}. A future that will
   * not answer either question is reported as failed — which is what it is.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // isDone()/isCancelled() are user code
  private static String futureState(Future<?> future) {
    try {
      if (!future.isDone()) {
        return PENDING;
      }
      return future.isCancelled() ? CANCELLED : null;
    } catch (Throwable t) { // NOPMD
      return FAILED;
    }
  }

  private RenderedValue renderStructuredCollection(Collection<?> collection, RenderWalk walk) {
    if (!walk.add(collection)) {
      return new RenderedValue.StringVal(identityMarker(collection));
    }
    try {
      return new RenderedValue.ListVal(
          Collections.unmodifiableList(structuredItems(collection, walk)));
    } finally {
      walk.remove(collection);
    }
  }

  /** Structured twin of {@link #collectionItems}: same bounds, same partial-failure behaviour. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // iterator() and next() are user code
  private List<RenderedValue> structuredItems(Collection<?> collection, RenderWalk walk) {
    var elements = new ArrayList<RenderedValue>();
    try {
      for (var item : collection) {
        if (elements.size() >= maxCollectionItems) {
          break;
        }
        elements.add(guardedStructured(item, walk));
      }
    } catch (Throwable t) { // NOPMD
      elements.add(new RenderedValue.StringVal(RENDER_FAILED));
    }
    return elements;
  }

  /** Structured twin of {@link #guardedRender}: one nested value, failing only its own slot. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad element may not discard the rest
  private RenderedValue guardedStructured(Object value, RenderWalk walk) {
    try {
      return renderStructured(value, walk);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(RENDER_FAILED);
    }
  }

  /** Structured twin of {@link #guardedElementAt}. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad element may not discard the rest
  private RenderedValue guardedStructuredElementAt(
      IntFunction<Object> elementAt, int index, RenderWalk walk) {
    try {
      return renderStructured(elementAt.apply(index), walk);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(RENDER_FAILED);
    }
  }

  private RenderedValue renderStructuredArray(Object array, RenderWalk walk) {
    return renderStructuredIndexed(array, Array.getLength(array), i -> Array.get(array, i), walk);
  }

  /**
   * Structured twin of {@link #renderIndexed}, shared by arrays and {@link AtomicReferenceArray}.
   */
  private RenderedValue renderStructuredIndexed(
      Object owner, int length, IntFunction<Object> elementAt, RenderWalk walk) {
    if (!walk.add(owner)) {
      return new RenderedValue.StringVal(identityMarker(owner));
    }
    try {
      var limit = Math.min(length, maxCollectionItems);
      var elements = new ArrayList<RenderedValue>(limit);
      for (var i = 0; i < limit; i++) {
        elements.add(guardedStructuredElementAt(elementAt, i, walk));
      }
      return new RenderedValue.ListVal(Collections.unmodifiableList(elements));
    } finally {
      walk.remove(owner);
    }
  }

  private RenderedValue renderStructuredRecord(Object record, RenderWalk walk) {
    var components = record.getClass().getRecordComponents();
    var limit = Math.min(components.length, maxObjectFields);
    var fields = new LinkedHashMap<String, RenderedValue>();
    for (var i = 0; i < limit; i++) {
      fields.put(components[i].getName(), structuredComponentValue(components[i], record, walk));
    }
    return new RenderedValue.ObjectVal(
        record.getClass().getSimpleName(), Collections.unmodifiableMap(fields));
  }

  /** Structured twin of {@link #componentValue}. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // an accessor may throw Error
  private RenderedValue structuredComponentValue(
      RecordComponent comp, Object record, RenderWalk walk) {
    try {
      if (isRedacted(comp.getName(), comp.getAnnotation(NotTraced.class) != null)) {
        return new RenderedValue.StringVal(RedactionPolicy.MARKER);
      }
      var accessor = comp.getAccessor();
      accessor.setAccessible(true);
      return renderStructured(accessor.invoke(record), walk);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(RENDER_FAILED);
    }
  }

  private RenderedValue renderStructuredMap(Map<?, ?> map, RenderWalk walk) {
    if (!walk.add(map)) {
      return new RenderedValue.StringVal(identityMarker(map));
    }
    try {
      return new RenderedValue.ObjectVal(
          "Map", Collections.unmodifiableMap(structuredEntries(map, walk)));
    } finally {
      walk.remove(map);
    }
  }

  /** Structured twin of {@link #mapEntries}: same bounds, same partial-failure behaviour. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // entrySet() and its iterator are user code
  private Map<String, RenderedValue> structuredEntries(Map<?, ?> map, RenderWalk walk) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    try {
      for (var entry : map.entrySet()) {
        if (fields.size() >= maxCollectionItems) {
          break;
        }
        putGuardedEntry(fields, entry, walk);
      }
    } catch (Throwable t) { // NOPMD
      fields.put(RENDER_FAILED, new RenderedValue.StringVal(RENDER_FAILED));
    }
    return fields;
  }

  /** Structured twin of {@link #guardedMapEntry}. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad entry may not discard the rest
  private void putGuardedEntry(
      Map<String, RenderedValue> fields, Map.Entry<?, ?> entry, RenderWalk walk) {
    try {
      putStructuredEntry(fields, entry, walk);
    } catch (Throwable t) { // NOPMD
      fields.put(RENDER_FAILED, new RenderedValue.StringVal(RENDER_FAILED));
    }
  }

  /** Structured twin of {@link #renderMapEntry}: one guarded key, one guarded or redacted value. */
  private void putStructuredEntry(
      Map<String, RenderedValue> fields, Map.Entry<?, ?> entry, RenderWalk walk) {
    var keyName = renderMapKey(entry.getKey(), walk);
    fields.put(
        keyName,
        redactionPolicy.shouldRedact(keyName)
            ? new RenderedValue.StringVal(RedactionPolicy.MARKER)
            : renderStructured(entry.getValue(), walk));
  }

  private RenderedValue renderStructuredIntrospected(Object value, RenderWalk walk) {
    if (!walk.add(value)) {
      return new RenderedValue.StringVal(
          "<"
              + value.getClass().getSimpleName()
              + "@"
              + Integer.toHexString(System.identityHashCode(value))
              + ">");
    }
    try {
      return renderStructuredObject(value, walk);
    } finally {
      walk.remove(value);
    }
  }

  /**
   * Central redaction decision for a reflectively-introspected member, deferred to {@link
   * RedactionPolicy#isRedacted} so this renderer and template resolution apply one rule rather than
   * two implementations of it.
   */
  private boolean isRedacted(String fieldName, boolean annotated) {
    return redactionPolicy.isRedacted(fieldName, annotated);
  }

  private RenderedValue renderStructuredObject(Object obj, RenderWalk walk) {
    var clazz = obj.getClass();
    var allFields = introspectableFields(clazz);
    var limit = Math.min(allFields.length, maxObjectFields);
    var fields = new LinkedHashMap<String, RenderedValue>();
    for (var i = 0; i < limit; i++) {
      fields.put(allFields[i].getName(), structuredFieldValue(allFields[i], obj, walk));
    }
    return new RenderedValue.ObjectVal(clazz.getSimpleName(), Collections.unmodifiableMap(fields));
  }

  /** Structured twin of {@link #fieldValue}. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // setAccessible/get may throw Error
  private RenderedValue structuredFieldValue(Field field, Object obj, RenderWalk walk) {
    try {
      if (isRedacted(field.getName(), field.isAnnotationPresent(NotTraced.class))) {
        return new RenderedValue.StringVal(RedactionPolicy.MARKER);
      }
      field.setAccessible(true);
      return renderStructured(field.get(obj), walk);
    } catch (Throwable t) { // NOPMD
      return new RenderedValue.StringVal(RENDER_FAILED);
    }
  }

  /** The declared instance fields both object paths print, in declaration order. */
  private static Field[] introspectableFields(Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
        .toArray(Field[]::new);
  }

  private String render(Object value, RenderWalk walk) {
    var scalar = renderScalar(value);
    return scalar != null ? scalar : renderGuarded(value, walk);
  }

  /**
   * The flat forms — everything the renderer can answer for without following a reference out of
   * the value. Returns {@code null} when this value is not one, the same "not mine" idiom {@link
   * #renderWrapper} and {@link #renderStructuredNumeric} use.
   *
   * <p><b>@llmNote</b> Split out of {@link #render(Object, RenderWalk)} so the public entry point
   * can answer a scalar without constructing a {@link RenderWalk} at all: these branches never read
   * the walk, and a traced call renders four values through them.
   */
  private String renderScalar(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String s) {
      if (redactionPolicy.shouldRedactValue(s)) {
        return RedactionPolicy.MARKER;
      }
      return "\"" + sanitizeAndCap(s) + "\"";
    }
    if (value instanceof Boolean || value instanceof Character) {
      return scalarText(value);
    }
    if (value instanceof Number number) {
      var text = scalarText(number);
      return ScalarTrust.isTrustedNumeric(number) ? text : sanitizeAndCap(text);
    }
    if (value instanceof Enum<?>) {
      // Enum.toString() is a per-constant overridable method, not a JDK-fixed format — the same
      // treatment a non-JDK Number gets, one line up.
      return sanitizeAndCap(scalarText(value));
    }
    return null;
  }

  /**
   * A scalar's own text, or its type marker when {@code toString()} throws or answers {@code null}.
   *
   * <p><b>@llmNote</b> The guard is free until it fires — a {@code try} block allocates nothing and
   * costs nothing on the path that does not throw — so the scalar fast paths R1 made
   * allocation-free stay that way. {@code Integer} and friends never reach the catch; a user {@code
   * Number} or {@code Enum} with a hand-written {@code toString()} is the case this exists for.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a rogue toString() may throw Error
  private static String scalarText(Object value) {
    try {
      var text = value.toString();
      return text != null ? text : typeMarker(value);
    } catch (Throwable t) { // NOPMD
      return typeMarker(value);
    }
  }

  /**
   * What the renderer says about a value it could not read at all: the type, in the same {@code
   * <Name>} shape {@link #renderWithToString} uses for a {@code toString()} that threw.
   *
   * <p><b>@edgeCase</b> The simple name is derived from {@link Class#getName()} rather than taken
   * from {@link Class#getSimpleName()}, which is documented to throw {@code InternalError} for a
   * malformed class name. This method runs inside the renderer's last-resort catch; a marker that
   * can itself throw would defeat the guard that called it. Cutting at the last {@code .} or {@code
   * $} reproduces {@code getSimpleName()} for every top-level and nested class, which is what keeps
   * this marker identical to the one {@link #renderWithToString} produces.
   */
  private static String typeMarker(Object value) {
    if (value == null) {
      return "null";
    }
    var name = value.getClass().getName();
    var cut = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
    return "<" + name.substring(cut + 1) + ">";
  }

  /** Follows a reference one level deeper, or renders {@link #TOO_DEEP} instead of descending. */
  private String renderGuarded(Object value, RenderWalk walk) {
    if (!walk.descend()) {
      return TOO_DEEP;
    }
    try {
      return renderComplex(value, walk);
    } finally {
      walk.ascend();
    }
  }

  private String renderComplex(Object value, RenderWalk walk) {
    var wrapped = renderWrapper(value, walk);
    if (wrapped != null) {
      return wrapped;
    }
    if (value instanceof Collection<?> c) {
      return renderCollection(c, walk);
    }
    if (value.getClass().isArray()) {
      return renderArray(value, walk);
    }
    if (value instanceof Map<?, ?> m) {
      return renderMap(m, walk);
    }
    var summaryMethod = findNarrativeSummaryMethod(value.getClass());
    if (summaryMethod != null) {
      try {
        return String.valueOf(summaryMethod.invoke(value));
      } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - a summary method may throw Error
        // fall through to ordinary rendering
      }
    }
    if (value.getClass().isRecord()) {
      return renderRecord(value, walk);
    }
    if (!rendersItsOwnString(value.getClass())) {
      return renderIntrospected(value, walk);
    }
    return renderWithToString(value);
  }

  private String renderIntrospected(Object value, RenderWalk walk) {
    if (!walk.add(value)) {
      return identityMarker(value);
    }
    try {
      return renderObject(value, walk);
    } finally {
      walk.remove(value);
    }
  }

  private String renderWithToString(Object value) {
    try {
      var raw = value.toString();
      if (raw == null) {
        return "<" + value.getClass().getSimpleName() + ">";
      }
      return sanitizeAndCap(raw);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - rogue toString() may throw Error
      return "<" + value.getClass().getSimpleName() + ">";
    }
  }

  /**
   * Renders a single-payload wrapper by rendering what it holds, under exactly the rules that apply
   * to that payload anywhere else. Returns {@code null} when the value is not a wrapper — the same
   * "not mine" idiom {@link #renderStructuredNumeric} uses.
   *
   * <p><b>@llmNote</b> Without this, every one of these types reaches {@link #renderWithToString},
   * and each declares a {@code toString()} that prints its payload's {@code toString()}: {@code
   * Optional.of(card)} logs {@code Optional[Card[number=4111, cvv=123]]} with the
   * {@code @NotTraced} component in full. The wrapper is not the value; opening it is what keeps
   * redaction, truncation, cycle detection and {@code @NarrativeSummary} whole one level down.
   *
   * <p><b>@edgeCase</b> The primitive optionals cannot leak — they hold no reference — but they are
   * opened for the same reason a boxed {@code Optional<Integer>} is: {@code OptionalInt[42]} is the
   * container talking about itself, and {@code 42} is the value the trace is about.
   */
  private String renderWrapper(Object value, RenderWalk walk) {
    if (value instanceof Future<?> future) {
      return renderFuture(future, walk);
    }
    if (value instanceof Optional<?> optional) {
      return optional.map(held -> render(held, walk)).orElse(ABSENT);
    }
    if (value instanceof OptionalInt optional) {
      return optional.isPresent() ? render(optional.getAsInt(), walk) : ABSENT;
    }
    if (value instanceof OptionalLong optional) {
      return optional.isPresent() ? render(optional.getAsLong(), walk) : ABSENT;
    }
    if (value instanceof OptionalDouble optional) {
      return optional.isPresent() ? render(optional.getAsDouble(), walk) : ABSENT;
    }
    if (value instanceof AtomicReference<?> reference) {
      return renderHeld(reference, walk);
    }
    if (value instanceof AtomicReferenceArray<?> array) {
      return renderIndexed(array, array.length(), array::get, walk);
    }
    if (value instanceof Map.Entry<?, ?> entry) {
      return renderEntry(entry, walk);
    }
    return null;
  }

  /**
   * Renders a pair that arrived on its own — a parameter or a return value — in exactly the {@code
   * key=value} shape it would have inside a {@link Map}, so the two cases are indistinguishable.
   *
   * <p><b>@edgeCase</b> An entry is mutable ({@code setValue}), so it can be made to hold itself;
   * the guard is on the entry, because unlike a map's own entries this one is the outermost object.
   */
  private String renderEntry(Map.Entry<?, ?> entry, RenderWalk walk) {
    if (!walk.add(entry)) {
      return identityMarker(entry);
    }
    try {
      return renderMapEntry(entry, walk);
    } finally {
      walk.remove(entry);
    }
  }

  /**
   * Opens a mutable holder under cycle detection: unlike an {@link Optional}, an {@link
   * AtomicReference} can be made to hold itself, and following that without a guard is a {@code
   * StackOverflowError} inside instrumentation.
   */
  private String renderHeld(AtomicReference<?> reference, RenderWalk walk) {
    if (!walk.add(reference)) {
      return identityMarker(reference);
    }
    try {
      return render(reference.get(), walk);
    } finally {
      walk.remove(reference);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a rogue Future may throw Error from get()
  private String renderFuture(Future<?> future, RenderWalk walk) {
    var state = futureState(future);
    if (state != null) {
      return state;
    }
    try {
      return render(future.get(), walk);
    } catch (Throwable t) { // NOPMD
      return FAILED;
    }
  }

  private String renderCollection(Collection<?> collection, RenderWalk walk) {
    if (!walk.add(collection)) {
      return identityMarker(collection);
    }
    try {
      var total = sizeOrUnknown(collection);
      var sb = new StringBuilder("[").append(String.join(", ", collectionItems(collection, walk)));
      if (total > maxCollectionItems) {
        sb.append(", … (").append(total).append(" total)");
      }
      return sb.append("]").toString();
    } finally {
      walk.remove(collection);
    }
  }

  /**
   * Up to {@link #maxCollectionItems} rendered items, keeping whatever the collection managed to
   * yield before it stopped cooperating.
   *
   * <p><b>@llmNote</b> Iterated with a plain loop rather than {@code stream().limit()}: a stream
   * over an arbitrary {@link Collection} goes through {@code spliterator()}, which asks the
   * collection for its {@code size()} — so a collection that iterates but will not size itself used
   * to lose every item it had already produced. The loop asks for nothing but the iterator.
   *
   * <p><b>@edgeCase</b> An iterator that throws part-way appends {@link #RENDER_FAILED} after the
   * items it did yield, so a partly-readable collection renders partly.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // iterator() and next() are user code
  private List<String> collectionItems(Collection<?> collection, RenderWalk walk) {
    var items = new ArrayList<String>();
    try {
      for (var item : collection) {
        if (items.size() >= maxCollectionItems) {
          break;
        }
        items.add(guardedRender(item, walk));
      }
    } catch (Throwable t) { // NOPMD
      items.add(RENDER_FAILED);
    }
    return items;
  }

  /** One nested value, rendered so that its failure costs only its own slot. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad element may not discard the rest
  private String guardedRender(Object value, RenderWalk walk) {
    try {
      return render(value, walk);
    } catch (Throwable t) { // NOPMD
      return RENDER_FAILED;
    }
  }

  /** How many elements the collection claims to hold, or {@code -1} when it will not say. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // size() is user code
  private static int sizeOrUnknown(Collection<?> collection) {
    try {
      return collection.size();
    } catch (Throwable t) { // NOPMD
      return -1;
    }
  }

  /** How many entries the map claims to hold, or {@code -1} when it will not say. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // size() is user code
  private static int sizeOrUnknown(Map<?, ?> map) {
    try {
      return map.size();
    } catch (Throwable t) { // NOPMD
      return -1;
    }
  }

  private String renderArray(Object array, RenderWalk walk) {
    return renderIndexed(array, Array.getLength(array), i -> Array.get(array, i), walk);
  }

  /**
   * Renders any index-addressable holder as a bounded list, each element through {@link
   * #render(Object, RenderWalk)} and the holder itself under the cycle guard.
   *
   * <p><b>@llmNote</b> Shared by plain arrays and {@link AtomicReferenceArray} so the two are
   * indistinguishable in output: an {@code AtomicReferenceArray} is an array, and a reader who can
   * tell which one the code used has learned nothing about the trace.
   */
  private String renderIndexed(
      Object owner, int length, IntFunction<Object> elementAt, RenderWalk walk) {
    if (!walk.add(owner)) {
      return identityMarker(owner);
    }
    try {
      var limit = Math.min(length, maxCollectionItems);
      var sb = new StringBuilder("[");
      for (var i = 0; i < limit; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(guardedElementAt(elementAt, i, walk));
      }
      if (length > maxCollectionItems) {
        sb.append(", ... (").append(length).append(" total)");
      }
      sb.append("]");
      return sb.toString();
    } finally {
      walk.remove(owner);
    }
  }

  /** One indexed element, read and rendered so that its failure costs only its own slot. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad element may not discard the rest
  private String guardedElementAt(IntFunction<Object> elementAt, int index, RenderWalk walk) {
    try {
      return render(elementAt.apply(index), walk);
    } catch (Throwable t) { // NOPMD
      return RENDER_FAILED;
    }
  }

  private static String identityMarker(Object value) {
    return "<"
        + value.getClass().getSimpleName()
        + "@"
        + Integer.toHexString(System.identityHashCode(value))
        + ">";
  }

  private String renderRecord(Object record, RenderWalk walk) {
    var components = record.getClass().getRecordComponents();
    var sb = new StringBuilder(record.getClass().getSimpleName()).append("(");
    var limit = Math.min(components.length, maxObjectFields);
    var fields =
        Arrays.stream(components)
            .limit(limit)
            .map(comp -> comp.getName() + ": " + componentValue(comp, record, walk))
            .collect(Collectors.joining(", "));
    sb.append(fields);
    if (components.length > maxObjectFields) {
      sb.append(", …");
    }
    sb.append(")");
    return sb.toString();
  }

  /**
   * One record component's rendered value, or {@link #RENDER_FAILED} when reading it failed.
   *
   * <p><b>@edgeCase</b> The redaction decision is inside the guard too: reading an annotation can
   * raise {@code TypeNotPresentException} or {@code ArrayStoreException} against a malformed
   * annotation, and the marker is the safe answer to that — it discloses nothing.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // an accessor may throw Error
  private String componentValue(RecordComponent comp, Object record, RenderWalk walk) {
    try {
      if (isRedacted(comp.getName(), comp.getAnnotation(NotTraced.class) != null)) {
        return RedactionPolicy.MARKER;
      }
      var accessor = comp.getAccessor();
      accessor.setAccessible(true);
      return render(accessor.invoke(record), walk);
    } catch (Throwable t) { // NOPMD
      return RENDER_FAILED;
    }
  }

  /** One field's rendered value, or {@link #RENDER_FAILED} when reading it failed. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // setAccessible/get may throw Error
  private String fieldValue(Field field, Object obj, RenderWalk walk) {
    try {
      if (isRedacted(field.getName(), field.isAnnotationPresent(NotTraced.class))) {
        return RedactionPolicy.MARKER;
      }
      field.setAccessible(true);
      return render(field.get(obj), walk);
    } catch (Throwable t) { // NOPMD
      return RENDER_FAILED;
    }
  }

  private String renderObject(Object obj, RenderWalk walk) {
    var clazz = obj.getClass();
    var fields = introspectableFields(clazz);
    var limit = Math.min(fields.length, maxObjectFields);
    var sb = new StringBuilder(clazz.getSimpleName()).append("{");
    for (var i = 0; i < limit; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      var field = fields[i];
      sb.append(field.getName()).append(": ").append(fieldValue(field, obj, walk));
    }
    if (fields.length > maxObjectFields) {
      sb.append(", ...");
    }
    sb.append("}");
    return sb.toString();
  }

  private String renderMap(Map<?, ?> map, RenderWalk walk) {
    if (!walk.add(map)) {
      return identityMarker(map);
    }
    try {
      var joined = String.join(", ", mapEntries(map, walk));
      return sizeOrUnknown(map) > maxCollectionItems ? "{" + joined + ", …}" : "{" + joined + "}";
    } finally {
      walk.remove(map);
    }
  }

  /**
   * Up to {@link #maxCollectionItems} rendered entries, keeping whatever the map managed to yield.
   *
   * <p><b>@edgeCase</b> An {@code entrySet()} that throws leaves {@link #RENDER_FAILED} alone; an
   * entry that throws costs only its own slot.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // entrySet() and its iterator are user code
  private List<String> mapEntries(Map<?, ?> map, RenderWalk walk) {
    var entries = new ArrayList<String>();
    try {
      for (var entry : map.entrySet()) {
        if (entries.size() >= maxCollectionItems) {
          break;
        }
        entries.add(guardedMapEntry(entry, walk));
      }
    } catch (Throwable t) { // NOPMD
      entries.add(RENDER_FAILED);
    }
    return entries;
  }

  /** One map entry, rendered so that its failure costs only its own slot. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // one bad entry may not discard the rest
  private String guardedMapEntry(Map.Entry<?, ?> entry, RenderWalk walk) {
    try {
      return renderMapEntry(entry, walk);
    } catch (Throwable t) { // NOPMD
      return RENDER_FAILED;
    }
  }

  private String renderMapEntry(Map.Entry<?, ?> entry, RenderWalk walk) {
    var keyName = renderMapKey(entry.getKey(), walk);
    var value =
        redactionPolicy.shouldRedact(keyName)
            ? RedactionPolicy.MARKER
            : render(entry.getValue(), walk);
    return keyName + "=" + value;
  }

  /**
   * Renders a map key through the same guarded path as any other value, so key objects honor
   * {@code @NotTraced}, the redaction deny-list, cycle detection, and bounded output. String keys
   * keep their historical bare form (no quotes) for readability.
   */
  private String renderMapKey(Object key, RenderWalk walk) {
    if (key instanceof String s) {
      return sanitizeAndCap(s);
    }
    return render(key, walk);
  }

  /** Sanitizes control characters and truncates to {@code maxStringLength} with an ellipsis. */
  private String sanitizeAndCap(String raw) {
    var safe = ControlEscape.sanitize(raw);
    return safe.length() > maxStringLength ? safe.substring(0, maxStringLength) + "…" : safe;
  }

  /**
   * Whether a class may stand in for introspection with its own {@code toString()}.
   *
   * <p>INTENT: A curated {@code toString()} is the better rendering of a value that has one — that
   * is why both render paths prefer it. It is <em>not</em> better than a redaction the author
   * declared: {@code @NotTraced} promises the value is hidden in all rendered and exported output,
   * and a {@code toString()} written years before anyone traced the class knows nothing about it.
   * So a class that declares a redacted field is introspected, where the annotation is honored,
   * whatever its {@code toString()} would have printed.
   *
   * <p>Both {@code renderComplex} and {@code renderStructuredComplex} ask this one method, so the
   * flat and structured paths cannot drift on the question.
   *
   * <p><b>@edgeCase</b> Two neighbouring decisions are deliberately left alone. A
   * {@code @NarrativeSummary} method still wins over everything, including this — it is code the
   * author wrote <em>for</em> the trace, so its output is their choice. And the name-based
   * deny-list of {@link RedactionPolicy} does not defeat a {@code toString()}: it is a heuristic
   * over introspected members, and the documented posture is that a class with a curated {@code
   * toString()} is trusted. Only an explicit annotation overrides that.
   */
  private static boolean rendersItsOwnString(Class<?> clazz) {
    return HAS_CUSTOM_TO_STRING.get(clazz) && !DECLARES_REDACTED_FIELD.get(clazz);
  }

  /**
   * Whether the class, or anything it inherits from, declares a {@code @NotTraced} field.
   *
   * <p><b>@llmNote</b> The walk goes up the hierarchy even though introspection only prints {@code
   * getDeclaredFields()} of the runtime class: a subclass {@code toString()} can print an inherited
   * secret through a getter, and that is the same broken promise. Cached per class by {@link
   * ClassValue}, so the reflection cost is paid once and never on the traced path afterwards.
   */
  private static final ClassValue<Boolean> DECLARES_REDACTED_FIELD =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> clazz) {
          for (var current = clazz;
              current != null && current != Object.class;
              current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
              if (field.isAnnotationPresent(NotTraced.class)) {
                return true;
              }
            }
          }
          return false;
        }
      };

  private static final ClassValue<Boolean> HAS_CUSTOM_TO_STRING =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> clazz) {
          try {
            return clazz.getMethod("toString").getDeclaringClass() != Object.class;
          } catch (NoSuchMethodException e) {
            return false;
          }
        }
      };

  private static final ClassValue<Method> SUMMARY_METHOD_CACHE =
      new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> clazz) {
          for (var method : clazz.getMethods()) {
            if (method.isAnnotationPresent(NarrativeSummary.class)
                && method.getParameterCount() == 0) {
              method.setAccessible(true); // NOPMD
              return method;
            }
          }
          return null;
        }
      };

  private static Method findNarrativeSummaryMethod(Class<?> clazz) {
    return SUMMARY_METHOD_CACHE.get(clazz);
  }
}
