/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import ai.narrativetrace.examples.ecommerce.ProductCatalogService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * H2-backed {@link ProductCatalogService} — real leaf-span JDBC latency instead of the ecommerce
 * example's in-memory map, per README.md's persistence design. {@link #listAll()} is new surface
 * beyond the reused interface, for {@code GET /catalog}.
 */
@Repository
public class JdbcProductCatalogService implements ProductCatalogService {

  private final JdbcTemplate jdbcTemplate;

  public JdbcProductCatalogService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public double lookupPrice(String productId) {
    var prices =
        jdbcTemplate.queryForList(
            "SELECT price FROM catalog WHERE product_id = ?", Double.class, productId);
    if (prices.isEmpty()) {
      throw new IllegalArgumentException("Product not found: " + productId);
    }
    return prices.get(0);
  }

  public List<CatalogItem> listAll() {
    return jdbcTemplate.query(
        "SELECT product_id, name, price FROM catalog ORDER BY product_id",
        (rs, rowNum) ->
            new CatalogItem(
                rs.getString("product_id"), rs.getString("name"), rs.getDouble("price")));
  }
}
