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
package ai.narrativetrace.tooling.init;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides what an install would do — and nothing else.
 *
 * <p>INTENT: a pure function of {@code (ProjectState, Carrier, InitOptions)}. Every existing-file
 * policy lives here and only here, which is why each row of it is a unit test with no filesystem at
 * all, and why a {@code --dry-run} diff is exactly what a real run would write.
 *
 * <p><b>@llmNote</b> An action whose result equals what is already there is dropped, so planning a
 * project that is already current yields an EMPTY plan. That is what makes a re-run idempotent, and
 * it is the property the round-trip tests lean on.
 *
 * <p><b>@llmNote</b> A refusal never stops the plan: the rest of the actions are still planned, and
 * the run exits 1. One bad file must not cost a project its other nine.
 *
 * <p><b>@sideEffects</b> None. Nothing here reads a file or writes one.
 */
public final class InitPlanner {

  /** The managed home, the one file the section is ever written into. */
  static final Path AGENTS_MD = Path.of("AGENTS.md");

  /** The vendor context file, which gets an import line and never a copy of the section. */
  static final Path CLAUDE_MD = Path.of("CLAUDE.md");

  /** The line that points the vendor's context file at the managed home. */
  static final String IMPORT_LINE = "@AGENTS.md";

  private InitPlanner() {}

  /** The plan an install with these options would apply to this project. */
  public static InitPlan plan(ProjectState state, Carrier carrier, InitOptions options) {
    if (state == null || carrier == null || options == null) {
      throw new IllegalArgumentException("planning needs a project state, a carrier and options");
    }
    List<Action> actions = new ArrayList<>();
    if (options.scope().includesSkills()) {
      planSkills(state, carrier, options, actions);
    }
    if (options.scope().includesAgentsMd()) {
      planAgentsMd(state, carrier, options, actions);
      planClaudeMd(state, options, actions);
      planRuleFiles(state, carrier, actions);
    }
    return new InitPlan(carrier.coordinate(), options.dryRun(), withoutNoOps(actions));
  }

  // --- skills ---------------------------------------------------------------------------------

  private static void planSkills(
      ProjectState state, Carrier carrier, InitOptions options, List<Action> actions) {
    List<SkillFlavour> writable = writableFlavours(state, options, actions);
    for (SkillEntry skill : carrier.skills()) {
      for (SkillFlavour flavour : writable) {
        actions.add(skillAction(state, carrier, options, skill, flavour));
      }
    }
  }

  /**
   * The flavours anything may be written into. A flavour whose whole install root is a symbolic
   * link is refused ONCE, here, rather than once per skill: one link is one decision, and a refusal
   * per skill would also put several actions on the one path a plan allows only one of.
   */
  private static List<SkillFlavour> writableFlavours(
      ProjectState state, InitOptions options, List<Action> actions) {
    List<SkillFlavour> writable = new ArrayList<>();
    for (SkillFlavour flavour : flavours(state, options)) {
      Optional<String> linked = state.linkedInstallRoot(flavour);
      if (linked.isEmpty()) {
        writable.add(flavour);
      } else {
        actions.add(refuseLinkedRoot(flavour, linked.get()));
      }
    }
    return writable;
  }

  private static Action refuseLinkedRoot(SkillFlavour flavour, String target) {
    return new Action.Refuse(
        Path.of(flavour.installRoot()),
        flavour.installRoot()
            + " is a symbolic link to "
            + target
            + " — every skill of this flavour would be written through it; remove the link, or run"
            + " the install where it points");
  }

  /**
   * Which flavours this project gets: the open standard always, the vendor one where the project
   * looks like that vendor's — or wherever the caller overrode the detection.
   */
  private static List<SkillFlavour> flavours(ProjectState state, InitOptions options) {
    boolean vendor =
        switch (options.vendorClaude()) {
          case ON -> true;
          case OFF -> false;
          case AUTO -> state.hasClaudeDirectory() || state.claudeMd().isPresent();
        };
    return vendor
        ? List.of(SkillFlavour.AGENTS, SkillFlavour.CLAUDE)
        : List.of(SkillFlavour.AGENTS);
  }

