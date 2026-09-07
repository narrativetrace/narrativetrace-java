/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import ai.narrativetrace.api.annotation.NotTraced;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Turns a declarative {@link GraphCase} into a live object graph.
 *
 * <p>INTENT: The corpus stays data — every runtime copies {@code graphs.json} verbatim and writes
 * its own builder. Only this class knows what an {@code Optional} or an {@code
 * AtomicReferenceArray} is.
 *
 * <p><b>@llmNote</b> {@code layers} is applied innermost-first, so {@code ["optional","map"]} is a
 * map holding an {@code Optional}. Every shape whose case says {@code payload: "secret-record"}
 * carries a {@link Secret} whose {@code @NotTraced} component holds the caller's sentinel token, so
 * the redaction oracle can look for that token in every byte of every output.
 *
 * <p><b>@edgeCase</b> Some shapes are hostile at *construction*, not only at render: a map that is
 * its own key hashes itself, and a self-holding {@code AbstractMap.SimpleEntry} needs {@code
 * setValue} because a record cannot hold itself. Those are built with the JDK types that permit it,
 * never with {@code Map.of}, which rejects both nulls and self-reference.
 */
public final class HostileGraphs {

  private HostileGraphs() {}

  /**
   * A record with one redacted component — the shape a container-redaction leak was found in.
   *
   * @param label rendered normally, so an output with no {@code label} is empty rather than
   *     redacted
   * @param secret the sentinel; must reach no byte of any output
   */
  public record Secret(String label, @NotTraced String secret) {}

  /** A one-component record, for the {@code holder} layer. */
  public record Holder(Object held) {}

  /** A two-component record, for the {@code record} layer. */
  public record Wrapped(String label, Object payload) {}

  /** Builds the graph a case describes, planting {@code sentinel} wherever the case says. */
  public static Object build(GraphCase graphCase, String sentinel) {
    var payload = graphCase.carriesSecret() ? secret(sentinel) : "no-payload";
    if (graphCase.kind() == null) {
      return stack(payload, graphCase.layers());
    }
    return byKind(graphCase, payload, sentinel);
  }

  /** The sentinel-bearing record, behind {@code @NotTraced}. */
  public static Secret secret(String sentinel) {
    return new Secret("visible-label", sentinel);
  }

  private static Object byKind(GraphCase graphCase, Object payload, String sentinel) {
    return switch (graphCase.kind()) {
      case "repeatLayer" -> stack(payload, repeated(graphCase.layer(), graphCase.n()));
      case "width" -> wide(graphCase.container(), graphCase.n(), payload);
      case "cycle" -> ring(graphCase.n(), sentinel);
      case "selfInCollection" -> selfReferencing(graphCase.container(), payload);
      case "diamond" -> diamond(payload);
      case "hostileMember" -> hostile(graphCase.member(), sentinel);
      case "manyFields" -> new HostileMembers.ManyFields(secret(sentinel));
      case "emptyContainers" -> emptyContainers();
      case "future" -> future(graphCase.state(), sentinel);
      case "throwable" -> throwable(graphCase.state(), graphCase.n());
      default -> throw new IllegalArgumentException("unknown graph kind: " + graphCase.kind());
    };
  }

  private static List<String> repeated(String layer, int count) {
    var layers = new ArrayList<String>(count);
    for (int i = 0; i < count; i++) {
      layers.add(layer);
    }
    return layers;
  }

  /**
   * Wraps an arbitrary payload in the named layers, innermost first.
   *
   * <p><b>@llmNote</b> The declarative {@code layers} path always plants the builder's own secret
   * record. This is the same stacking for a payload the caller built — the fuzz target uses it to
   * put a sentinel behind a field name the vocabulary corpus supplied.
   *
   * @param layers wrapper names, index 0 innermost; an empty list returns the payload unchanged
   * @param payload the object to wrap
   * @return the wrapped graph
   */
  public static Object wrap(List<String> layers, Object payload) {
    return stack(payload, layers);
  }

