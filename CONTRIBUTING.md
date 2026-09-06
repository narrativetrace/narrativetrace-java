# Contributing

This project is **source-open, development-closed**. The source is published so
you can read it, audit it and fix what bites you — not because the roadmap is
open. Issues are welcome and read; small, focused patches are welcome. Review
capacity is one person's, so a pull request may wait, and a large one may be
declined on scope alone rather than on quality. Saying that up front is better
than a queue that never moves. Before a large change, open an issue first: if
the answer is "not this", you will get it before you have written the code.

## Licensing of contributions

Every contribution is accepted **only** under an inbound Apache-2.0 licence
grant to Empower Agile — including contributions to files that ship under the
Business Source License. By submitting a patch you licence it to Empower Agile
under Apache-2.0, and you confirm you have the right to do so.

That is asymmetric, deliberately, so here is why. The project ships three
licence categories — Apache-2.0 for the API, BSL 1.1 for the runtime, commercial
for the Pro tier — and code moves between them as the tier line is redrawn. An
inbound grant narrower than the outbound licence would make a contributed line
unmovable, and a maintainer who cannot relicense their own tree cannot correct a
tier boundary later. If that is not a trade you want to make, an issue with a
reproduction is a contribution too, and it carries no such condition.

## Sign-off

Every commit must carry a `Signed-off-by` line certifying the
[Developer Certificate of Origin](https://developercertificate.org/):

```
git commit -s -m "fix: ..."
```

The DCO is a statement about provenance — that you wrote the patch, or have the
right to submit it. It is not a copyright assignment: you keep your copyright,
and the grant above is a licence, not a transfer.

## Before you open a pull request

Run the gate CI runs — `./gradlew check`. It is the same command, and a green
local run is the fastest way through review.
