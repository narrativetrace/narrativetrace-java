/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** H2-backed order storage — real leaf-span JDBC latency for {@code GET /orders/{id}}. */
@Repository
public class JdbcOrderRepository {

  private static final RowMapper<CartLine> LINE_MAPPER =
      (rs, rowNum) -> new CartLine(rs.getString("product_id"), rs.getInt("quantity"));

  private final JdbcTemplate jdbcTemplate;

  public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public String nextOrderId() {
    var sequence = jdbcTemplate.queryForObject("CALL NEXT VALUE FOR order_id_seq", Long.class);
    return "ORD-%05d".formatted(sequence);
  }

  public void save(PlacedOrder order) {
    jdbcTemplate.update(
        "INSERT INTO orders(order_id, customer_id, transaction_id, subtotal, shipping_cost,"
            + " status) VALUES (?, ?, ?, ?, ?, ?)",
        order.orderId(),
        order.customerId(),
        order.transactionId(),
        order.subtotal(),
        order.shippingCost(),
        order.status());
    var lineArgs =
        order.lines().stream()
            .map(line -> new Object[] {order.orderId(), line.productId(), line.quantity()})
            .toList();
    jdbcTemplate.batchUpdate(
        "INSERT INTO order_lines(order_id, product_id, quantity) VALUES (?, ?, ?)", lineArgs);
  }

  public Optional<PlacedOrder> findById(String orderId) {
    var rows = jdbcTemplate.query("SELECT * FROM orders WHERE order_id = ?", this::mapRow, orderId);
    return rows.stream().findFirst().map(order -> order.withLines(findLines(orderId)));
  }

  public void markCancelled(String orderId) {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'CANCELLED', transaction_id = NULL WHERE order_id = ?",
        orderId);
  }

  private List<CartLine> findLines(String orderId) {
    return jdbcTemplate.query(
        "SELECT product_id, quantity FROM order_lines WHERE order_id = ?", LINE_MAPPER, orderId);
  }

  // rowNum is unused but required by the RowMapper<T> functional interface signature.
  @SuppressWarnings("PMD.UnusedFormalParameter")
  private PlacedOrder mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new PlacedOrder(
        rs.getString("order_id"),
        rs.getString("customer_id"),
        rs.getString("transaction_id"),
        rs.getDouble("subtotal"),
        (Double) rs.getObject("shipping_cost"),
        rs.getString("status"),
        List.of());
  }
}
