/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration imported alongside {@link EnableNarrativeTrace}.
 *
 * <p>INTENT: Keeps the annotation import stable while the registrar handles the actual bean
 * registrations programmatically.
 *
 * @see EnableNarrativeTrace
 */
@Configuration
public class NarrativeTraceConfiguration {}