  private static Action skillAction(
      ProjectState state,
      Carrier carrier,
      InitOptions options,
      SkillEntry skill,
      SkillFlavour flavour) {
    Path page = Path.of(flavour.installRoot(), skill.name()).resolve(InstalledSkill.PAGE);
    String rendered = carrier.body(skill, flavour);
    String content = Provenance.stamp(rendered, carrier.coordinate());
    Optional<InstalledSkill> installed = state.installedSkill(flavour, skill.name());
    if (installed.isEmpty()) {
      return new Action.CreateFile(page, content);
    }
    return switch (installed.get().presence()) {
      case NOT_A_DIRECTORY ->
          new Action.Refuse(
              installed.get().directory(),
              installed.get().directory()
                  + " is not a directory — move it aside and run the install again");
      case OURS -> write(page, installed.get().body(), content);
      case FOREIGN -> foreign(options, installed.get(), rendered, content);
      case LINKED_DIRECTORY, LINKED_PAGE -> linked(carrier, skill, installed.get(), content);
    };
  }

  /**
   * A symbolic link where a skill's directory or page belongs. Writing through it would land in
   * whatever it points at — after {@code npx skills add}, the OTHER flavour's page — so the link
   * itself is replaced whenever what it reaches is a page this install owns or would adopt, and
   * refused otherwise. No flag appears here: {@code --force} covers foreign CONTENT, and a link is
   * structure.
   */
  private static Action linked(
      Carrier carrier, SkillEntry skill, InstalledSkill installed, String content) {
    Path at = installed.linkedAt();
    if (installed.body().isEmpty()) {
      return new Action.Refuse(
          at,
          at
              + " is a symbolic link to "
              + installed.link()
              + ", and there is no page of narrativetrace's at the other end — remove the link and"
              + " run the install again");
    }
    if (!isOursOrAdoptable(carrier, skill, installed.body())) {
      return new Action.Refuse(
          at,
          at
              + " is a symbolic link to "
              + installed.link()
              + ", a page narrativetrace did not install — remove the link and run the install"
              + " again; --force covers content, never a link");
    }
    return new Action.ReplaceLink(at, installed.page(), installed.link(), content);
  }

  /**
   * Whether a page reached through a link is one this install would own anyway: already stamped, or
   * identical to what this carrier renders for EITHER flavour. Either flavour, because the link a
   * registry leaves at the vendor path points at the open-standard page.
   */
  private static boolean isOursOrAdoptable(Carrier carrier, SkillEntry skill, String body) {
    if (Provenance.coordinateIn(body).isPresent()) {
      return true;
    }
    return Adoption.isAdoptable(body, carrier.body(skill, SkillFlavour.AGENTS))
        || Adoption.isAdoptable(body, carrier.body(skill, SkillFlavour.CLAUDE));
  }

  /**
   * A directory somebody else's tool wrote. A page equal to what this carrier renders is ADOPTED —
   * it is our own page, installed by a registry rather than by us, so stamping it takes nothing
   * from anybody. Anything else is a refusal until {@code --force} says otherwise.
   */
  private static Action foreign(
      InitOptions options, InstalledSkill installed, String rendered, String content) {
    if (Adoption.isAdoptable(installed.body(), rendered)) {
      return new Action.AdoptPage(installed.page(), installed.body(), content);
    }
    if (!options.force()) {
      return new Action.Refuse(
          installed.directory(),
          installed.directory()
              + " was not installed by narrativetrace — re-run with --force to overwrite this"
              + " skill, or move the directory aside");
    }
    return write(installed.page(), installed.body(), content);
  }

  /** A write is a create when nothing is there and a replacement when something is. */
  private static Action write(Path page, String current, String content) {
    return current.isEmpty()
        ? new Action.CreateFile(page, content)
        : new Action.ReplaceBlock(page, current, content);
  }