  private static Object stack(Object payload, List<String> layers) {
    var current = payload;
    for (var layer : layers) {
      current = wrap(layer, current);
    }
    return current;
  }

  private static Object wrap(String layer, Object inner) {
    return switch (layer) {
      case "optional" -> Optional.of(inner);
      case "atomicReference" -> new AtomicReference<>(inner);
      case "atomicReferenceArray" -> new AtomicReferenceArray<>(new Object[] {inner});
      case "entryValue" -> new AbstractMap.SimpleEntry<>("key", inner);
      case "entryKey" -> new AbstractMap.SimpleEntry<>(inner, "value");
      case "future" -> CompletableFuture.completedFuture(inner);
      case "list" -> List.of(inner);
      case "array" -> new Object[] {inner};
      case "map" -> singleEntryMap(inner);
      case "record" -> new Wrapped("wrapper", inner);
      case "holder" -> new Holder(inner);
      default -> throw new IllegalArgumentException("unknown layer: " + layer);
    };
  }

  private static Map<String, Object> singleEntryMap(Object inner) {
    var map = new LinkedHashMap<String, Object>();
    map.put("key", inner);
    return map;
  }

  /** One container of {@code count} elements, the payload last so truncation cannot hide it. */
  private static Object wide(String container, int count, Object payload) {
    var elements = new ArrayList<>(count + 1);
    for (int i = 0; i < count; i++) {
      elements.add("listWithNulls".equals(container) ? null : "filler-" + i);
    }
    elements.add(payload);
    return switch (container) {
      case "list", "listWithNulls" -> elements;
      case "array" -> elements.toArray();
      case "map" -> indexedMap(elements);
      default -> throw new IllegalArgumentException("unknown container: " + container);
    };
  }

