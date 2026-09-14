/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the soak harness's downstream notify process.
 *
 * <p>INTENT: Simulate the third-party call the ecommerce example's {@code
 * JsonPlaceholderNotificationService} makes, without the soak ever reaching an external host: a
 * configurable delay and a configurable failure rate, nothing else. See {@code
 * ai.narrativetrace.soak.notify.domain.NotificationProcessor} for the traced core.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotifyApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotifyApplication.class, args);
  }
}