  // --- the managed section --------------------------------------------------------------------

  private static void planAgentsMd(
      ProjectState state, Carrier carrier, InitOptions options, List<Action> actions) {
    String block = AgentsMdBlock.render(carrier, state);
    Optional<String> existing = state.agentsMd();
    if (existing.isEmpty()) {
      actions.add(new Action.CreateFile(AGENTS_MD, MarkedBlock.CREATED_NOTE + "\n" + block));
      return;
    }
    actions.add(blockAction(AGENTS_MD, existing.get(), block, options.writeExisting()));
  }

  /** The same decision for any file that may carry the section: our home and the rule files. */
  private static Action blockAction(
      Path path, String text, String block, boolean mayWriteExisting) {
    MarkedBlock.Scan scan = MarkedBlock.scan(text);
    if (!scan.problems().isEmpty()) {
      return new Action.Refuse(path, path + ": " + String.join("; ", scan.problems()));
    }
    if (scan.regions().size() > 1) {
      return new Action.Refuse(path, path + " carries " + lines(scan) + " — leave exactly one");
    }
    String matched = MarkedBlock.withEol(block, MarkedBlock.eolOf(text));
    if (scan.regions().size() == 1) {
      return new Action.ReplaceBlock(
          path, text, MarkedBlock.replace(text, scan.regions().get(0), matched));
    }
    if (!mayWriteExisting) {
      return new Action.Refuse(
          path,
          path
              + " exists and carries no NarrativeTrace section — re-run with --write-existing to"
              + " append one");
    }
    if (MarkedBlock.endsInsideFence(text)) {
      return new Action.Refuse(path, unfinishedFence(path));
    }
    return new Action.AppendBlock(path, text, matched);
  }

  private static String lines(MarkedBlock.Scan scan) {
    return "two or more NarrativeTrace sections ("
        + scan.regions().stream().map(region -> "line " + region.startLine()).toList()
        + ")";
  }

  private static void planClaudeMd(ProjectState state, InitOptions options, List<Action> actions) {
    Optional<String> existing = state.claudeMd();
    if (existing.isEmpty()
        || MarkedBlock.lineIsIgnoringTrailingSpace(existing.get(), IMPORT_LINE).isPresent()) {
      return;
    }
    if (!options.writeExisting()) {
      actions.add(
          new Action.Refuse(
              CLAUDE_MD,
              CLAUDE_MD
                  + " exists and does not import "
                  + AGENTS_MD
                  + " — re-run with --write-existing to add the one-line import"));
      return;
    }
    if (MarkedBlock.endsInsideFence(existing.get())) {
      actions.add(new Action.Refuse(CLAUDE_MD, unfinishedFence(CLAUDE_MD)));
      return;
    }
    actions.add(new Action.AppendLine(CLAUDE_MD, existing.get(), IMPORT_LINE));
  }

  /**
   * A vendor rule file is never created and never appended to: an existing managed section in one
   * is kept current, and that is all.
   */
  private static void planRuleFiles(ProjectState state, Carrier carrier, List<Action> actions) {
    String block = AgentsMdBlock.render(carrier, state);
    state
        .markedRuleFiles()
        .forEach((path, text) -> actions.add(blockAction(Path.of(path), text, block, false)));
  }

  /**
   * A file that ends inside an unfinished fenced code block cannot be appended to: whatever is
   * added lands inside that fence, invisible to this installer's own markers and to every Markdown
   * reader, so the next run would add it again.
   */
  private static String unfinishedFence(Path path) {
    return path
        + " ends inside an unfinished fenced code block — close the fence, and anything appended"
        + " after it will be read as text rather than as code";
  }

  /** Drops every action that would write what is already there. */
  private static List<Action> withoutNoOps(List<Action> actions) {
    return actions.stream().filter(InitPlanner::changesSomething).toList();
  }

  private static boolean changesSomething(Action action) {
    return !(action instanceof Action.FileEdit edit) || !edit.before().equals(edit.after());
  }
}
