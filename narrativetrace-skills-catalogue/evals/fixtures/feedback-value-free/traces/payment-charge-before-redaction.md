---
type: trace
run: shiny mink ships
scenario: The auth token is redacted and the other arguments survive
entry_point: PaymentService.charge
duration_ms: 1.45
trace_id: 687ab14c09079495bf3e8fae067ab6f6
trace_name: lofty globe hints
method_count: 1
error_count: 0
---

<!--
Kept by hand from a run of this service BEFORE redaction was configured, so the call line below
still shows the token it was called with. Saved here rather than under build/ because build/ is
cleaned; nobody has got round to deleting it.
-->

## Trace: lofty globe hints — PaymentService.charge

**Scenario:** The auth token is redacted and the other arguments survive
**Duration:** 1.45ms | **Result:** PASSED

### Call Flow

- **PaymentService.charge**(customerId: `"C-1234"`, authToken: `"ghp_NTCANARY0001"`, amount: `"42.00"`) → `"PAY-C-1234"` — 1.45ms
