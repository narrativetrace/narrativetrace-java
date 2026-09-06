/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Composes the human-readable "why" behind an element's clarity score.
 *
 * <p>INTENT: Clarity is a teacher, not a judge — a bare score is a verdict without an appeal
 * process. This class turns the same dictionary knowledge the scorers use into one plain-language
 * note per element, emitted at every score so good names learn why they are good and weak names
 * learn what to change.
 *
 * <p>The notes speak the project's language: the same {@link DomainVocabulary} the scorers use is
 * threaded through here, so a declared verb is called a domain verb, and shorthand the glossary's
 * {@code abbreviations} section accepts is never asked to be spelled out — it is spelled out
 * <em>for</em> the reader, from the project's own declaration.
 */
public final class ElementNoteComposer {

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final CollocationDictionary collocationDictionary = new CollocationDictionary();
  private final RoleSuffixDictionary roleSuffixDictionary = new RoleSuffixDictionary();
  private final VerbDictionary verbDictionary;
  private final GenericTokenDetector genericDetector;
  private final MorphologyAnalyzer morphologyAnalyzer;
  private final AbbreviationDictionary abbreviationDictionary;

  /** A composer with no project vocabulary — the built-in dictionaries alone. */
  public ElementNoteComposer() {
    this(DomainVocabulary.empty());
  }

  /**
   * The note for an identifier that carries no words at all. Said plainly rather than left blank: a
   * reader looking at a low score deserves to know the name is the reason.
   */
  private static final String NO_WORDS = "No words to read in this name";

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public ElementNoteComposer(DomainVocabulary vocabulary) {
    this.verbDictionary = new VerbDictionary(vocabulary);
    this.genericDetector = new GenericTokenDetector(vocabulary);
    this.morphologyAnalyzer = new MorphologyAnalyzer(vocabulary);
    this.abbreviationDictionary = new AbbreviationDictionary(vocabulary);
  }

  /**
   * The note for a method name, explaining its verb and object quality.
   *
   * <p><b>@edgeCase</b> An identifier can tokenize to nothing — {@code __} and the empty string
   * both do, and both reach here from bytecode the library did not write (a Kotlin unused-parameter
   * placeholder, an obfuscated jar, a synthetic accessor). There is no verb to read in a name with
   * no words, and reaching for one used to be an {@code IndexOutOfBoundsException} that failed the
   * traced run.
   */
  public String methodNote(String methodName) {
    var tokens = tokenizer.tokenize(methodName);
    if (tokens.isEmpty()) {
      return NO_WORDS;
    }
    return methodPhrase(tokens) + abbreviationNotes(tokens);
  }

  /** The note for a class name, explaining its role suffix and prefix quality. */
  public String classNote(String className) {
    var tokens = tokenizer.tokenize(className);
    if (tokens.isEmpty()) {
      return NO_WORDS;
    }
    return classPhrase(tokens) + prefixCallout(tokens) + abbreviationNotes(tokens);
  }

  /** The note for a parameter name, scored on the noun rubric. */
  public String parameterNote(String paramName) {
    var tokens = tokenizer.tokenize(paramName);
    return parameterPhrase(paramName) + abbreviationNotes(tokens);
  }

  private String parameterPhrase(String paramName) {
    return switch (genericDetector.detect(paramName).tier()) {
      case NOT_GENERIC -> "Domain-specific noun '" + paramName + "'";
      case TYPED_GENERIC -> "Broad noun '" + paramName + "' — qualify it (e.g., orderAmount)";
      case VAGUE -> "Vague name '" + paramName + "' — say what it holds";
      case MEANINGLESS -> "Meaningless name '" + paramName + "'";
    };
  }

  /** The note for a record component, scored on the noun rubric and phrased for components. */
  public String propertyNote(String componentName) {
    var tokens = tokenizer.tokenize(componentName);
    return propertyPhrase(componentName) + abbreviationNotes(tokens);
  }

  private String propertyPhrase(String name) {
    return switch (genericDetector.detect(name).tier()) {
      case NOT_GENERIC -> "Domain-specific component '" + name + "'";
      case TYPED_GENERIC -> "Broad component '" + name + "' — qualify it (e.g., orderAmount)";
      case VAGUE -> "Vague component '" + name + "' — name the component after its domain concept";
      case MEANINGLESS ->
          "Meaningless component '" + name + "' — name the component after its domain concept";
    };
  }

  private String classPhrase(List<String> tokens) {
    var suffix = tokens.get(tokens.size() - 1);
    return switch (roleSuffixDictionary.classify(suffix).category()) {
      case DESIGN_PATTERN, FUNCTIONAL -> "Role suffix '" + capitalize(suffix) + "'";
      case GENERIC -> "Generic suffix '" + capitalize(suffix) + "' — prefer a precise role";
      case UNKNOWN -> "Domain name, no conventional suffix";
    };
  }

