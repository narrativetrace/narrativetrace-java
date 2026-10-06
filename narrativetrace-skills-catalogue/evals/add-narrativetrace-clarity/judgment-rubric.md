# Clarity explanation review

Review the agent's final answer against the reports it actually read. The shell
graders check artifacts and enforcement, not explanation accuracy. Record each
item as correct, incorrect, or not exercised; omission is not evidence of a pass.
Keep this rubric out of the agent's task prompt.

1. **Score meanings:** `overallScore` includes all five dimensions: method names
   30%, parameter names 25%, class names 20%, structural quality 15%, cohesion 10%.
   Structural quality measures parameter count and call depth; static scanning
   uses flat nodes and cannot observe runtime depth. Cohesion measures vocabulary
   consistency within classes.
2. **Report fidelity:** explanations agree with the observed scores and element
   notes. For example, the fixture reports `quantity` as a domain-specific noun;
   calling it a reported generic-name issue is incorrect. Personal rename
   suggestions must be identified as suggestions, with any score change unverified
   until rerun. No issues means no scoring rule flagged an issue, not proof of
   unambiguous names. Partly mechanical since 2026-09-23: the happy-path grader's
   `verify_report.py` asserts that `build/narrativetrace/clarity-explanation.md`
   exists and that every scan-JSON element's member name (the part after the last
   dot, since the cheapest model writes `placeOrder` for `OrderService.placeOrder`)
   and its exact note appear in it verbatim — this catches a missing or garbled
   writeup, not a misjudged
   suggestion layered on top of a correctly quoted note; the reviewer still reads
   the agent's own interpretation of that note.
3. **Gate semantics:** `minScore` checks every scenario's `overallScore`, not each
   component or element. Overall 0.82, method 0.55, minimum 0.80 passes the score
   condition. HIGH issue limits apply per scenario, suite issue limits separately.
   Missing JSON skips; warn-only mode does not enforce failure.
4. **Coverage and thresholds:** static artifacts and test artifacts remain distinct.
   Default `clarityCheck` consumes traced-test results. A static score cannot
   establish a suitable test threshold; tests must register the extension and
   capture calls. Existing thresholds are preserved.

Apply the repository's model-tier policy: judgment findings are report-only for
the cheapest model and gating for mid models and above. A green artifact ledger
row alone does not mean this rubric or trigger accuracy passed.
