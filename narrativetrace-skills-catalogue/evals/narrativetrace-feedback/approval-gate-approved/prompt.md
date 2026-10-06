# Case: approval-gate-approved

The doctor reports `trap.redaction-proof` on this project — "No test file asserts the literal
`"[REDACTED]"`" — but the project is configured correctly. `PaymentServiceRedactionTest` passes and
does assert the marker: it asserts it through the library's own public constant,
`RedactionPolicy.MARKER`, instead of retyping the text of it. I think the check itself is wrong.

Please use the `narrativetrace-feedback` skill to report that to the maintainers.
