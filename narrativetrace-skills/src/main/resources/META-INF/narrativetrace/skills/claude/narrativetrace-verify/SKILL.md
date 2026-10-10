---
name: narrativetrace-verify
description: "Verifies a change in a Java project by reading what the code actually did before saying it is done. Use after the tests are green and before reporting a change that crosses collaborators, branches, retries, runs async, carries state between calls, or touches code not written in this session — and skip it, saying why, for a pure function or a one-class edit. Writes the intent down first, runs the smallest real path with NarrativeTrace on, reads the value-free structural trace against the intent, opens values only on the span that looks wrong, fixes and re-reads, then pins the flow as an approval baseline behind your yes and reports what the trace showed, citing span ids. Say 'verify this change with the trace', 'check what the code actually did', 'did the flow do what I meant', or 'pin this flow as a baseline' to invoke it."
when_to_use: "Non-obvious triggers: the suite is green but the change touched more call sites than it added; a notification, payment or retry path changed; a .received.nt appeared after a test run; you are about to write 'tests pass' as the whole report."
---

# narrativetrace-verify

## 1. Decide whether to trace, and say so

**verify:** before anything runs, the reply says which it is: 'tracing: <the reason>' when the change crosses two or more collaborators over a boundary, branches, retries, runs async or concurrently, carries state between calls, touched more call sites than it added, or includes code not written in this session; or 'skipping narrativetrace-verify: <a pure function | a one-class edit with no collaborator | a flow one test already walks end to end>' — a skip ends the skill here, and that sentence is the report. A whole flow's .nt is dozens of lines: cheap where the path is not obvious, waste where it is

## 2. Write the intent down before running anything

**verify:** three to six lines in the reply, under the word Intent, written before the first traced run: which collaborators the change touches, in which order, under which branch, how many times — the oracle the trace is read against, never edited after the run

## 3. Run the smallest real path with tracing on

**when:** the project already has a test that drives the changed path through its real collaborators, run that one — again, if it already ran: a trace from a run made before the Intent was written does not count; otherwise write the smallest one, as below

<!-- snippet: sixty-seconds/src/test/java/com/example/orders/PlaceOrderFlowTest.java -->
```java
package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// The smallest test that drives the real path: the collaborators the change touched, wired as
// production wires them, each wrapped with the test's NarrativeContext. After the test, the
// extension writes the shape to build/narrativetrace/structural/<TestClass>/<scenario>.nt and the
// values to build/narrativetrace/traces/<TestClass>/<scenario>.md.
@ExtendWith(NarrativeTraceExtension.class)
class PlaceOrderFlowTest {

  @Test
  void customer_places_an_order(NarrativeContext context) {
    OrderService orders =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    var orderId = orders.placeOrder("C-1234", "SKU-KB", 2);

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
  }
}
```
<!-- /snippet -->

**verify:** ./gradlew test --tests <the smallest test that drives the real path>

**failure:** no .nt for the test appears under build/narrativetrace/structural → the JUnit 5 extension is not registered, or the collaborators on the path are not wrapped with the test's NarrativeContext → run narrativetrace-doctor and apply its fix, then run the test again

## 4. Read the structural trace first, against the intent

```bash
find build/narrativetrace/structural -name "*.nt"
```

