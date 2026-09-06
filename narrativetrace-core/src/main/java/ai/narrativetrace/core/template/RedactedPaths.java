/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.core.render.RedactionPolicy;

/**
 * Decides whether a template path names something redaction hides.
 *
 * <p>INTENT: {@code @Narrated("charging {card.cvv}")} used to print the cvv in full, because
 * template resolution stringified whatever the accessor returned. Naming a path must never weaken
 * the rules that apply to the value directly — the author who needs the value in a narrative
 * removes {@code @NotTraced} from the component, and that removal is the deliberate, reviewable
 * decision.
 *
 * <p><b>@llmNote</b> The decision itself is {@link RedactionPolicy#isRedacted} — the same call
 * {@code ValueRenderer} makes for a reflectively-introspected member. This class only supplies the
 * two inputs that call needs for a path: the segment's name, and whether the member it names
 * carries {@code @NotTraced}.
 *
 * <p><b>@edgeCase</b> A segment that names no member at all stops the walk and redacts nothing. A
 * placeholder matching no member is an authoring typo, and swallowing it as {@code [REDACTED]}
 * would hide the unresolved-placeholder warning that exists to catch it. Nothing can leak either
 * way: a path that names nothing resolves to nothing.
 */
final class RedactedPaths {

  private RedactedPaths() {}

  /**
   * Whether any segment of {@code path}, walked from {@code root}, names a redacted member.
   *
   * <p>Redaction applies at every depth: it is the whole path that is refused, so a redacted
   * segment in the middle hides everything named below it too.
   *
   * @param root the object the placeholder's leading key resolved to, possibly {@code null}
   * @param path the dot-separated member path after that key, e.g. {@code "card.cvv"}
   * @return {@code true} when the path reaches something redaction hides
   */
  static boolean redacts(Object root, String path) {
    if (root == null) {
      return false;
    }
    var owner = root.getClass();
    for (var segment : path.split("\\.", -1)) {
      var member = member(owner, segment);
      if (member == null) {
        return false;
      }
      if (RedactionPolicy.DEFAULT.isRedacted(segment, member.notTraced())) {
        return true;
      }
      owner = member.type();
    }
    return false;
  }

  /** What a path segment names on its owner: the type it leads to, and whether it is annotated. */
  private record Member(Class<?> type, boolean notTraced) {}

  /**
   * Resolves one segment against its owner: the field that declares it (superclasses included),
   * then a bare accessor.
   *
   * <p><b>@llmNote</b> The field scan covers records too, and deliberately so rather than by
   * oversight. {@code @NotTraced} lists {@code FIELD} among its targets, so JLS 8.10.3 propagates
   * an annotation on a record component onto the private field the record generates for it, with
   * the same type. A separate record-component branch would be a second spelling of one answer —
   * every mutation of it is equivalent, which is how it was found.
   *
   * <p><b>@edgeCase</b> The accessor fallback is annotation-blind by construction, since
   * {@code @NotTraced} cannot target a method. A computed property with no backing field therefore
   * gets the name-based half of the rule only.
   */
  private static Member member(Class<?> owner, String name) {
    for (var type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
      for (var field : type.getDeclaredFields()) {
        if (field.getName().equals(name)) {
          return new Member(field.getType(), field.isAnnotationPresent(NotTraced.class));
        }
      }
    }
    var accessor = TemplateParser.findAccessor(owner, name);
    return accessor == null ? null : new Member(accessor.getReturnType(), false);
  }
}
