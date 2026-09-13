/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The {@code entry-point} kind: does {@code coordinate} resolve at all, at the version under test,
 * on the real registry — jar and pom both {@code HEAD}-checked, same classification {@code
 * scripts/verify-publication.sh} uses (200 PRESENT, 404 LAGGING, anything else MISSING). No Gradle
 * dependency resolution involved on purpose: a coordinate that resolves in a build but whose
 * artifacts are not actually reachable over HTTP is exactly the gap this catches.
 */
public final class EntryPointProbe {

  private EntryPointProbe() {}

  public static String observe(String coordinate, String registryBase, String version) {
    String[] parts = coordinate.split(":", 2);
    if (parts.length != 2) {
      return "MISSING";
    }
    String groupPath = parts[0].replace('.', '/');
    String artifact = parts[1];
    String base =
        registryBase
            + "/"
            + groupPath
            + "/"
            + artifact
            + "/"
            + version
            + "/"
            + artifact
            + "-"
            + version;
    String worst = "PRESENT";
    for (String extension : new String[] {"pom", "jar"}) {
      String verdict = classify(headStatus(base + "." + extension));
      if ("MISSING".equals(verdict)) {
        return "MISSING";
      }
      if ("LAGGING".equals(verdict)) {
        worst = "LAGGING";
      }
    }
    return worst;
  }

  private static int headStatus(String url) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .timeout(Duration.ofSeconds(20))
              .build();
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (Exception e) { // NOPMD - a network hiccup is "no answer", not a probe crash
      return 0;
    }
  }

  private static String classify(int status) {
    if (status == 200) {
      return "PRESENT";
    }
    if (status == 404) {
      return "LAGGING";
    }
    return "MISSING";
  }
}