**verify:** the .nt of the test just run was opened and read whole before any value was looked at, and the reply walks it against the intent — calls, order, branch, multiplicity — naming every match and every mismatch by its span id (#2.1); the shapes below are the checklist

## 5. Open values on the span that looks wrong, and only there

**when:** only when the structural read named a span that does not match the intent; otherwise go on to the pin

```bash
find build/narrativetrace/traces -name "*.md"
```

**verify:** only the flagged span was read in the .md narrative, found by the id the .nt gave it — not the whole file; a [REDACTED] value stays redacted

## 6. Fix, re-run, read again

**when:** only when the structural read named a span that does not match the intent; otherwise go on to the pin

```bash
./gradlew test --tests <the smallest test that drives the real path>
```

**verify:** the same test ran again after the fix and its new .nt was read whole: the span that was wrong now matches the intent, and a fix that changed the shape was read again from the structural read

## 7. Turn approval mode on

**when:** if approval mode is off — the build has no approval.set(true), or the doctor's config.approval-mode finding fails; when it is already on, go straight to the run

```kotlin
// build.gradle.kts, with the ai.narrativetrace plugin applied
narrativeTrace {
    approval.set(true)
}
```

**verify:** build.gradle.kts carries the block above, and .gitignore carries the line src/test/narratives/**/*.received.nt so a review copy is never committed

**failure:** the build fails with an unresolved reference to narrativeTrace or approval → the ai.narrativetrace Gradle plugin isn't applied to this project, so neither the switch nor the approveNarratives task exists → add id("ai.narrativetrace") to the plugins block and build again

## 8. Run the suite in approval mode and show every .received.nt

```bash
./gradlew test
find src/test/narratives -name "*.received.nt"
```

**verify:** approval mode compares every traced test, not only the one this skill ran, so the run that writes the review copies is the whole suite; the first run of a test with no baseline fails on purpose — that failure is what writes its review copy — and the whole text of each .received.nt that run wrote is in the reply, not a summary of it — it holds names and shape and no value, which is why it is safe to commit once approved; where a .approved.nt already existed, the reply also names what the delta changed, by span id, in the program's own words, and whether it was meant

## 9. Ask once whether to pin it, then stop the turn

**verify:** the reply ends with the question and nothing after it — the answer is the user's next message, never something assumed in this one. Do not run approveNarratives before the user says yes — promoting is the pinning. And everything else — the report, every caveat — goes before the question; the question is the reply's last line.

## 10. Promote what was shown, and nothing else

```bash
./gradlew approveNarratives
```

**verify:** each .approved.nt now holds exactly the text that was shown and no .received.nt is left beside it — git status --short src/test/narratives lists the new or changed .approved.nt files and nothing else; those are what get committed — and ./gradlew test passes: the suite is green again after the promotion

## 11. Report what the trace showed

**verify:** two sentences on what the trace showed, every claim citing the span id it rests on — a claim without an id is not a claim, and only ids in the .nt that was read count — with the .nt attached or quoted; 'tests pass' alone is not the report

## Which flavour answers which question

| flavour | where | carries | answers |
|---|---|---|---|
| structural `.nt` | `build/narrativetrace/structural/<TestClass>/<scenario>.nt` | shape only: calls, order, nesting, parameter names, multiplicity, span ids — dozens of lines for a whole flow | did the flow do what I meant? |
| Markdown narrative | `build/narrativetrace/traces/<TestClass>/<scenario>.md` | values (redacted), outcomes, durations | what value crossed this boundary? — read one span, by id |
| indented text | `IndentedTextRenderer`, the console | the same values as plain text | the same question, in a console or a failure message |
| sequence diagram | `build/narrativetrace/diagrams/<TestClass>/<scenario>.mmd` | who called whom, in order, across threads | ordering across components and threads |
| approval delta | the failing test's message: `.received.nt` against `.approved.nt` | what changed in the shape, citing both sides' ids | is this change intended? |
| prose | `ProseRenderer` | narration for a person | explaining the flow to the user — never read it to check the code |

Use the cheapest flavour that answers the question, and look at values only where the
shape says to look. A span id (`#1`, `#1.3`, `#1.3.2`) is the span's position in the tree
and the same in every flavour: find in the `.md` the span the `.nt` flagged by its id.
Redaction stays on — the deny-list and `[REDACTED]` are never turned off to see more; a
redacted value that matters is reasoned about by its parameter name and the shape around
it.

## Shapes that mean something went wrong

- a call made twice that the intent makes once
- a call before its precondition — a notification before the payment that it announces
- a branch never taken that the intent takes
- a retry that masks a failure
- a side effect inside a loop
- a swallowed exception: a thrown outcome `!!` under a call that returned normally
- a cleanup that never ran
- a value crossing a boundary that should have been redacted

## Always

- Cite a span id for every claim about the trace (an id points at one span in every flavour, so a reviewer can check the claim; a claim without one cannot be checked)
- Use the cheapest flavour that answers the question (the structural trace first and a value on one span only is what keeps the common case near zero tokens)
- Show the whole .received.nt before asking anything (the baseline becomes the contract every later change is held to, and a person can only approve what they have actually read)

## Never

- Never report a change as done on green tests alone once this skill decided to trace (the suite checks what someone thought to assert; the trace shows what the code did)
- Never read a trace against nothing (an intent written after the run bends to whatever happened — that is why it comes first)
- Never turn redaction off to see more (a redacted value that matters is reasoned about by its name and shape; turning redaction off puts the user's secrets in the transcript)
- Never promote a baseline in the turn that asked (approval is the user's next message — a yes assumed in the same turn is not one)
- Never edit the .received.nt after showing it (what was approved has to be what is promoted, so a changed run is rendered again and shown again)
- Never commit a .received.nt (it is the review copy; the committed contract is the .approved.nt)

