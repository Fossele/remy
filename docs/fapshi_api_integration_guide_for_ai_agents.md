# Fapshi API Integration Guide (Agent Reference)

This document provides exact technical specifications, endpoints, payloads, state transitions, and operational constraints for integrating **Fapshi** (Cameroon Mobile Money payment gateway for MTN MoMo and Orange Money).

---

## 1. Environments & Authentication

### Base URLs
* **Sandbox (Testing):** `https://sandbox.fapshi.com`
* **Live (Production):** `https://live.fapshi.com`

### Required Request Headers
Every authenticated request must include both headers:
```http
Content-Type: application/json
apiuser: <FAPSHI_API_USER>
apikey: <FAPSHI_API_KEY>
```

### Critical Service Separation Rule
* Fapshi separates **Collection Services** and **Payout Services** into distinct services, each with its own `apiuser`, `apikey`, and balance.
* Funds collected via a Collection Service **do not** automatically populate a Payout Service balance. Transfers between services or payout wallet top-ups happen via the Fapshi dashboard/app, not via API.
* Never hardcode credentials; load `FAPSHI_BASE_URL`, `FAPSHI_COLLECTION_API_USER`, `FAPSHI_COLLECTION_API_KEY`, `FAPSHI_WEBHOOK_SECRET`, and (if enabled) `FAPSHI_PAYOUT_API_USER` / `FAPSHI_PAYOUT_API_KEY` from environment variables.

---

## 2. Core Constraints & Business Rules

1. **Currency:** `XAF` (Central African CFA franc). Amounts are integers (no decimals).
2. **Minimum Collection Amount:** `100` XAF.
3. **Payout Limits:** Individual API payouts must be between `100` XAF and `500,000` XAF per request.
4. **Activation Gates:**
   * `POST /initiate-pay` works immediately in sandbox and live (once account KYC is verified).
   * `POST /direct-pay` and `POST /payout` require sandbox integration testing and explicit approval from Fapshi support before live activation.
5. **No Dedicated Refund Endpoint:** Refunds are executed by initiating an outbound payout (`POST /payout`) back to the customer's phone number.
6. **Link Expiration:** Generated payment links expire automatically after **24 hours** unless expired early via `POST /expire-pay`.

---

## 3. Collection Endpoints (Incoming Payments)

### A. Initiate Pay (Hosted Checkout Redirect)
Generates a Fapshi-hosted payment URL where the user selects MTN MoMo or Orange Money and completes payment.

* **Endpoint:** `POST /initiate-pay`
* **Request Body:**
```json
{
  "amount": 5000,
  "email": "member@example.com",
  "redirectUrl": "https://yourapp.com/payments/callback",
  "userId": "usr_12345",
  "externalId": "contrib_98765",
  "message": "Neighborhood Road Repair Fund",
  "webhook": "https://yourapp.com/api/webhooks/fapshi"
}
```
* **Field Rules:**
  * `amount` (integer, **required**): Amount in XAF ($\ge 100$).
  * `email` (string, optional): Payer email for receipt.
  * `redirectUrl` (string, optional): URL to redirect user after payment attempt.
  * `userId` (string, optional): Internal user identifier (`[a-zA-Z0-9-_]`).
  * `externalId` (string, optional): Your unique internal transaction/contribution ID (`[a-zA-Z0-9-_]`). Use this for reconciliation.
  * `message` (string, optional): Description shown on checkout.
  * `webhook` (string, optional): Overrides the default webhook URL configured on the Fapshi dashboard.
* **Success Response (`200 OK`):**
```json
{
  "message": "Payment link generated",
  "link": "https://checkout.fapshi.com/pay/...",
  "transId": "a1B2c3D4e5",
  "dateInitiated": "2026-11-24T12:00:00.000Z"
}
```

---

### B. Direct Pay (In-App Mobile Money Prompt)
Triggers the USSD/push approval prompt directly on the user's phone without leaving your app. *(Requires live approval from Fapshi support).*

