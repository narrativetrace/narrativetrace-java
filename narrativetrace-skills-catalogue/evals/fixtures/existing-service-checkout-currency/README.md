# existing-service-checkout-currency

The narrativetrace-debug cases' fixture: the `existing-service-checkout` billing domain charged in
the card's own currency, with NarrativeTrace already installed (plugin, core, proxy, the JUnit 5
extension, `-parameters`) and resolved through the eval test repository like the verify fixture.
Never copied into a trial (the harness skips a fixture's root README).

- `DefaultCheckoutService.checkout` issues the invoice in euro, reads the card's currency from
  `CustomerDirectory`, converts with `CurrencyConverter`, authorizes the converted amount and
  settles. `RateTableConverter.convert` — "keeping the amount exact until the last step", says its
  Javadoc — rounds to whole francs BEFORE moving to cents: 45.99 EUR at 0.93 is charged 4300
  instead of 4277. That is the defect, and it is visible only as a value at one boundary:
  `#1.3 CurrencyConverter.convert(euroCents: 4599, currency: "CHF") → 4300`, with its child
  `#1.3.1 ExchangeRates.rateFor` returning the right `0.93`. The structural trace is identical
  before and after the fix.
- Every test passes as shipped: `RateTableConverterTest` only converts amounts that land on whole
  francs and pounds, and `CheckoutFlowTest` drives a euro card.
- A red run writes the `.md` narrative and the sequence diagram but no `.nt` (the `.nt` on disk is
  the last green one), so the failing reproduction's shape is read from its `.md`.

Cases: `narrativetrace-debug/debug-value-divergence`, `happy-path`.
