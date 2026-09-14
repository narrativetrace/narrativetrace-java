/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import ai.narrativetrace.examples.ecommerce.PaymentDeclinedException;
import ai.narrativetrace.soak.shop.domain.OrderAlreadyCancelledException;
import ai.narrativetrace.soak.shop.domain.OrderNotFoundException;
import ai.narrativetrace.soak.shop.domain.SoakMetrics;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Classifies every failure into the three kinds README.md's oracle design calls for: edge
 * rejections (never reached the domain), business failures (reported domain outcomes), and poison
 * exceptions (the deliberately open {@code quantity} gate). {@link SoakMetrics} backs {@code GET
 * /soak/stats}; the response body/status carries the same classification for {@code run-soak.sh} to
 * count from the k6/access logs too.
 */
@RestControllerAdvice
public class ShopExceptionHandler {

  private final SoakMetrics metrics;

  public ShopExceptionHandler(SoakMetrics metrics) {
    this.metrics = metrics;
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ErrorResponse> onEdgeRejection(Exception e) {
    metrics.incrementEdgeRejection();
    return respond(HttpStatus.BAD_REQUEST, "EDGE_REJECTION", e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> onUnknownCustomerOrProduct(IllegalArgumentException e) {
    metrics.incrementBusinessFailure();
    return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> onInventoryFailure(IllegalStateException e) {
    if ("true".equals(MDC.get("soak.poison"))) {
      metrics.incrementPoisonException();
      return respond(HttpStatus.INTERNAL_SERVER_ERROR, "POISON", "!! " + e.getMessage());
    }
    metrics.incrementBusinessFailure();
    return respond(HttpStatus.CONFLICT, "OUT_OF_STOCK", e.getMessage());
  }

  @ExceptionHandler(PaymentDeclinedException.class)
  public ResponseEntity<ErrorResponse> onPaymentDeclined(PaymentDeclinedException e) {
    metrics.incrementBusinessFailure();
    return respond(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", e.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ErrorResponse> onOrderNotFound(OrderNotFoundException e) {
    metrics.incrementBusinessFailure();
    return respond(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(OrderAlreadyCancelledException.class)
  public ResponseEntity<ErrorResponse> onAlreadyCancelled(OrderAlreadyCancelledException e) {
    metrics.incrementBusinessFailure();
    return respond(HttpStatus.CONFLICT, "ALREADY_CANCELLED", e.getMessage());
  }

  private static ResponseEntity<ErrorResponse> respond(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message));
  }
}
