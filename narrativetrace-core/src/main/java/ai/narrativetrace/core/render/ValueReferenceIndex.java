/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content-addressed deduplication of captured values for one rendering pass.
 *
 * <p>INTENT: A value whose rendered form repeats across a trace carries information only where it
 * differs; byte-identical repetition is noise that hides the render that changed. This index
 * collects the rendered parameter and return values of a {@link TraceTree}, decides which of them
 * earn a reference (long enough and emitted more than once), and hands renderers a display form:
 * first emission defines {@code ‹label›=full}, later emissions are just {@code ‹label›}. Equality
 * is byte equality of the rendered string.
 *
 * <p>A value that differs is not simply re-rendered in full, though. When two rendered forms belong
 * to the same <em>entity</em> — same structured type name, same value in the identity field that
 * names the label — the later one renders as {@code ‹label›′{amount: 100.0→92.0}}, a diff against
 * the reference this document already defines. That keeps the artifact self-contained (the baseline
 * is on the page) and makes the one field that moved the thing the reader sees, instead of a second
 * near-identical blob. Anything the diff cannot express — a structural change, an identity-less
 * value — falls back to the full render, unchanged.
 *
 * <p>One instance serves exactly one rendering pass: label definition order follows emission order,
 * so the instance is stateful and must not be shared across renders.
 */
final class ValueReferenceIndex {

  private static final int MIN_REF_LENGTH = 40;
  private static final int MAX_LABEL_LENGTH = 24;
  private static final List<String> IDENTITY_FIELDS =
      List.of("name", "id", "description", "title", "key", "label", "code");

  private final Set<String> referenced;
  private final List<String> referencedByLengthDesc;
  private final Map<String, RenderedValue> structuredByContent;
  private final Map<String, String> deltaKeyByContent;
  private final Map<String, String> groupAnchors = new HashMap<>();
  private final Map<String, String> definedLabels = new HashMap<>();
  private final Map<String, Integer> baseLabelUses = new HashMap<>();
  private int genericCounter;

  private ValueReferenceIndex(
      Set<String> referenced,
      Map<String, RenderedValue> structuredByContent,
      Map<String, String> deltaKeyByContent) {
    this.referenced = referenced;
    this.referencedByLengthDesc =
        referenced.stream()
            .sorted(
                Comparator.comparingInt(String::length).reversed().thenComparing(s -> (String) s))
            .toList();
    this.structuredByContent = structuredByContent;
    this.deltaKeyByContent = deltaKeyByContent;
  }

