# Canonical JSON schemas

The cross-platform output contract, and the **golden source** the ports copy
byte-identically (`narrative-trace-python` and `narrative-trace-ts` each keep a
`schema/` mirror of this directory). Change the contract here and re-copy; a
port that edits its own copy stops being a port.

| File | Validates |
|---|---|
| `entry.schema.json` | one atomic trace event — the `.canonical.json` artifact is an array of these |
| `chapter.schema.json` | one service's whole contribution to a trace |
| `chapter-tree.schema.json` | the nested tree embedded in `nt.chapterTree`, and the per-test `.json` companion |

These are **test resources**, not shipped in any distribution: they exist so
schema drift fails a build instead of being discovered by a consumer.
`SchemaValidationTest` validates the exporters against them, and
`narrativetrace-security-tests` reads `chapter-tree.schema.json` from this path
rather than keeping a second copy that could drift.

## Licence

The schema files in this directory are licensed under the **Apache License,
Version 2.0** — not the BSL 1.1 that covers the runtime around them. The
canonical trace format is a standard other implementations are meant to target,
so it is licensed as one, whatever module the files happen to sit in. This
carve-out is stated in the repository-root `NOTICE`.

JSON has no comment syntax, so the schemas cannot carry the
`SPDX-License-Identifier: Apache-2.0` line every source file in an Apache
module carries. This file is that marker instead: the publish-time header stamp
does not touch them, and without this note their licence would be visible only
in `NOTICE`, a directory away from the files it applies to.
