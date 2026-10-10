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
package ai.narrativetrace.tooling.frameworks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The writer behind {@code ./gradlew :narrativetrace-tooling:renderFrameworkTable}: re-renders the
 * framework-table blocks of {@code llms-full.md} and {@code llms.txt} in the repository named by
 * the first argument, for the version named by the second. Lives with the tests because nothing
 * that ships may write into a repository; {@code FrameworkTableDocsTest} is the gate it satisfies.
 */
public final class RenderFrameworkTableMain {

  private RenderFrameworkTableMain() {}

  public static void main(String[] args) throws IOException {
    Path repo = Path.of(args[0]);
    String version = args[1];
    rewrite(
        repo.resolve(FrameworkTableDocs.LLMS_FULL),
        FrameworkTableDocs.renderLlmsFull(read(repo, FrameworkTableDocs.LLMS_FULL), version));
    rewrite(
        repo.resolve(FrameworkTableDocs.LLMS_TXT),
        FrameworkTableDocs.renderLlmsTxt(read(repo, FrameworkTableDocs.LLMS_TXT)));
  }

  private static String read(Path repo, String relative) throws IOException {
    return Files.readString(repo.resolve(relative));
  }

  private static void rewrite(Path file, String rendered) throws IOException {
    if (!rendered.equals(Files.readString(file))) {
      Files.writeString(file, rendered);
      System.out.println("renderFrameworkTable: rewrote " + file.getFileName());
    }
  }
}
