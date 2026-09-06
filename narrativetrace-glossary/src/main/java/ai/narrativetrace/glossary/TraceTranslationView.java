/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.core.export.CanonicalEntry;
import ai.narrativetrace.core.export.ParameterEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Renders canonical trace entries as a translated, human-readable view for one locale.
 *
 * <p>INTENT: The derived per-locale trace of the glossary plan (Phase 6). The load-bearing rule is
 * re-derivation, not substitution: every line is rebuilt from structural fields plus verbatim
 * values, so no value token ({@code nt.parameters[].value}, {@code nt.returnValue}, {@code
 * exception.message}) is ever altered — the pre-rendered {@code message} field is never parsed.
 * Identifiers translate via the glossary within the entry's bounded context (resolved from the
 * captured {@code nt.package} field, falling back to {@code packageOf} for pre-1.2 canonical
 * files); translated identifiers keep the original in parentheses so the canonical log stays
 * greppable. Untranslated phrases render as-is and are collected into a per-state gap registry —
 * the work queue that drives glossary completion.
 *
 * <p>Rendering is per-entry: {@link #renderEntry(CanonicalEntry, RenderState)} renders exactly one
 * canonical entry against a mutable {@link RenderState} (created per trace and locale via {@link
 * #newRenderState(String)}), so a live pipeline subscriber can translate an event stream as it
 * arrives. Depth is computed incrementally from the parent-span chain — a parent's enter always
 * precedes its child's — so an entry whose enter was never seen (a late event after state eviction)
 * renders at depth 0: degraded, never wrong. The batch {@link #render(List, String)} is a loop over
 * the same per-entry implementation plus the {@link #renderGapsFooter(RenderState)}.
 */
public final class TraceTranslationView {

  private final Glossary glossary;
  private final UnaryOperator<String> packageOf;
  private final GlossaryTranslator translator;
  private final ContextResolver resolver;
  private final TermNormalizer normalizer = new TermNormalizer();

  /**
   * @param glossary glossary providing per-context terms and translations
   * @param packageOf maps a simple class name to its package ({@code null} when unknown), the same
   *     function harvesting uses, so context resolution matches the committed glossary
   */
  public TraceTranslationView(Glossary glossary, UnaryOperator<String> packageOf) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    if (packageOf == null) {
      throw new IllegalArgumentException("packageOf must not be null");
    }
    this.glossary = glossary;
    this.packageOf = packageOf;
    this.translator = new GlossaryTranslator(glossary);
    this.resolver = new ContextResolver(glossary);
  }

  /**
   * Creates the mutable rendering state for one trace in one locale: span depths accumulated
   * incrementally and the glossary-gap registry. Not thread-safe — confine one state to one
   * rendering sequence.
   *
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   * @return fresh state for {@link #renderEntry(CanonicalEntry, RenderState)}
   */
  public RenderState newRenderState(String locale) {
    return new RenderState(locale);
  }

  /**
   * Renders one canonical entry into the state's locale, updating the state's span depths and gap
   * registry as a side effect.
   *
   * @param entry the canonical entry to render
   * @param state per-trace state from {@link #newRenderState(String)}
   * @return the rendered lines for this entry (empty when the entry renders nothing, e.g. a
   *     successful exit without a return value)
   */
  public String renderEntry(CanonicalEntry entry, RenderState state) {
    if (entry == null) {
      throw new IllegalArgumentException("entry must not be null");
    }
    if (state == null) {
      throw new IllegalArgumentException("state must not be null");
    }
    return new EntryRenderer(state).render(entry);
  }

  /**
   * Renders the glossary-gaps footer for everything accumulated in the state's gap registry.
   *
   * @param state per-trace state from {@link #newRenderState(String)}
   * @return the footer, or an empty string when no phrase stayed untranslated
   */
  public String renderGapsFooter(RenderState state) {
    if (state == null) {
      throw new IllegalArgumentException("state must not be null");
    }
    if (state.gaps.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder("\n---\n").append(state.bundle.gapsHeading()).append(":\n");
    for (var gap : state.gaps) {
      sb.append("- ").append(gap).append('\n');
    }
    return sb.toString();
  }

  /**
   * Renders the entries of one trace file into the target locale — a loop over {@link
   * #renderEntry(CanonicalEntry, RenderState)} followed by {@link #renderGapsFooter(RenderState)}.
   *
   * @param entries canonical entries in file order
   * @param locale target locale tag (e.g. {@code "es"})
   * @return the translated view, one line per rendered entry, plus a glossary-gaps footer whenever
   *     any phrase stayed untranslated
   */
  public String render(List<CanonicalEntry> entries, String locale) {
    if (entries == null) {
      throw new IllegalArgumentException("entries must not be null");
    }
    var state = newRenderState(locale);
    var sb = new StringBuilder();
    for (var entry : entries) {
      sb.append(renderEntry(entry, state));
    }
    sb.append(renderGapsFooter(state));
    return sb.toString();
  }

  /**
   * Mutable per-trace, per-locale rendering state: the incremental span-depth map (fed by enter
   * entries, read by exits) and the sorted glossary-gap registry the footer renders from. Created
   * via {@link TraceTranslationView#newRenderState(String)}; not thread-safe.
   */
  public static final class RenderState {

    private final String locale;
    private final ScaffoldingBundle bundle;
    private final Map<String, Integer> depths = new HashMap<>();
    private final TreeSet<String> gaps = new TreeSet<>();

    private RenderState(String locale) {
      this.bundle = ScaffoldingBundle.forLocale(locale);
      this.locale = locale;
    }
  }

  /** Renders exactly one entry against a {@link RenderState}; lives for one render call. */
  @SuppressWarnings("PMD.AvoidStringBufferField") // an EntryRenderer lives for exactly one entry
  private final class EntryRenderer {

    private final RenderState state;
    private final StringBuilder sb = new StringBuilder();

    private EntryRenderer(RenderState state) {
      this.state = state;
    }

    String render(CanonicalEntry entry) {
      if ("method_enter".equals(entry.ntEventType())) {
        renderEnter(entry, enterDepth(entry));
      } else if ("method_exit".equals(entry.ntEventType())) {
        renderExit(entry, state.depths.getOrDefault(entry.spanId(), 0));
      }
      return sb.toString();
    }

    /**
     * Records and returns this enter's depth from the parent-span chain. A parent's enter precedes
     * its child's, so the parent depth is already in the state; an unknown parent (or a late event
     * after eviction) lands at depth 0.
     */
    private int enterDepth(CanonicalEntry entry) {
      if (entry.spanId() != null) {
        var parentDepth = state.depths.get(entry.parentSpanId());
        state.depths.put(entry.spanId(), parentDepth == null ? 0 : parentDepth + 1);
      }
      return state.depths.getOrDefault(entry.spanId(), 0);
    }

    private void renderEnter(CanonicalEntry entry, int depth) {
      var context = contextOf(entry);
      indent(depth);
      sb.append(entry.codeNamespace()).append('.');
      appendFunction(entry.codeFunction(), context);
      appendParameters(entry, context);
      sb.append('\n');
      appendNarration(entry, context, depth);
    }

    /**
     * Renders the translated narration line beneath the call when the raw template has a locale
     * variant; a template without one becomes a glossary gap and renders nothing (the resolved
     * English narration is never substituted into).
     */
    private void appendNarration(CanonicalEntry entry, String context, int depth) {
      var template = entry.ntNarrationTemplate();
      if (template == null) {
        return;
      }
      var variant = translator.templateVariant(template, context, state.locale);
      if (variant.isEmpty()) {
        state.gaps.add(template);
        return;
      }
      indent(depth + 1);
      sb.append(fillPlaceholders(variant.get(), entry)).append('\n');
    }

    /** Fills {@code {name}} placeholders from the untouched parameter values. */
    private String fillPlaceholders(String template, CanonicalEntry entry) {
      var filled = template;
      if (entry.ntParameters() != null) {
        for (var p : entry.ntParameters()) {
          filled = filled.replace("{" + p.name() + "}", p.value());
        }
      }
      return filled;
    }

    private void renderExit(CanonicalEntry entry, int depth) {
      if ("failure".equals(entry.ntOutcome())) {
        indent(depth + 1);
        appendException(entry);
        sb.append('\n');
      } else if ("success".equals(entry.ntOutcome()) && entry.ntReturnValue() != null) {
        indent(depth + 1);
        sb.append("-> ").append(state.bundle.returns()).append(' ').append(entry.ntReturnValue());
        sb.append('\n');
      } else if ("incomplete".equals(entry.ntOutcome())) {
        indent(depth + 1);
        sb.append(".. ").append(state.bundle.incomplete());
        sb.append('\n');
      }
    }

    /**
     * {@code !! <translated phrase> [OriginalType]: <verbatim message>}; an uncovered exception
     * phrase falls back to the classic {@code !! OriginalType: message} line.
     */
    private void appendException(CanonicalEntry entry) {
      sb.append("!! ");
      var context = contextOf(entry);
      var candidate =
          entry.exceptionType() == null
              ? Optional.<TermNormalizer.Candidate>empty()
              : normalizer.exceptionCandidate(entry.exceptionType());
      if (candidate.isPresent()) {
        var result = translateOrGap(candidate.get().phrase(), context);
        if (result.complete()) {
          sb.append(result.text()).append(" [").append(entry.exceptionType()).append(']');
        } else {
          sb.append(entry.exceptionType());
        }
      } else if (entry.exceptionType() != null) {
        sb.append(entry.exceptionType());
      }
      if (entry.exceptionMessage() != null) {
        sb.append(": ").append(entry.exceptionMessage());
      }
    }

    private void indent(int depth) {
      sb.append("  ".repeat(depth));
    }

    private void appendFunction(String function, String context) {
      var result = translateOrGap(normalizer.phrase(function), context);
      if (result.complete()) {
        sb.append(result.text()).append(" (").append(function).append(") ");
      } else {
        sb.append(function);
      }
    }

    private void appendParameters(CanonicalEntry entry, String context) {
      sb.append('(');
      var parameters =
          entry.ntParameters() == null ? List.<ParameterEntry>of() : entry.ntParameters();
      for (int i = 0; i < parameters.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        var p = parameters.get(i);
        sb.append(parameterName(p.name(), context)).append(": ").append(p.value());
      }
      sb.append(')');
    }

    private String parameterName(String name, String context) {
      var candidate = normalizer.parameterCandidate(name);
      if (candidate.isEmpty()) {
        return name;
      }
      var result = translateOrGap(candidate.get().phrase(), context);
      return result.complete() ? result.text() : name;
    }

    /**
     * Resolves the bounded context for an entry. The captured {@code nt.package} field (schema 1.2)
     * is the primary source — identity captured at the site is authoritative; the {@code packageOf}
     * resolver is the fallback for pre-1.2 canonical files. When the package is unknown or
     * unmatched and the glossary declares exactly one context, that context applies — a
     * single-context glossary is unambiguous. Multi-context glossaries stay strict and resolve to
     * {@code _unassigned}.
     */
    private String contextOf(CanonicalEntry entry) {
      var packageName =
          entry.ntPackage() != null ? entry.ntPackage() : packageOrEmpty(entry.codeNamespace());
      var resolved = resolver.resolve(packageName);
      // No is-unassigned guard: the resolver only returns declared context names or UNASSIGNED,
      // so when exactly one context is declared, it either matched (identity) or is the fallback.
      var declared =
          glossary.contexts().keySet().stream()
              .filter(name -> !ContextResolver.UNASSIGNED.equals(name))
              .toList();
      return declared.size() == 1 ? declared.get(0) : resolved;
    }

    private GlossaryTranslator.PhraseTranslation translateOrGap(String phrase, String context) {
      var result = translator.translate(phrase, context, state.locale);
      if (!result.complete()) {
        state.gaps.add(phrase);
      }
      return result;
    }
  }

  private String packageOrEmpty(String className) {
    var packageName = packageOf.apply(className);
    return packageName == null ? "" : packageName;
  }
}