  /** Collects candidate values from the tree; values emitted at least twice earn a reference. */
  static ValueReferenceIndex build(TraceTree tree) {
    var counts = new HashMap<String, Integer>();
    var structured = new HashMap<String, RenderedValue>();
    for (var root : tree.roots()) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> countNode(n, counts, structured),
          (n, depth, reason) -> countNode(n, counts, structured));
    }
    var referenced = new HashSet<String>();
    counts.forEach(
        (value, count) -> {
          if (count + containmentCount(value, counts) >= 2) {
            referenced.add(value);
          }
        });
    return new ValueReferenceIndex(referenced, structured, deltaKeys(structured));
  }

  /**
   * Maps each candidate value to its identity key when that key covers more than one rendered form
   * — the same entity, captured again with something changed — <em>and</em> at least one of those
   * forms can actually be expressed as a delta of another. The second condition is what keeps the
   * fallback silent: a pair whose difference is structural renders exactly as it did before this
   * feature existed, with no reference label stamped on a definition nothing ever refers back to.
   */
  private static Map<String, String> deltaKeys(Map<String, RenderedValue> structured) {
    var formsByKey = new HashMap<String, List<String>>();
    structured.forEach(
        (rendered, value) -> {
          var key = identityKey(value);
          if (key != null) {
            formsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(rendered);
          }
        });
    var keyByContent = new HashMap<String, String>();
    formsByKey.forEach(
        (key, forms) -> {
          if (anyPairDiffs(forms, structured)) {
            forms.forEach(rendered -> keyByContent.put(rendered, key));
          }
        });
    return keyByContent;
  }

  /**
   * Whether any two of one identity's rendered forms differ by scalar fields alone. Self-pairs cost
   * nothing to include: a value has no delta against itself.
   */
  private static boolean anyPairDiffs(List<String> forms, Map<String, RenderedValue> structured) {
    var values = forms.stream().map(structured::get).toList();
    return values.stream()
        .anyMatch(one -> values.stream().anyMatch(other -> ValueDelta.between(one, other) != null));
  }

  /** Emissions of {@code value} nested inside other captured values, weighted by their counts. */
  private static int containmentCount(String value, Map<String, Integer> counts) {
    int total = 0;
    for (var entry : counts.entrySet()) {
      var container = entry.getKey();
      if (!container.equals(value)) {
        total += occurrencesIn(container, value) * entry.getValue();
      }
    }
    return total;
  }

  private static int occurrencesIn(String container, String value) {
    int occurrences = 0;
    int idx = container.indexOf(value);
    while (idx >= 0) {
      occurrences++;
      idx = container.indexOf(value, idx + value.length());
    }
    return occurrences;
  }

  /**
   * A node's own parameter and return values only — {@link #build} drives the walk over its
   * children, bounded and cycle-safe via {@link TreeWalk}: a hand-built, replayed or deserialized
   * tree is not guaranteed acyclic, and a genuinely deep tree is ordinary for a recursive business
   * method.
   */
  private static void countNode(
      TraceNode node, Map<String, Integer> counts, Map<String, RenderedValue> structured) {
    for (var param : node.signature().parameters()) {
      if (!param.redacted()) {
        countValue(param.renderedValue(), param.structuredValue(), counts, structured);
      }
    }
    if (node.outcome() instanceof TraceOutcome.Returned r) {
      countValue(r.renderedValue(), r.structuredValue(), counts, structured);
    }
  }

  private static void countValue(
      String rendered,
      RenderedValue structuredValue,
      Map<String, Integer> counts,
      Map<String, RenderedValue> structured) {
    if (rendered == null || rendered.length() < MIN_REF_LENGTH) {
      return;
    }
    counts.merge(rendered, 1, Integer::sum);
    if (structuredValue != null) {
      structured.putIfAbsent(rendered, structuredValue);
    }
  }

  /**
   * Returns the emission form of a rendered value: the full text for unreferenced values, {@code
   * ‹label›=full} the first time a referenced value is emitted, {@code ‹label›} afterwards, and
   * {@code ‹label›′{field: before→after}} for a later emission of the same entity that changed.
   */
  String display(String rendered) {
    if (rendered == null) {
      return null;
    }
    if (referenced.contains(rendered)) {
      return referenceDisplay(rendered);
    }
    var delta = deltaDisplay(rendered);
    return delta != null ? delta : replaceContained(rendered);
  }

  /**
   * Defines or reuses the label of a byte-repeated value. A repeated value that is itself a changed
   * re-capture of an already-defined reference is defined AS the delta ({@code
   * ‹label·2›=‹label›′{…} }) rather than as a second full blob — it repeats, so it earns its own
   * label, but the change is still what the reader sees.
   */
  private String referenceDisplay(String value) {
    var label = definedLabels.get(value);
    if (label != null) {
      return label;
    }
    var created = newLabel(structuredByContent.get(value));
    definedLabels.put(value, created);
    var asDelta = deltaAgainstAnchor(value);
    rememberAnchor(value);
    return created + "=" + (asDelta != null ? asDelta : replaceContained(value));
  }

  /**
   * Emission form for a value whose identity was seen in more than one rendered form: the first
   * such form emitted becomes the in-document reference (defined in full), and every later,
   * differing form renders as a diff against it. Returns {@code null} when the value is not part of
   * such a pair or the difference cannot be expressed as a scalar field diff, leaving the caller on
   * the full-render path.
   */
  private String deltaDisplay(String rendered) {
    var key = deltaKeyByContent.get(rendered);
    if (key == null) {
      return null;
    }
    if (groupAnchors.get(key) == null) {
      return defineAnchor(key, rendered);
    }
    return deltaAgainstAnchor(rendered);
  }

  /**
   * The {@code ‹anchor›′{field: before→after}} form of a value whose identity already has a
   * reference defined in this document, or {@code null} when this value belongs to no delta pair,
   * its identity has no reference yet (this emission is about to become one), or the difference is
   * not a scalar field diff.
   */
  private String deltaAgainstAnchor(String rendered) {
    var anchor = groupAnchors.get(deltaKeyByContent.get(rendered));
    var delta =
        ValueDelta.between(structuredByContent.get(anchor), structuredByContent.get(rendered));
    return delta == null ? null : definedLabels.get(anchor) + "′" + delta;
  }

  private String defineAnchor(String key, String rendered) {
    var label = newLabel(structuredByContent.get(rendered));
    definedLabels.put(rendered, label);
    groupAnchors.put(key, rendered);
    return label + "=" + replaceContained(rendered);
  }

  /** Records a newly-labelled value as its identity's reference, if it belongs to a delta pair. */
  private void rememberAnchor(String rendered) {
    var key = deltaKeyByContent.get(rendered);
    if (key != null) {
      groupAnchors.putIfAbsent(key, rendered);
    }
  }

  /**
   * Display for a value <em>named</em> on a loop-fold summary line: the {@code ‹anchor›′{…}} delta
   * when this iteration is a changed re-capture of a value the document already defines in full,
   * and otherwise the identity label {@link #foldLabel} mints. A value that already carries a label
   * keeps it — a label the reader has seen beats a diff against a different baseline.
   *
   * @param rendered the distinguishing argument's rendered form (byte identity of the value)
   * @param structured its structured form, source of the delta and of the identity label
   */
  String foldDisplay(String rendered, RenderedValue structured) {
    var named = definedLabels.get(rendered);
    if (named != null) {
      return named;
    }
    var delta = deltaAgainstAnchor(rendered);
    return delta != null ? delta : foldLabel(rendered, structured);
  }

  /**
   * Mints the label for a not-yet-named value on a loop-fold summary line, through the same
   * identity ladder {@link #display} uses so the fold line and any later {@code ‹ref›} emission
   * agree, and records it as an emission. Returns {@code null} when no value-derived label exists
   * (a scalar or identity-less value), so the caller can fall back to a positional marker.
   *
   * @param rendered the distinguishing argument's rendered form (byte identity of the value)
   * @param structured its structured form, source of the identity-field / type-name label
   */
  private String foldLabel(String rendered, RenderedValue structured) {
    if (rendered == null || deriveBaseLabel(structured) == null) {
      return null;
    }
    var label = newLabel(structured);
    definedLabels.put(rendered, label);
    return label;
  }

  /**
   * Replaces every occurrence of a referenced value inside {@code rendered} with its reference,
   * defining it inline (‹label›=full) on first emission. Longest values are replaced first so a
   * value nested inside another referenced value resolves inside that definition.
   */
  private String replaceContained(String rendered) {
    var result = rendered;
    for (var value : referencedByLengthDesc) {
      if (!value.equals(rendered)) {
        result = replaceOccurrences(result, value);
      }
    }
    return result;
  }

  private String replaceOccurrences(String text, String value) {
    var idx = text.indexOf(value);
    if (idx < 0) {
      return text;
    }
    var sb = new StringBuilder();
    int from = 0;
    while (idx >= 0) {
      sb.append(text, from, idx).append(referenceDisplay(value));
      from = idx + value.length();
      idx = text.indexOf(value, from);
    }
    return sb.append(text, from, text.length()).toString();
  }

  private String newLabel(RenderedValue structured) {
    var base = deriveBaseLabel(structured);
    if (base == null) {
      return "‹v" + (++genericCounter) + "›";
    }
    var uses = baseLabelUses.merge(base, 1, Integer::sum);
    return uses == 1 ? "‹" + base + "›" : "‹" + base + "·" + uses + "›";
  }

  private static String deriveBaseLabel(RenderedValue structured) {
    if (!(structured instanceof RenderedValue.ObjectVal obj)) {
      return null;
    }
    var identity = identityFieldValue(obj);
    return identity != null ? identity : obj.typeName();
  }

  /**
   * Picks the first identity-signaling field with a usable plain-string value. Redacted markers are
   * skipped so a label can never resurrect a value that redaction removed.
   */
  private static String identityFieldValue(RenderedValue.ObjectVal obj) {
    var field = identityFieldName(obj);
    return field == null ? null : capLabel(ControlEscape.sanitize(identityText(obj, field)));
  }

  /** The plain text of an identity field {@link #identityFieldName} has already vetted. */
  private static String identityText(RenderedValue.ObjectVal obj, String field) {
    return ((RenderedValue.StringVal) obj.fields().get(field)).value();
  }

  private static String identityFieldName(RenderedValue.ObjectVal obj) {
    for (var fieldName : IDENTITY_FIELDS) {
      if (obj.fields().get(fieldName) instanceof RenderedValue.StringVal s
          && isUsableLabelText(s.value())) {
        return fieldName;
      }
    }
    return null;
  }

  /**
   * Stable key for "the same entity": the structured type name plus the uncapped value of the
   * identity field that named it. Two captures sharing this key are the same thing at two moments,
   * which is what makes a delta between them meaningful. {@code null} when the value carries no
   * identity field — a type name alone would group unrelated instances of the same class, and a
   * diff between two different expenses is noise, not signal.
   */
  private static String identityKey(RenderedValue structured) {
    if (structured instanceof RenderedValue.ObjectVal obj) {
      var field = identityFieldName(obj);
      if (field != null) {
        return obj.typeName() + ' ' + field + ' ' + identityText(obj, field);
      }
    }
    return null;
  }

  private static boolean isUsableLabelText(String text) {
    return text != null && !text.isBlank() && !RedactionPolicy.MARKER.equals(text);
  }

  private static String capLabel(String text) {
    return text.length() > MAX_LABEL_LENGTH ? text.substring(0, MAX_LABEL_LENGTH) + "…" : text;
  }
}