* **Endpoint:** `POST /direct-pay`
* **Request Body:**
```json
{
  "amount": 5000,
  "phone": "670000000",
  "medium": "mobile money",
  "name": "Jane Doe",
  "email": "jane@example.com",
  "userId": "usr_12345",
  "externalId": "contrib_98765",
  "message": "Neighborhood Road Repair Fund",
  "webhook": "https://yourapp.com/api/webhooks/fapshi"
}
```
* **Field Rules:**
  * `amount` (integer, **required**): Amount in XAF ($\ge 100$).
  * `phone` (string, **required**): 9-digit Cameroon phone number without `+237` (e.g., `67XXXXXXX` or `69XXXXXXX`).
  * `medium` (string, optional): `"mobile money"` (MTN) or `"orange money"` (Orange). If omitted, Fapshi infers from prefix.
  * `name`, `email`, `userId`, `externalId`, `message`, `webhook`: Optional metadata and callback overrides.
* **Success Response (`200 OK`):**
```json
{
  "message": "Request initiated",
  "transId": "a1B2c3D4e5",
  "dateInitiated": "2026-11-24T12:00:00.000Z"
}
```

---

### C. Check Payment Status
Fetches the authoritative state of a transaction. Always call this to reconcile pending transactions or verify webhook payloads if needed.

* **Endpoint:** `GET /payment-status/{transId}`
* **Success Response (`200 OK`):**
```json
{
  "transId": "a1B2c3D4e5",
  "status": "SUCCESSFUL",
  "medium": "mobile money",
  "serviceName": "Neighborhood Funds",
  "amount": 5000,
  "revenue": 4850,
  "payerName": "Jane Doe",
  "email": "jane@example.com",
  "redirectUrl": "https://yourapp.com/payments/callback",
  "externalId": "contrib_98765",
  "userId": "usr_12345",
  "webhook": "https://yourapp.com/api/webhooks/fapshi",
  "financialTransId": "1928374650",
  "dateInitiated": "2026-11-24T12:00:00.000Z",
  "dateConfirmed": "2026-11-24T12:01:15.000Z"
}
```
* **Note on `amount` vs. `revenue`:** `amount` is the gross amount paid by the user; `revenue` is the net amount credited after Fapshi's collection fee (typically 3%).

---

### D. Search Transactions by `externalId` or `userId`
* **By External ID:** `GET /transaction/{externalId}` (returns an array of matching transaction objects).
* **By User ID:** `GET /search?userId={userId}` (or query params `status`, `medium`, `start`, `end`, `amt`, `limit`, `sort`).

---

### E. Expire a Payment Link
Invalidates an unpaid checkout link immediately so the user can no longer pay against it.

* **Endpoint:** `POST /expire-pay`
* **Request Body:**
```json
{
  "transId": "a1B2c3D4e5"
}
```

---

## 4. Payout Endpoints (Outgoing Disbursements)

> **Warning:** Only integrate live payouts after verifying that your Fapshi account has an approved Payout Service funded with sufficient balance.

### A. Check Service Balance
* **Endpoint:** `GET /balance`
* **Headers:** Must use the **Payout Service** `apiuser` and `apikey`.
* **Success Response (`200 OK`):**
```json
{
  "service": "Neighborhood Payouts",
  "balance": 150000,
  "currency": "XAF"
}
```

### B. Initiate Payout
Sends funds from your Payout Service balance to an MTN MoMo or Orange Money wallet.

* **Endpoint:** `POST /payout`
* **Headers:** Must use the **Payout Service** `apiuser` and `apikey`.
* **Request Body:**
```json
{
  "amount": 25000,
  "phone": "670000000",
  "medium": "mobile money",
  "name": "John Contractor",
  "email": "contractor@example.com",
  "userId": "admin_01",
  "externalId": "withdrawal_4421",
  "message": "Gravel delivery payout"
}
```
* **Validation Rules:**
  * `amount` must be between `100` and `500000` XAF. If an approved withdrawal exceeds `500,000` XAF, split it into multiple payouts or execute manually with an external receipt.
  * Track the returned `transId` and poll `GET /payment-status/{transId}` (or listen to the payout service webhook) before marking the withdrawal as settled in your ledger.

