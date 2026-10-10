# existing-service-checkout

The narrativetrace-verify cases' fixture: `existing-service`'s billing domain grown into a checkout
flow, with NarrativeTrace already installed (plugin, core, proxy, the JUnit 5 extension,
`-parameters`) and resolved through the eval test repository like the feedback fixtures. Never
copied into a trial (the harness skips a fixture's root README).

- `DefaultCheckoutService.checkout` issues the invoice, authorizes the payment, calls every
  `CheckoutListener`, and only then settles — `CardSettlement.settle` is where
  `PaymentGateway.confirm` happens. `CheckoutListener`'s Javadoc says it is called "once a
  checkout's payment has gone through": the comment is wrong about the code, which is the trap. A
  receipt sent from a listener goes out BEFORE the payment is confirmed, and every test still
  passes. `CheckoutFlowTest` drives the path as `Checkout.compose` wires it, every collaborator
  traced, so the structural trace shows the order: `NotificationService.send` before
  `PaymentGateway.confirm`.
- `LateFees.feeFor` is a pure function with its own unit test — the skip case's subject.

Cases: `narrativetrace-verify/verify-unintended-interaction`, `happy-path`, `verify-skip`.
