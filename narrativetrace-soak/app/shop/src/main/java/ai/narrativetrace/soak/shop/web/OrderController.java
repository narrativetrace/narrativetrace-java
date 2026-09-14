/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import ai.narrativetrace.soak.shop.domain.CartLine;
import ai.narrativetrace.soak.shop.domain.OrderPlacementService;
import ai.narrativetrace.soak.shop.domain.PlacedOrder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderPlacementService orders;

  public OrderController(OrderPlacementService orders) {
    this.orders = orders;
  }

  @PostMapping
  public ResponseEntity<PlacedOrder> place(@Valid @RequestBody PlaceOrderRequest request) {
    var lines =
        request.lines().stream().map(l -> new CartLine(l.productId(), l.quantity())).toList();
    var order =
        orders.placeOrder(
            request.customerId(),
            lines,
            request.email(),
            request.cardNumber(),
            request.sessionCookie(),
            request.jwt());
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
  }

  @GetMapping("/{id}")
  public PlacedOrder get(@PathVariable String id) {
    return orders.getOrder(id);
  }

  @PostMapping("/{id}/cancel")
  public PlacedOrder cancel(@PathVariable String id) {
    return orders.cancelOrder(id);
  }
}