---

## 5. Transaction Lifecycle & Status Mapping

Fapshi transactions use five statuses:

| Fapshi Status | Meaning | Recommended Internal Mapping |
| :--- | :--- | :--- |
| `CREATED` | Checkout link created; user has not yet initiated payment. | `PENDING` |
| `PENDING` | Mobile Money prompt sent or payment in progress. | `PENDING` |
| `SUCCESSFUL` | Funds confirmed by operator. Final state. | `CONFIRMED` (credit ledger) |
| `FAILED` | Declined, insufficient user funds, or network error. Final state. | `FAILED` |
| `EXPIRED` | Link expired (after 24h or via `/expire-pay`). Final state. | `FAILED` / `EXPIRED` |

---

## 6. Webhooks & Security Verification

Fapshi sends an HTTP `POST` request to your webhook URL whenever a transaction transitions to `SUCCESSFUL`, `FAILED`, or `EXPIRED`.

* **Webhook Payload:** Matches the exact JSON structure of `GET /payment-status/{transId}`.
* **Authenticity Verification:**
  1. Configure a Webhook Secret on your Fapshi service dashboard and store it in `FAPSHI_WEBHOOK_SECRET`.
  2. Inspect the incoming `x-wh-secret` request header and compare it against `FAPSHI_WEBHOOK_SECRET` using a **constant-time string comparison** (`crypto.timingSafeEqual` / `hmac.compare_digest`). Reject with `401 Unauthorized` if missing or mismatched.
  3. Optionally cross-verify high-value transactions by calling `GET /payment-status/{transId}` directly before crediting your ledger.
* **Idempotency & Deduplication:**
  * Always treat `transId` (and `externalId`) as a unique idempotency key.
  * Return `200 OK` quickly once the event is safely persisted/processed so Fapshi does not retry unnecessarily.
  * Wrap ledger crediting in a database transaction that checks whether the contribution/payment is already `CONFIRMED` so duplicate callbacks or concurrent webhook + status-poll executions cannot double-credit a fund.

---

## 7. Sandbox Testing Reference

Use these phone numbers in `https://sandbox.fapshi.com` (`POST /direct-pay` or on the sandbox checkout page) to simulate specific outcomes:

| Operator | Simulated Outcome | Test Phone Numbers |
| :--- | :--- | :--- |
| **MTN MoMo** | `SUCCESSFUL` | `670000000`, `670000001`, `670000002` |
| **MTN MoMo** | `FAILED` | `670000003`, `670000004`, `670000005` |
| **Orange Money** | `SUCCESSFUL` | `690000000`, `690000001`, `690000002` |
| **Orange Money** | `FAILED` | `690000003`, `690000004`, `690000005` |

---

## 8. Agent Implementation Checklist

When writing code that integrates Fapshi, enforce the following invariants:

- [ ] **Provider Boundary:** Wrap Fapshi HTTP calls inside a dedicated adapter implementing a generic `PaymentProvider` interface (`initiateCollection`, `getPaymentStatus`, `verifyWebhook`, and optional `initiatePayout`). Never leak Fapshi-specific payloads into domain services or database models.
- [ ] **Two-Step Record Creation:** Create your internal `Contribution` (`PENDING`) and generate a unique `externalId` *before* calling `POST /initiate-pay` or `POST /direct-pay`. Save Fapshi's returned `transId` immediately upon response.
- [ ] **Append-Only Ledger:** Never mutate a fund's balance column directly. Only append a credit entry when a payment reaches `SUCCESSFUL` (`CONFIRMED`), and only append a debit entry when a withdrawal payout is confirmed `PAID`.
- [ ] **Reconciliation Worker:** Implement a background check that polls `GET /payment-status/{transId}` for any `PENDING` payments older than a few minutes in case a webhook was missed or delayed.
- [ ] **Error Handling:** Handle non-200 HTTP responses (`400` validation errors, `401` invalid `apiuser`/`apikey`, `404` unknown `transId`, `5xx` gateway errors) gracefully without marking a payment as `CONFIRMED`.