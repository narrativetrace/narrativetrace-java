/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
