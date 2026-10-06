/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.util.List;
import java.util.Optional;

/**
 * Parsed {@code --skill --case --platform --model [--agent-command] [--trials]} runner arguments.
 */
public record EvalRunnerArgs(
    String skill,
    String caseName,
    Platform platform,
    String model,
    String agentCommand,
    int trials) {

  public EvalRunnerArgs {
    if (skill == null || skill.isBlank()) {
      throw new IllegalArgumentException("--skill is required");
    }
    if (caseName == null || caseName.isBlank()) {
      throw new IllegalArgumentException("--case is required");
    }
    if (platform == null) {
      throw new IllegalArgumentException("--platform is required");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("--model is required");
    }
    if (trials < 1) {
      throw new IllegalArgumentException("--trials must be at least 1");
    }
  }

  public Optional<String> agentCommandOverride() {
    return Optional.ofNullable(agentCommand);
  }

  private static final String USAGE =
      "Usage: EvalRunner --skill <name> --case <name> --platform <claude|codex|gemini> --model"
          + " <id> [--agent-command \"<template with {prompt}>\"] [--trials N]";

  public static EvalRunnerArgs parse(String[] argv) {
    List<String> args = List.of(argv);
    String skill = valueOf(args, "--skill");
    String caseName = valueOf(args, "--case");
    String platformArg = valueOf(args, "--platform");
    String model = valueOf(args, "--model");
    Platform platform = platformArg == null ? null : Platform.parse(platformArg).orElse(null);
    if (skill == null || caseName == null || platform == null || model == null) {
      throw new IllegalArgumentException(USAGE);
    }
    String agentCommand = valueOf(args, "--agent-command");
    String trialsArg = valueOf(args, "--trials");
    int trials = trialsArg == null ? 1 : Integer.parseInt(trialsArg);
    return new EvalRunnerArgs(skill, caseName, platform, model, agentCommand, trials);
  }

  private static String valueOf(List<String> args, String flag) {
    int i = args.indexOf(flag);
    return i < 0 || i + 1 >= args.size() ? null : args.get(i + 1);
  }
}
