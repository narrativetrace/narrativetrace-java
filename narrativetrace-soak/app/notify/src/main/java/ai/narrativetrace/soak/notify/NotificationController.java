/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify;

import ai.narrativetrace.soak.notify.domain.NotificationOutcome;
import ai.narrativetrace.soak.notify.domain.NotificationProcessor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

  private final NotificationProcessor processor;

  public NotificationController(NotificationProcessor processor) {
    this.processor = processor;
  }

  @PostMapping("/notifications")
  public ResponseEntity<NotificationOutcome> receive(
      @Valid @RequestBody NotificationRequest request) {
    var outcome = processor.process(request.customerId(), request.orderId());
    return ResponseEntity.ok(outcome);
  }
}