  /** Vague or meaningless prefix tokens weaken an otherwise well-suffixed class name. */
  private String prefixCallout(List<String> tokens) {
    if (tokens.size() < 2) {
      return "";
    }
    var parts = new ArrayList<String>();
    for (var prefix : tokens.subList(0, tokens.size() - 1)) {
      switch (genericDetector.detect(prefix).tier()) {
        case VAGUE -> parts.add("vague prefix '" + capitalize(prefix) + "'");
        case MEANINGLESS -> parts.add("meaningless prefix '" + capitalize(prefix) + "'");
        default -> {
          // typed-generic and domain prefixes read well; no callout
        }
      }
    }
    return parts.isEmpty() ? "" : "; " + String.join(", ", parts);
  }

  private String methodPhrase(List<String> tokens) {
    var verb = tokens.get(0).toLowerCase(Locale.ROOT);
    if (tokens.size() == 1) {
      return verbPhrase(verb);
    }
    var noun = tokens.get(tokens.size() - 1).toLowerCase(Locale.ROOT);
    return verbPhrase(verb) + " + " + nounPhrase(noun) + renameHint(verb, noun);
  }

  /** Accepted project shorthand first, then the abbreviations the reader should still spell out. */
  private String abbreviationNotes(List<String> tokens) {
    return projectShorthand(tokens) + spellOut(tokens);
  }

  /**
   * Shorthand the project declared in its glossary is accepted, so it is never asked to be spelled
   * out — but the glossary knows what it stands for, so the note teaches it ({@code fx → foreign
   * exchange}) instead of falling silent.
   */
  private String projectShorthand(List<String> tokens) {
    var parts = new ArrayList<String>();
    for (var token : tokens) {
      var lower = token.toLowerCase(Locale.ROOT);
      var expansion = abbreviationDictionary.projectExpansionOf(lower);
      if (expansion != null) {
        parts.add(lower + " → " + expansion);
      }
    }
    return parts.isEmpty() ? "" : "; project shorthand: " + String.join(", ", parts);
  }

  /**
   * The dictionary already knows what each penalized abbreviation stands for, so the note spells it
   * out inline ({@code chk → check}). UNIVERSAL-tier tokens ({@code id}, {@code url}) are accepted
   * usage and never flagged, mirroring {@link ClarityAnalyzer}'s abbreviation issues.
   */
  private String spellOut(List<String> tokens) {
    var parts = new ArrayList<String>();
    for (var token : tokens) {
      var lower = token.toLowerCase(Locale.ROOT);
      var entry = abbreviationDictionary.lookup(lower);
      if (entry == null || entry.tier() == AbbreviationDictionary.Tier.UNIVERSAL) {
        continue;
      }
      parts.add(lower + " → " + entry.expansion());
    }
    return parts.isEmpty() ? "" : "; spell out: " + String.join(", ", parts);
  }

  /**
   * A concrete rename suggestion, but only when the verb is the problem: generic or unrecognized
   * verbs paired with a noun the collocation dictionary knows get up to three {@code verbNoun}
   * candidates. Standard and domain verbs already read well, so they earn no hint.
   */
  private String renameHint(String verb, String noun) {
    var category = verbDictionary.categorize(verb).category();
    if (category != VerbDictionary.Category.GENERIC
        && category != VerbDictionary.Category.UNKNOWN) {
      return "";
    }
    var preferred = collocationDictionary.preferredVerbs(noun);
    if (preferred.isEmpty()) {
      return "";
    }
    var capitalNoun = capitalize(noun);
    var suggestion =
        preferred.stream()
            .sorted()
            .limit(3)
            .map(v -> v + capitalNoun)
            .collect(Collectors.joining(", "));
    return " — consider: " + suggestion;
  }

  private String capitalize(String token) {
    return Character.toUpperCase(token.charAt(0)) + token.substring(1);
  }

  private String verbPhrase(String verb) {
    return switch (verbDictionary.categorize(verb).category()) {
      case GENERIC -> "Generic verb '" + verb + "'";
      case STANDARD -> "Standard verb '" + verb + "'";
      case DOMAIN -> "Domain verb '" + verb + "'";
      case BOOLEAN_PREFIX -> "Boolean prefix '" + verb + "'";
      case UNKNOWN -> unknownVerbPhrase(verb);
    };
  }

  private String unknownVerbPhrase(String verb) {
    var isVerbLike =
        morphologyAnalyzer.analyze(verb).partOfSpeech() == MorphologyAnalyzer.PartOfSpeech.VERB;
    return (isVerbLike ? "Unknown verb '" : "Unclear verb '") + verb + "'";
  }

  private String nounPhrase(String noun) {
    return switch (genericDetector.detect(noun).tier()) {
      case MEANINGLESS -> "meaningless noun '" + noun + "'";
      case VAGUE -> "vague noun '" + noun + "'";
      case TYPED_GENERIC -> "broad noun '" + noun + "'";
      case NOT_GENERIC -> "domain noun '" + noun + "'";
    };
  }
}
