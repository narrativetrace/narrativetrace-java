/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify;

import ai.narrativetrace.soak.notify.domain.NotificationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NotifyExceptionHandler {

  @ExceptionHandler(NotificationFailedException.class)
  public ResponseEntity<ErrorResponse> onSimulatedFailure(NotificationFailedException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("SIMULATED_FAILURE", e.getMessage()));
  }
}
