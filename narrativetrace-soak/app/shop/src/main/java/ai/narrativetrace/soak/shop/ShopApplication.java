/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the soak harness's shop process (see ../../README.md).
 *
 * <p>INTENT: Reuse the ecommerce example's domain services (customer, catalog, inventory, payment,
 * discount, shipping) by project dependency, wrapped in a new web/JDBC layer under {@code
 * ai.narrativetrace.soak.shop} — see {@code ai.narrativetrace.soak.shop.domain} for the package the
 * java agent instruments.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication {

  public static void main(String[] args) {
    SpringApplication.run(ShopApplication.class, args);
  }
}
