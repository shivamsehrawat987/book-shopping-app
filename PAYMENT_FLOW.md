# Checkout And Simulated Payments

## Existing Architecture

- HTTP Basic authentication; email is the username. No JWT or session tokens.
- Controllers depend on service interfaces; implementations coordinate transactions.
- MapStruct 1.6.3 and Lombok processors are already configured.
- Existing book/user mapping, validation, repositories and tests remain in use.

## Scope Decisions

- Payments are simulated only. No gateway SDK, card processing or real charges.
- Currency is INR. Prices are read from the database, never from checkout input.
- Only a signed server-side simulation event may mark an order PAID.
- Late success after cancellation or expiry requires manual reconciliation.
- Available stock is reduced at reservation, not again at payment success.
- Network calls to a real gateway would require a separate durable payment-attempt/outbox design; the local simulator does not have distributed transaction risks.

## TODO

- [x] Audit entities, DTOs, MapStruct and Basic authentication.
- [x] Confirm simulated gateway, INR and manual late-payment reconciliation.
- [x] Add checkout DTOs, order mapper, ownership and inventory validation.
- [x] Reserve stock and snapshot prices transactionally; prevent duplicate checkout.
- [x] Add PaymentGateway interface and simulated implementation.
- [x] Verify raw webhook signatures and payment identity/amount/currency.
- [x] Persist processed event IDs and enforce idempotent state transitions.
- [x] Release reservations on failure and scheduled expiry.
- [x] Preserve the cart on failure; remove purchased quantities on success.
- [x] Test ownership, validation, duplicates, competing stock, failure and late events.
- [x] Document Swagger preparation and signed simulation examples.

## Intended Flow

1. Register/login with Basic credentials; create a cart and add existing books.
2. Checkout with shippingAddress, cartId and an Idempotency-Key header.
3. Lock the cart and stock rows, validate ownership/stock, snapshot prices, reserve inventory and create a PENDING order.
4. The simulated gateway returns a mock intent ID and mock client token. These cannot move money or confirm payment.
5. A trusted test driver sends a signed raw JSON webhook; no customer-facing success endpoint exists.
6. Verified success marks PAID and removes purchased quantities from the cart. Duplicate delivery does nothing.
7. Failure or expiry restores inventory exactly once. Late success is recorded for reconciliation, not fulfilled.

## Deployment Boundary

This remains a demo using in-memory H2. Real deployment requires persistent storage, schema migrations, HTTPS/CSRF decisions and an actual gateway adapter with verified provider signatures, retry/reconciliation jobs and durable payment attempts.

## Swagger Walkthrough

1. Start the app and open `http://localhost:8081/swagger-ui/index.html`.
2. Create books using an admin's Basic credentials if the catalog is empty.
3. Register a customer using `POST /api/auth/register`; Authorize with their email/password.
4. `GET /api/cart` creates/returns that customer's cart. Save its `id`.
5. `POST /api/cart/items` with `{"bookId":1,"quantity":2}` adds existing books. This does not reserve stock.
6. `POST /api/orders/checkout` with `{"shippingAddress":"12 Test Street, Mumbai","cartId":1}` and a unique `Idempotency-Key` header, for example `checkout-demo-1`.
7. Expect 201 with `orderId`, `totalAmount`, `currency`, `paymentIntentId`, `clientToken`, `expiresAt`, and `simulated:true`. The token is a mock marker, not a Stripe/Razorpay client secret.
8. Reusing the same key and payload while the order remains pending returns the same order and token without reserving twice. A different key for an already-pending cart returns 409. An old key cannot start a second payment after the order finishes or expires.
9. `GET /api/orders/{id}` returns the order to its owner only; other accounts receive 404. Order item prices remain purchase-time snapshots.
10. Send a local signed simulation event using the helper below. Fetch the order again to see PAID or CANCELLED.

All cart/order endpoints require Basic authentication. Only `POST /api/payments/webhook` bypasses Basic authentication; it instead requires a valid mock signature. Customer passwords and mock client tokens cannot authenticate webhook events.

## Local Webhook Simulation

Set `app.payment.mock.webhook-secret` in the ignored local secrets file to a private random value of at least 32 UTF-8 bytes and restart the app. No shared signing secret is committed. If it is absent/blank, the app generates an unknown ephemeral key: checkout still works but manual signed simulation requires configuring a known private key. Never send this key to a browser frontend.

The Python 3 standard-library helper prompts for the key without echoing it. Replace the intent and amount with values from checkout:

```text
python scripts/send_mock_payment.py mock_intent_1 4000 --event-id evt-demo-1
```

Here `4000` means INR 40.00. Repeat the command with the same event ID/data to verify duplicate delivery is harmless. To simulate a failure on a NEW pending checkout:

```text
python scripts/send_mock_payment.py mock_intent_2 4000 --result payment_failed
```

The raw event shape is:

```json
{"eventId":"evt-demo-1","type":"payment_intent.succeeded","paymentIntentId":"mock_intent_1","amountMinor":4000,"currency":"INR"}
```

Headers are `X-Mock-Timestamp` (Unix seconds) and `X-Mock-Signature` (hex HMAC-SHA256 of `timestamp + "." + exactRawJsonBody`). Signatures must be within five minutes of server time. Do not reformat the JSON after signing. The helper is deliberately localhost-only and is a trusted test-driver utility, not an API for frontend payment confirmation.

Verified events are acknowledged with 200 only AFTER their database transaction commits. Duplicate identical events also return 200. Invalid signatures/JSON return 400, mismatched amounts/currency or reused IDs with different data return 409, unknown intents return 404. A processing failure must not be acknowledged as success without durable persistence; a real gateway would retry.

## Lifecycle And Recovery

- PENDING -> PAID: retain the already-deducted reservation and remove purchased cart quantities.
- PENDING -> CANCELLED on payment failure: restore stock once, retain cart, allow checkout with a new key.
- PENDING -> EXPIRED after 15 minutes: restore stock once, retain cart. The scheduled scan runs every minute; temporary delays cannot cause a late success to fulfill an expired reservation because event processing checks expiry too.
- EXPIRED/CANCELLED -> REQUIRES_RECONCILIATION on later success: do not deduct inventory or clear the cart. This is visible in the order response; manual investigation is required. No real charge or refund exists in this simulator.
- Events received after PAID never downgrade the order or change inventory again.
- A simulated gateway exception returns 502 and rolls back the new order and all stock reservations, allowing retry with the same key. Real remote gateway timeouts need durable reconciliation, NOT blind database rollback/retry.
- Cart edits are rejected while checkout is PENDING. Cart/order/stock locks serialize competing checkout and payment operations; book locks are acquired in ID order.
- Dedicated processed-event records and payment state updates share a transaction, preventing partial event acknowledgement. Local H2 data is not durable across application restarts.

## Next Scope (Not Implemented)

- Persistent database and schema migrations; database-level verification on the eventual production engine.
- Real provider integration, durable payment attempts/outbox, refunds and automated reconciliation.
- Payment frontend SDK and provider-specific webhook contract/signatures.
- Full cart editing/removal endpoints, operational dashboards and reconciliation tooling.