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
package ai.narrativetrace.cli;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.feedback.AgentIdentity;
import ai.narrativetrace.tooling.feedback.FeedbackCategory;
import ai.narrativetrace.tooling.feedback.FeedbackGatherer;
import ai.narrativetrace.tooling.feedback.FeedbackReport;
import ai.narrativetrace.tooling.feedback.ProblemNarrative;
import java.util.List;
import java.util.Set;

/**
 * What {@code narrativetrace feedback} was asked to do, parsed out of a command line and nothing
 * else.
 *
 * <p>INTENT: the same shape {@link InstallerArguments} has, for the same reason — every flag
 * decision stays a pure function, so the verb itself is a handful of lines over the tooling
 * library. A bad flag is DATA ({@link #error()}), never an exception, and the FIRST problem is the
 * one reported.
 *
 * <p><b>@llmNote</b> The five report fields are MANDATORY and that is checked here rather than left
 * to the library's constructor, so a person who forgets one is told which flag they forgot instead
 * of reading a constructor's message about a blank field.
 *
 * @param channel {@code draft}, {@code url} or {@code gh}
 * @param trace a path suffix naming the structural trace to attach, or {@code ""} to let the verb
 *     choose
 * @param error why the command line could not be read, or {@code null} when it could
 */
record FeedbackArguments(
    String channel,
    String category,
    String step,
    String did,
    String happened,
    String expected,
    String language,
    String agentProduct,
    String agentModel,
    String trace,
    boolean json,
    boolean help,
    String error) {

  /** The three channels, in the order the skill offers them. */
  static final List<String> CHANNELS = List.of("draft", "url", "gh");

  private static final Set<String> VALUE_FLAGS =
      Set.of(
          "--category",
          "--step",
          "--did",
          "--happened",
          "--expected",
          "--language",
          "--agent-product",
          "--agent-model",
          "--trace");

  /** Reads the arguments that follow the verb; the first of them is the channel. */
  static FeedbackArguments parse(List<String> arguments) {
    if (arguments == null) {
      throw new IllegalArgumentException("a command line is a list of arguments, never null");
    }
    Reading reading = new Reading();
    int index = 0;
    while (index < arguments.size()) {
      index = reading.read(arguments, index) + 1;
    }
    return reading.result();
  }

  /** The report these arguments and this project describe. */
  FeedbackReport report(DoctorSnapshot snapshot, String doctorReportJson) {
    return FeedbackReport.builder()
        .runtime(ai.narrativetrace.tooling.feedback.PublicRepository.RUNTIME)
        .category(FeedbackCategory.ofId(category))
        .install(FeedbackGatherer.installCoordinate(snapshot))
        .step(step)
        .narrative(new ProblemNarrative(did, happened, expected))
        .language(language)
        .agent(new AgentIdentity(agentProduct, agentModel))
        .attachments(FeedbackGatherer.attachments(snapshot, doctorReportJson, trace))
        .build();
  }

  /** One pass over the arguments; mutable so each flag stays one readable line. */
  private static final class Reading {

    private String channel = "";
    private String category = "";
    private String step = "";
    private String did = "";
    private String happened = "";
    private String expected = "";
    private String language = "en";
    private String agentProduct = "";
    private String agentModel = "";
    private String trace = "";
    private boolean json;
    private boolean help;
    private String error;

    FeedbackArguments result() {
      validate();
      return new FeedbackArguments(
          channel,
          category,
          step,
          did,
          happened,
          expected,
          language,
          agentProduct,
          agentModel,
          trace,
          json,
          help,
          error);
    }

    /** Reads one argument and returns the index of the last one it consumed. */
    int read(List<String> arguments, int index) {
      String argument = arguments.get(index);
      if (!argument.startsWith("-") && channel.isEmpty()) {
        channel = argument;
        return index;
      }
      int equals = argument.indexOf('=');
      String name = equals < 0 ? argument : argument.substring(0, equals);
      if (switched(name)) {
        return index;
      }
      if (!VALUE_FLAGS.contains(name)) {
        fail("unknown option: \"" + argument + "\"");
        return index;
      }
      int valueIndex = equals < 0 ? index + 1 : index;
      String value = equals < 0 ? at(arguments, valueIndex) : argument.substring(equals + 1);
      return valued(name, value) ? valueIndex : index;
    }

    private boolean switched(String name) {
      switch (name) {
        case "--json" -> json = true;
        case "--help", "-h" -> help = true;
        default -> {
          return false;
        }
      }
      return true;
    }

    private boolean valued(String name, String value) {
      if (value == null || value.isEmpty()) {
        fail(name + " needs a value");
        return false;
      }
      switch (name) {
        case "--category" -> category = value;
        case "--step" -> step = value;
        case "--did" -> did = value;
        case "--happened" -> happened = value;
        case "--expected" -> expected = value;
        case "--language" -> language = value;
        case "--agent-product" -> agentProduct = value;
        case "--agent-model" -> agentModel = value;
        default -> trace = value;
      }
      return true;
    }

    /** Everything a channel cannot run without, checked in the order a person types it. */
    private void validate() {
      if (help) {
        return;
      }
      if (!CHANNELS.contains(channel)) {
        fail(
            channel.isEmpty()
                ? "feedback needs a channel: " + String.join(", ", CHANNELS)
                : "unknown feedback channel: \"" + channel + "\"");
        return;
      }
      requireCategory();
      require(step, "--step");
      require(did, "--did");
      require(happened, "--happened");
      require(expected, "--expected");
    }

    private void requireCategory() {
      if (category.isEmpty()) {
        fail("--category needs a value: prompt, skill, doctor or library");
        return;
      }
      try {
        FeedbackCategory.ofId(category);
      } catch (IllegalArgumentException e) {
        fail(e.getMessage());
      }
    }

    private void require(String value, String flag) {
      if (value.isBlank()) {
        fail(flag + " is required — a report without it says nothing");
      }
    }

    private void fail(String message) {
      if (error == null) {
        error = message;
      }
    }

    private static String at(List<String> arguments, int index) {
      return index < arguments.size() ? arguments.get(index) : null;
    }
  }
}
