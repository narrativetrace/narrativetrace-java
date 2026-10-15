/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.platforms;

import ai.narrativetrace.tooling.init.CursorRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The writer behind {@code ./gradlew :narrativetrace-tooling:renderPlatforms}: re-renders every
 * repository file that is a view of the platform table, in the repository named by the first
 * argument. Lives with the tests because nothing that ships may write into a repository; {@code
 * PlatformFilesDriftTest} is the gate it satisfies.
 */
public final class RenderPlatformsMain {

  private RenderPlatformsMain() {}

  public static void main(String[] args) throws IOException {
    Path repo = Path.of(args[0]);
    rewrite(repo.resolve(PlatformFilesDriftTest.CURSOR_RULE), PlatformFilesDriftTest.cursorRule());
    Path page = repo.resolve(PlatformTableDocs.PAGE);
    rewrite(page, PlatformTableDocs.splice(Files.readString(page)));
  }

  private static void rewrite(Path file, String rendered) throws IOException {
    if (!Files.exists(file) || !rendered.equals(Files.readString(file))) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, rendered);
      System.out.println("renderPlatforms: rewrote " + file);
    }
  }

  /** Keeps the import honest for readers of the class doc: the rule is {@link CursorRule}'s. */
  static String ruleText() {
    return ((Pointer.Rule) PlatformTable.row("cursor").orElseThrow().pointer()).text();
  }

  static String render() {
    return CursorRule.render(ruleText(), "");
  }
}
