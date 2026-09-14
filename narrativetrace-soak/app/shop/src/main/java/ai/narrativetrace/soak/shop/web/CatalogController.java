/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import ai.narrativetrace.soak.shop.domain.CatalogItem;
import ai.narrativetrace.soak.shop.domain.JdbcProductCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

  private final JdbcProductCatalogService catalog;

  public CatalogController(JdbcProductCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/catalog")
  public List<CatalogItem> catalog() {
    return catalog.listAll();
  }
}
