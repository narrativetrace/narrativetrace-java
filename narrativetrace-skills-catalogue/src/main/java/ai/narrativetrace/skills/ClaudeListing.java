/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/**
 * The Anthropic plugin directory's own listing fields, beside the vendor-neutral {@link
 * MarketplaceListing} every registry shares. The directory titles a plugin from {@code displayName}
 * (without it, the title is the kebab-case name) and links its support, privacy and documentation
 * entries from the three URLs; everything else the manifest needs comes from {@link #base()}.
 *
 * @param base the vendor-neutral listing this directory entry is drawn from
 * @param displayName the title shown in the directory
 * @param supportUrl where a user reports a problem
 * @param privacyPolicyUrl the privacy policy
 * @param documentationUrl where the documentation lives
 */
public record ClaudeListing(
    MarketplaceListing base,
    String displayName,
    String supportUrl,
    String privacyPolicyUrl,
    String documentationUrl) {}
