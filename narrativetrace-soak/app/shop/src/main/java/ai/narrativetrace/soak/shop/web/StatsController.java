/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import ai.narrativetrace.soak.shop.domain.SoakStats;
import ai.narrativetrace.soak.shop.domain.SoakStatsCollector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

  private final SoakStatsCollector collector;

  public StatsController(SoakStatsCollector collector) {
    this.collector = collector;
  }

  @GetMapping("/soak/stats")
  public SoakStats stats() {
    return collector.collect();
  }
}
