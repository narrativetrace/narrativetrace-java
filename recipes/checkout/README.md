# The checkout every recipe runs

This project **is** [`documentation/recipes/`](../../documentation/recipes/) — one checkout,
five pages, each embedding this project's sources and the output of a real run of them
(`snippetCheck`/`snippetSync` in the root `build.gradle.kts`, the mechanism
[`documentation/sixty-seconds.md`](../../documentation/sixty-seconds.md) already uses). Nothing on
a recipe page is typed output: rule 8, docs as tests.

This project is **not published** — it exists to be read and run, not depended on.

## What is here

| Source | Role |
|---|---|
| `OrderService` + `DefaultOrderService` | The checkout: reserve the stock, charge the card, save the order — and release the reservation when the charge is declined |
| `OrderServiceWithoutRelease` | The same flow with no release path. Two pages need that code to run: the bug before its fix, and the refactoring that drops the call |
| `InventoryService`, `PaymentService`, `OrderRepository` | The three collaborators, each an interface so the JDK proxy can wrap it |
| `InMemoryInventory`, `CardPaymentService`, `InMemoryOrderRepository` | Stubs with no database and no network. `CardPaymentService` declines the one card gateways document as declining |
| `src/main/resources/logback.xml` | The logging configuration the pages embed and a reader copies |
| `src/test/resources/logback-test.xml` | The same configuration plus a file appender a Gradle task names, so one scenario's log lines land in one file a page can embed |

The scenario code carries no guard clauses, postconditions or class invariants, unlike the
runtime's own modules: a page's listing has to read as the flow it describes, and a reader copies
it into their own project, where their own contracts belong.

## Two kinds of test

- **A recipe test** (`*RecipeTest`) runs in the ordinary suite and writes what its page embeds.
- **A fixture** (`*Fixture`) is a `@Test` run *by* another test through the JUnit Platform
  launcher, in process, because what it does is fail, or be approved against an earlier shape.
  `tasks.test` excludes it by name — running it directly would fail this project's build for doing
  exactly what it exists to do.

## Determinism

Every page's embedded output has to be byte-identical between runs or `snippetCheck` fails:

- each scenario adopts a fixed W3C traceparent, so the trace name in the log lines and the trace
  header is the same phrase every run (a real run generates a random trace id; your own code never
  adopts a constant);
- `logback.xml`'s pattern carries no timestamp, and says so;
- the value-free `.nt` structural trace carries no timings at all;
- a block whose timings do vary is embedded with `mask=duration`, which compares `— 23ms` against
  `— Nms` on both sides without rewriting either.