  private static Map<String, Object> indexedMap(List<Object> elements) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < elements.size(); i++) {
      map.put("key-" + i, elements.get(i));
    }
    return map;
  }

  /** A ring of {@code length} nodes; length 1 is an object that holds itself. */
  private static Object ring(int length, String sentinel) {
    var nodes = new ArrayList<HostileMembers.Ring>(length);
    for (int i = 0; i < length; i++) {
      nodes.add(new HostileMembers.Ring(secret(sentinel)));
    }
    for (int i = 0; i < length; i++) {
      nodes.get(i).linkTo(nodes.get((i + 1) % length));
    }
    return nodes.get(0);
  }

  private static Object selfReferencing(String container, Object payload) {
    return switch (container) {
      case "list" -> selfHoldingList(payload);
      case "map" -> selfHoldingMap(payload, false);
      case "mapKey" -> selfHoldingMap(payload, true);
      case "array" -> selfHoldingArray(payload);
      case "atomicReferenceArray" -> selfHoldingAtomicArray(payload);
      case "entry" -> selfHoldingEntry(payload);
      default -> throw new IllegalArgumentException("unknown container: " + container);
    };
  }

  private static Object selfHoldingList(Object payload) {
    var list = new ArrayList<>();
    list.add(payload);
    list.add(list);
    return list;
  }

  private static Object selfHoldingMap(Object payload, boolean asKey) {
    var map = new LinkedHashMap<Object, Object>();
    map.put("payload", payload);
    if (asKey) {
      map.put(map, "self-as-key");
    } else {
      map.put("self", map);
    }
    return map;
  }

  private static Object selfHoldingArray(Object payload) {
    var array = new Object[2];
    array[0] = payload;
    array[1] = array;
    return array;
  }

  private static Object selfHoldingAtomicArray(Object payload) {
    var array = new AtomicReferenceArray<>(2);
    array.set(0, payload);
    array.set(1, array);
    return array;
  }

  private static Object selfHoldingEntry(Object payload) {
    var entry = new AbstractMap.SimpleEntry<Object, Object>(payload, "placeholder");
    entry.setValue(entry);
    return entry;
  }

  /** The same object twice by different paths: shared, not cyclic — a cycle guard must allow it. */
  private static Object diamond(Object payload) {
    var shared = new Holder(payload);
    return List.of(new Wrapped("left", shared), new Wrapped("right", shared));
  }

  private static Object hostile(String member, String sentinel) {
    var held = secret(sentinel);
    return switch (member) {
      case "toStringThrows" -> new HostileMembers.Throwing(held);
      case "toStringThrowsWithPayload" -> new HostileMembers.ThrowingWithPayload(held);
      case "toStringRecurses" -> new HostileMembers.Recursing(held);
      case "toStringBlocks" -> new HostileMembers.Blocking(held);
      case "toStringHuge" -> new HostileMembers.Huge(held);
      case "toStringNull" -> new HostileMembers.NullReturning(held);
      case "numberHostileToString" -> new HostileMembers.NumberHostileToString(held);
      case "hashCodeThrows" -> new HostileMembers.HashThrowing(held);
      case "equalsThrows" -> new HostileMembers.EqualsThrowing(held);
      case "getterThrows" -> new HostileMembers.GetterThrowing(held);
      case "accessorThrows" -> new HostileMembers.AccessorThrowing(held, "label");
      case "hostileKeyNames" -> HostileMembers.hostileKeyNames(held);
      default -> throw new IllegalArgumentException("unknown hostile member: " + member);
    };
  }

  private static Object emptyContainers() {
    var map = new LinkedHashMap<String, Object>();
    map.put("list", List.of());
    map.put("array", new Object[0]);
    map.put("map", Map.of());
    map.put("optional", Optional.empty());
    map.put("atomicReferenceArray", new AtomicReferenceArray<>(0));
    map.put("string", "");
    return map;
  }

  private static Object future(String state, String sentinel) {
    if ("pending".equals(state)) {
      return new CompletableFuture<>();
    }
    if ("cancelled".equals(state)) {
      var future = new CompletableFuture<>();
      future.cancel(true);
      return future;
    }
    return CompletableFuture.failedFuture(
        new IllegalStateException("lookup failed for " + sentinel));
  }

  /**
   * An exception carrying prose. Deliberately not the sentinel: an exception message is text the
   * application wrote, and showing it is the renderer's job, so a containment assertion here would
   * pin the opposite of the contract. The message-carries-a-secret path is {@link
   * HostileMembers.ThrowingWithPayload}, where the renderer's own fallback is what must not print
   * it.
   */
  private static Object throwable(String state, int depth) {
    Throwable current = new IllegalStateException("payment declined for card 4111");
    if ("suppressed".equals(state)) {
      var outer = new IllegalStateException("payment declined");
      outer.addSuppressed(current);
      return outer;
    }
    for (int i = 0; i < depth; i++) {
      current = new IllegalStateException("layer " + i, current);
    }
    return current;
  }

  /**
   * The fixture graphs {@code templates.json} resolves against, by the name its {@code values}
   * field carries. Every one plants the caller's sentinel behind either {@code @NotTraced} or a
   * deny-listed property name, so a template that names the path must render the marker instead.
   *
   * <p><b>@llmNote</b> The last three are <em>scalars</em>, not graphs, and they exist because
   * every fixture above them is an object: each one makes a template name a property path, which is
   * the placeholder production that was already correct. The production that leaked — a bare key
   * naming a value directly — had no fixture to be exercised with at all, which is how {@code
   * {password}} printing a password survived a suite pointed straight at it, per an adversarial
   * review's threat model.
   *
   * <p><b>@edgeCase</b> {@code newline-scalar} is the one fixture here carrying no sentinel. Its
   * value is not secret and is meant to be shown; what must not survive is its raw line break, and
   * a containment oracle cannot say that. The escaping is pinned by {@code TemplateParserTest}.
   */
  public static Map<String, Object> templateValues(String name, String sentinel) {
    return switch (name == null ? "card" : name) {
      case "card" -> Map.of("card", new Card("4111", sentinel));
      case "user" -> Map.of("user", new Credentials("ada", sentinel));
      case "order" -> Map.of("order", new Order("order-42", new Card("4000", sentinel)));
      case "deep" ->
          Map.of("a", new DepthOne(new DepthTwo(new DepthThree(new DepthFour(sentinel)))));
      case "unicode" -> Map.of("café", new Unicode("plain", sentinel));
      case "wide" -> Map.of("wide", new Wide("1", "2", "3", "4", "5", sentinel));
      case "chain" -> Map.of("chain", chain(sentinel));
      case "password-scalar" -> Map.of("password", sentinel);
      case "jwt-scalar" -> Map.of("value", jwt(sentinel));
      case "newline-scalar" -> Map.of("comment", "note" + (char) 0x000a + "## forged");
      default -> throw new IllegalArgumentException("unknown template fixture: " + name);
    };
  }

  /**
   * A JWT whose payload segment is the sentinel, so the value axis has a shape to recognise and the
   * containment oracle still knows which bytes must not appear.
   *
   * <p><b>@llmNote</b> The key holding it is {@code value}, deliberately a name no deny-list knows.
   * Under {@code token} the name axis would answer first and the case would prove nothing about the
   * shape — which is the entire reason the second axis exists.
   */
  private static String jwt(String sentinel) {
    return "eyJhbGciOiJIUzI1NiJ9." + sentinel + ".c2lnbmF0dXJl";
  }

  /** The dogfood shape: a payment card whose verification code is annotated out of every output. */
  public record Card(String number, @NotTraced String cvv) {}

  /** A card one level down, so a template can name a redacted segment mid-path. */
  public record Order(String id, Card card) {}

  /** A bean whose property names the deny-list knows, with no annotation involved. */
  public record Credentials(String name, String password) {

    /** The deny-list matches {@code secret} by name, exactly as it matches {@code password}. */
    public String secret() {
      return password;
    }
  }

  /** Four records deep, so {@code {a.b.c.d.secret}} names a redacted leaf and nothing shorter. */
  public record DepthOne(DepthTwo b) {}

  /** The second level of the {@code deep} template fixture. */
  public record DepthTwo(DepthThree c) {}

  /** The third level of the {@code deep} template fixture. */
  public record DepthThree(DepthFour d) {}

  /** The redacted leaf of the {@code deep} template fixture. */
  public record DepthFour(@NotTraced String secret) {}

  /** Identifier segments outside ASCII, so a path grammar cannot assume {@code [A-Za-z_]}. */
  public record Unicode(String naïve, @NotTraced String secret) {}

  /**
   * A record whose redacted component sits past the renderer's five-field cap, so the safe
   * rendering truncates the component away and carries no redaction marker at all.
   *
   * <p><b>@edgeCase</b> This is the shape that made {@code fuzzTemplate} fail on 2026-09-02. A
   * resolver that reads "no marker in the safe form" as "nothing is hidden, the value's own {@code
   * toString()} may stand" prints every component here, including this one. Absence of the marker
   * means "nothing is hidden" and "the renderer did not look" alike.
   */
  public record Wide(
      String one, String two, String three, String four, String five, @NotTraced String six) {}

  /** One link of a chain longer than the renderer's depth cap. */
  public record Link(Object next) {}

  /**
   * A chain nested deeper than the renderer's depth cap, with a redacted leaf at the bottom: the
   * second entrance to the same leak class as {@link Wide}. The walk stops at the cap and never
   * reaches the {@code @NotTraced} component, so the safe rendering carries no marker — while the
   * chain's own {@code toString()} prints the whole thing.
   */
  private static Object chain(String sentinel) {
    Object link = new Card("4111", sentinel);
    for (var i = 0; i < 40; i++) {
      link = new Link(link);
    }
    return link;
  }
}
