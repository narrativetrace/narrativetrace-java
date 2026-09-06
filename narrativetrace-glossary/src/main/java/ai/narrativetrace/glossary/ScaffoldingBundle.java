/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Per-locale renderer scaffolding shipped with the library (plan Phase 6).
 *
 * <p>INTENT: Translated trace views need locale variants of the renderer's fixed vocabulary
 * ("returns", "throws", fork/join labels, the glossary-gaps heading) independent of any project
 * glossary. Backed by {@code scaffolding*.properties} resource bundles; a locale without a bundle
 * falls back to the English base — never to the host JVM's default locale.
 */
public final class ScaffoldingBundle {

  private final ResourceBundle bundle;

  private ScaffoldingBundle(ResourceBundle bundle) {
    this.bundle = bundle;
  }

  /**
   * Loads the scaffolding bundle for a locale tag (e.g. {@code "es"}, {@code "zh-CN"}). Unknown
   * locales resolve to the English base bundle.
   */
  public static ScaffoldingBundle forLocale(String localeTag) {
    if (localeTag == null || localeTag.isBlank()) {
      throw new IllegalArgumentException("localeTag must not be blank");
    }
    var bundle =
        ResourceBundle.getBundle(
            "ai.narrativetrace.glossary.scaffolding",
            Locale.forLanguageTag(localeTag),
            ScaffoldingBundle.class.getClassLoader(),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    return new ScaffoldingBundle(bundle);
  }

  /** Label for a successful return ("returns"). */
  public String returns() {
    return bundle.getString("returns");
  }

  /** Label for a thrown exception ("throws"). */
  public String throwsLabel() {
    return bundle.getString("throws");
  }

  /** Label for a call that never completed ("incomplete"). */
  public String incomplete() {
    return bundle.getString("incomplete");
  }

  /** Label for a fork marker ("fork"). */
  public String fork() {
    return bundle.getString("fork");
  }

  /** Label for a join marker ("join"). */
  public String join() {
    return bundle.getString("join");
  }

  /** Heading introducing fire-and-forget branches ("In the background:"). */
  public String background() {
    return bundle.getString("background");
  }

  /** Heading of the glossary-gaps footer ("Glossary gaps"). */
  public String gapsHeading() {
    return bundle.getString("gapsHeading");
  }
}
