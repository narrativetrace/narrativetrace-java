/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The rendered pages and the marketplace file ship under Apache while the repository root's own
 * LICENSE is BSL, so NOTICE — the file a reader consults to learn which licence covers what — has
 * to name them. Derived from {@link RenderPaths} rather than from three string literals: a renamed
 * render target must fail here, not go quietly unlicensed.
 *
 * <p>The sibling half of this gate lives in the build: {@code licensingCheck} fails when an {@code
 * open} module of {@code licensing.properties} is missing from NOTICE. Modules there, paths here.
 */
class NoticeNamesEveryRenderTargetTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  @Test
  void noticeNamesTheRootOfEveryPublishedRenderTarget() throws IOException {
    String notice = Files.readString(REPO_ROOT.resolve("NOTICE"));
    var firstSkill = CatalogueIndex.ALL.get(0);

    assertThat(notice)
        .as("NOTICE must name the Claude-flavour page root")
        .contains(
            relative(RenderPaths.claudeSkillMd(REPO_ROOT, firstSkill).getParent().getParent()))
        .as("NOTICE must name the open-standard page root")
        .contains(relative(RenderPaths.codexSkillMd(REPO_ROOT, firstSkill).getParent().getParent()))
        .as("NOTICE must name the marketplace file")
        .contains(relative(RenderPaths.marketplaceJson(REPO_ROOT)));
  }

  private static String relative(Path target) {
    return REPO_ROOT.relativize(target).toString();
  }
}
