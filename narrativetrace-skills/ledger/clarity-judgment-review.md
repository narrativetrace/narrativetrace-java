# Clarity explanation review — 2026-09-23

Model: `claude-haiku-4-5-20251001`, through the subscription CLI and `EvalRunner`.
Review criteria: [Clarity judgment rubric](../evals/add-narrativetrace-clarity/judgment-rubric.md).
The [run ledger](runs.jsonl) records artifact-grader results; this document records
source-grounded review of the model's explanations by the parent agent. Judgment
is report-only at this model tier.

## Findings and revisions

The earlier response incorrectly described structural scoring as naming conventions
and said every metric was gated. Source review confirms that structural quality
measures parameter count and call depth; `minScore` checks scenario `overallScore`.

Clarifying these facts improved the score-condition explanation, but the 07:15 UTC
happy-path response still asserted runtime depth was bounded from static output,
mischaracterized element notes, and suggested that running a scan alongside the
test gate enforced static scores. Its artifact check passed.

The 07:16 UTC recovery trial removed the output-disabling setting but ran only a
static scan before claiming repair. Its artifact grader correctly failed because
the test report was absent. The skill now routes test-output repairs to the JUnit
path, and its explanations use short bullets and request exact element notes.

## Final wording: happy-path repeats

Rendered Claude skill SHA-256:
`0be5ab51504e9033b3f4bd0484c764fc435ef499fe2735c16ded50512787ae12`.
Both trials loaded this revision through the Skill tool; neither saw this rubric.

| Run (UTC) | Artifact check | Explanation review |
|---|---|---|
| 07:19, trial 1 | Passed | Still described `clarityCheck` as scanning compiled classes, then contradicted that claim with a test-report caveat. Misclassified report notes and based a test threshold on a static score. |
| 07:20, trial 2 | Passed | Correctly distinguished test-gate input and overall versus component scores. Still called `quantity` generic despite its domain-specific report note and reused the static score as an assumed test score. |

Neither repeat explicitly explained structural scoring, so its correctness was not
exercised by these answers. Both failed the full explanation rubric. The instruction
changes do not establish reliable explanation accuracy, and a green promotion cell
must not be read as a judgment pass or a trigger-test pass.

## Final wording: output recovery

The 07:21 UTC recovery repeat passed its artifact grader: it produced fresh test
reports, preserved `minScore=0.50`, and the grader proved failure at a stricter
threshold before restoring the original policy. Its explanation correctly used
the observed test score of 0.89, but still called `quantity` generic against the
report's note. It also overstated the gate as catching any future naming regression;
only configured threshold violations cause failure.

## JUnit 4 follow-up

The later JUnit 4 revision has rendered Claude skill SHA-256
`1e4c90d00693d074d09d1c60b6b987cb48949ef1c974b70e454de4e14676b75b`.
One `deviation-junit4-class-rule` Haiku trial passed: the agent linked a class rule
to the existing per-test rule, generated test reports, and preserved JUnit 4 and
`minScore=0.50`. The grader reproduced the result and proved strict-threshold
failure. The final answer correctly described the missing aggregate collector and
reported the observed 0.89 overall score. It did not explain the scoring dimensions,
so this is evidence for JUnit 4 repair, not resolution of earlier interpretation findings.

## Remaining work

The next improvement should make explanation verification reproducible, especially
report-note fidelity and scan/test provenance. Repeatedly extending the prompt or
using a stronger model would not establish reliability on this required cheap model.
