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

import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import java.util.List;
import java.util.Set;

/**
 * What {@code narrativetrace init} and {@code narrativetrace uninstall} were asked to do, parsed
 * out of a command line and nothing else.
 *
 * <p>INTENT: keeps every flag decision in a pure function, so the verbs themselves stay a handful
 * of lines over the installer library — the same shape the doctor verb has.
 *
 * <p><b>@llmNote</b> A bad flag is DATA here ({@link #error()}), never an exception: the launcher
 * prints it with the verb's usage and exits 2, which is the one exit code that means "could not run
 * at all". The FIRST problem is the one reported — naming a later flag would name one nobody has
 * read yet.
 *
 * <p><b>@llmNote</b> {@code --help} wins over an error, so a mistyped command can still ask how it
 * works instead of being told twice that it was mistyped.
 *
 * @param options what the installer library was asked for
 * @param from the carrier a person named, or {@code null} for the one bundled with this launcher
 * @param json whether to print the machine-readable envelope instead of human text
 * @param help whether the verb's usage was asked for
 * @param error why the command line could not be read, or {@code null} when it could
 */
record InstallerArguments(
    InitOptions options, String from, boolean json, boolean help, String error) {

  /** The flags that take a value, written either {@code --flag value} or {@code --flag=value}. */
  private static final Set<String> VALUE_FLAGS = Set.of("--only", "--vendor", "--from");

  /** Reads the arguments that follow the verb. */
  static InstallerArguments parse(List<String> arguments) {
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

  /** One pass over the arguments; mutable so each flag stays one readable line. */
  private static final class Reading {

    private InitOptions options = InitOptions.defaults();
    private String from;
    private boolean json;
    private boolean help;
    private String error;

    InstallerArguments result() {
      return new InstallerArguments(options, from, json, help, error);
    }

    /** Reads one argument and returns the index of the last one it consumed. */
    int read(List<String> arguments, int index) {
      String argument = arguments.get(index);
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

    /** A flag that carries no value; false when the name is not one of them. */
    private boolean switched(String name) {
      switch (name) {
        case "--dry-run" -> options = options.withDryRun(true);
        case "--write-existing" -> options = options.withWriteExisting(true);
        case "--force" -> options = options.withForce(true);
        case "--json" -> json = true;
        case "--help", "-h" -> help = true;
        default -> {
          return false;
        }
      }
      return true;
    }

    /** A flag and its value; false when the value is missing, so nothing was consumed. */
    private boolean valued(String name, String value) {
      if (value == null || value.isEmpty()) {
        fail(name + " needs a value");
        return false;
      }
      switch (name) {
        case "--only" -> scope(value);
        case "--vendor" -> vendor(value);
        default -> from = value;
      }
      return true;
    }

    private void scope(String value) {
      switch (value) {
        case "skills" -> options = options.withScope(Scope.SKILLS);
        case "agents-md" -> options = options.withScope(Scope.AGENTS_MD);
        default -> fail("--only takes skills or agents-md, got \"" + value + "\"");
      }
    }

    private void vendor(String value) {
      switch (value) {
        case "claude" -> options = options.withVendorClaude(Vendor.ON);
        case "none" -> options = options.withVendorClaude(Vendor.OFF);
        default -> fail("--vendor takes claude or none, got \"" + value + "\"");
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
