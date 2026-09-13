---
name: doctor
description: "Diagnoses a NarrativeTrace Java install and configuration. Use when nothing is being traced, no build/narrativetrace output appears, the JUnit 5 extension never seems to run, parameter names render as arg0/arg1, or you are not sure NarrativeTrace is wired up correctly. Checks the JDK and JUnit Jupiter versions, the launcher on testRuntimeOnly, narrativetrace.output, whether NarrativeTraceExtension is actually registered, whether any sink receives what is traced, whether -parameters degraded parameter names, whether redaction is proven in a test, and stale .received.nt approval files. Read-only — makes no changes. Say 'check my narrativetrace setup', 'is narrativetrace broken', or 'why isn't anything being traced' to invoke it."
when_to_use: "Non-obvious triggers: a build that traces nothing without any visible error; parameter names printed as arg0/arg1; a stale .received.nt left after an approval mismatch."
allowed-tools: ./gradlew, git, find
---

# narrativetrace-doctor

## 1. Run the doctor and read the report

```bash
./gradlew narrativetraceDoctor
```

**verify:** the JSON report at build/narrativetrace/doctor-report.json is well-formed, naming all eleven findings

**failure:** the task fails with "Task 'narrativetraceDoctor' not found" → the ai.narrativetrace Gradle plugin isn't applied to this project → add id("ai.narrativetrace") to the plugins block, or run the standalone narrativetrace-cli launcher instead

## 2. Prove redaction in a test

```java
@Test
void redactsTheSensitiveParameter() {
  var rendered = renderCallWith(deniedParameterName, secretValue);
  assertThat(rendered).doesNotContain(secretValue);
  assertThat(rendered).contains("[REDACTED]");
  assertThat(rendered).contains(neighboringNonSensitiveValue);
}
```

**verify:** the report at build/narrativetrace/doctor-report.json names the trap.redaction-proof check

**failure:** @NotTraced is imported but the test still fails this check → the annotation was imported as a reminder and never actually applied to the field or parameter → annotate the sensitive field or parameter itself — an import alone redacts nothing (trap.unused-not-traced-import)

## 3. Read the rendered trace before asserting

```bash
find build/narrativetrace -name "*.md"
```

**verify:** the newest file this lists under build/narrativetrace was actually opened and read before writing any assertion against it

## 4. Diff the structural trace on approval, not just values

**Flagged:** unstudied — eval cell pending

```bash
git status --short src/test/narratives
```

**verify:** any .received.nt this lists sitting beside a .approved.nt has been diffed against that baseline before the run is treated as clean

## Always

- Run the doctor this session before reporting a finding (a stale report from an earlier session can no longer be true)

## Never

- Never edit, generate, or delete a file (the doctor is read-only by design — generation is a separate, later skill)
- Never claim a finding passed without having run the doctor this session (the report is the only source of truth, not memory of a past run)

