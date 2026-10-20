/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import java.util.List;

/**
 * The OpenAI plugin directory's own listing fields, beside the vendor-neutral {@link
 * MarketplaceListing} every registry shares. {@code displayName} and {@code shortDescription} carry
 * the directory's own 30-character ceiling; everything else the directory's manifest needs — {@code
 * name}, {@code description}, {@code author.name}, {@code homepage}, {@code license}, {@code
 * keywords} — comes from {@link #base()}, so this record never repeats a fact the vendor-neutral
 * listing already states.
 *
 * <p>No {@code privacyPolicyUrl} or {@code termsOfServiceUrl} field: the two URLs are optional for
 * a skills-only submission, narrativetrace.ai carries neither page yet, and a field this type would
 * carry but {@link ai.narrativetrace.skills.render.OpenAiPluginJsonRenderer} would never read is
 * dead weight, not a listing. Add both back together with the rendering they would drive once the
 * two pages exist.
 *
 * @param base the vendor-neutral listing this directory entry is drawn from
 * @param displayName shown in the directory, at most 30 characters
 * @param shortDescription the one-line pitch, at most 30 characters
 * @param category the directory's own grouping for the listing (allowed values unverified)
 * @param capabilities what the plugin declares it can do (allowed values unverified)
 * @param defaultPrompts example prompts shown to a user before they have typed anything
 * @param brandColor the listing's accent colour, a hex string
 * @param websiteUrl where a reader goes to learn more
 */
public record OpenAiListing(
    MarketplaceListing base,
    String displayName,
    String shortDescription,
    String category,
    List<String> capabilities,
    List<String> defaultPrompts,
    String brandColor,
    String websiteUrl) {

  /** The directory's own ceiling on {@code displayName} and {@code shortDescription} [S]. */
  public static final int MAX_LISTING_FIELD_LENGTH = 30;

  public OpenAiListing {
    requireAtMost(displayName, "displayName");
    requireAtMost(shortDescription, "shortDescription");
    capabilities = List.copyOf(capabilities);
    defaultPrompts = List.copyOf(defaultPrompts);
  }

  private static void requireAtMost(String value, String field) {
    if (value.length() > MAX_LISTING_FIELD_LENGTH) {
      throw new IllegalArgumentException(
          field
              + " must be at most "
              + MAX_LISTING_FIELD_LENGTH
              + " characters, was "
              + value.length());
    }
  }
}
