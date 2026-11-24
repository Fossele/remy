# Neighborhood Messaging API - Frontend Integration

This guide describes how to connect a frontend to the neighborhood messaging backend.

## Configuration

```js
export const API_URL = "http://localhost:8080";
export const WS_URL = `${API_URL}/chatt`;
```

Send authenticated requests with:

```http
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json
```

## API helper

```js
async function api(path, { method = "GET", body, token } = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token && { Authorization: `Bearer ${token}` })
    },
    body: body ? JSON.stringify(body) : undefined
  });

  if (!response.ok) {
    throw new Error(await response.text());
  }

  return response.status === 204 ? null : response.json();
}
```

## Authentication

### Register

```http
POST /api/auth/register
```

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "displayName": "Alice"
}
```

```js
const registered = await api("/api/auth/register", {
  method: "POST",
  body: {
    username: "alice",
    email: "alice@example.com",
    password: "password123",
    displayName: "Alice"
  }
});
```

### Log in

```http
POST /api/auth/login
```

```json
{
  "username": "alice",
  "password": "password123"
}
```

```js
const login = await api("/api/auth/login", {
  method: "POST",
  body: {
    username: "alice",
    password: "password123"
  }
});

localStorage.setItem("accessToken", login.accessToken);
localStorage.setItem("refreshToken", login.refreshToken);

const token = login.accessToken;
```

### Refresh and logout

```js
const refreshed = await api("/api/auth/refresh", {
  method: "POST",
  body: { refreshToken }
});

await api("/api/auth/logout", {
  method: "POST",
  body: { refreshToken }
});
```

## Neighborhoods

### Create a neighborhood

```http
POST /api/neighborhoods
Authorization: Bearer ACCESS_TOKEN
```

The `location` is the shared neighborhood area, not a resident's private house address.

```json
{
  "name": "Bepanda",
  "location": "Douala III, Bepanda, near Bepanda Omnisports",
  "description": "Community for Bepanda residents"
}
```

```js
const neighborhood = await api("/api/neighborhoods", {
  method: "POST",
  token,
  body: {
    name: "Bepanda",
    location: "Douala III, Bepanda, near Bepanda Omnisports",
    description: "Community for Bepanda residents"
  }
});
```

The creator becomes a verified administrator.

### List neighborhoods

```http
GET /api/neighborhoods
Authorization: Bearer ACCESS_TOKEN
```

```js
const neighborhoods = await api("/api/neighborhoods", { token });
```

### Join a neighborhood

```http
POST /api/neighborhoods/{neighborhoodId}/join
Authorization: Bearer ACCESS_TOKEN
```

```js
await api(`/api/neighborhoods/${neighborhoodId}/join`, {
  method: "POST",
  token
});
```

Joining creates a `PENDING` membership. Only a verified administrator or moderator can verify it.

### Verify a member

```http
POST /api/neighborhoods/{neighborhoodId}/members/{memberId}/verify
Authorization: Bearer ACCESS_TOKEN
```

```js
await api(
  `/api/neighborhoods/${neighborhoodId}/members/${memberId}/verify`,
  { method: "POST", token }
);
```

## Community project funds

Only verified neighborhood members can view funds, contribute, or request a withdrawal. Verified moderators or administrators can create funds. All amounts are integer XAF.

### Create and list project funds

```js
const fund = await api(`/api/neighborhoods/${neighborhoodId}/funds`, {
  method: "POST",
  token,
  body: {
    name: "Road repair",
    description: "Gravel and drainage work",
    targetAmount: 500000
  }
});

const funds = await api(`/api/neighborhoods/${neighborhoodId}/funds`, { token });
```

`targetAmount` is optional. Fund responses include `contributedAmount`, `availableAmount`, and `reservedAmount`. `contributedAmount` is the gross amount members paid; `availableAmount` reflects confirmed net Fapshi revenue less paid withdrawals and reserved requests.

### Contribute through Fapshi

```js
const contribution = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/contributions`,
  {
    method: "POST",
    token,
    body: { amount: 5000 }
  }
);

window.location.assign(contribution.paymentLink);
```

The minimum contribution is 100 XAF. The response starts with status `PENDING`; the backend changes it after checking Fapshi's payment status or processing its verified webhook. The `netAmount` reflects the amount credited after Fapshi fees. Never collect Fapshi API credentials in the frontend. The backend webhook is `POST /api/webhooks/fapshi` and must be configured with the `x-wh-secret` secret on the server.

### Request a withdrawal

A verified member can request a withdrawal from available funds:

```js
const withdrawal = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals`,
  {
    method: "POST",
    token,
    body: {
      amount: 25000,
      purpose: "Gravel delivery for road repair",
      beneficiaryName: "Roadworks contractor",
      beneficiaryPhone: "670000000",
      beneficiaryMedium: "mobile money"
    }
  }
);
```

The phone must be a 9-digit Cameroon mobile number without `+237`. `beneficiaryMedium` is optional and may be `"mobile money"` or `"orange money"`. The amount is reserved while the request is pending. Every verified neighborhood administrator captured when the request is created must approve it; one rejection rejects the request. Moderators who are not administrators do not count as approvers.

Administrators submit their decision individually:

```js
const decision = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals/${withdrawal.id}/approvals`,
  {
    method: "POST",
    token,
    body: { approved: true }
  }
);
```

The response includes `requiredApprovals`, `approvalsReceived`, and the current `status`. A changed verified-admin roster cancels the request when it is next approved, completed, or paid out. Verified members can list requests with:

```js
const withdrawals = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals`,
  { token }
);
```

The full beneficiary phone is visible only to verified administrators included in the approval list; other members see a masked number. Listed administrators can retrieve payout details before approving:

```js
const payoutDetails = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals/${withdrawal.id}/payout-details`,
  { token }
);
```

### Initiate a Fapshi payout

After unanimous approval, a listed verified administrator can initiate the payout. The server checks the Fapshi payout-service balance before calling Fapshi:

```js
const payout = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals/${withdrawal.id}/payouts`,
  { method: "POST", token }
);
```

Fapshi payouts are 100–500,000 XAF per request. Status `PAYOUT_PENDING` means Fapshi accepted the request but it is not settled; `PAYOUT_INITIATING` means the API outcome is being reconciled. Poll the individual withdrawal endpoint:

```js
const currentWithdrawal = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals/${withdrawal.id}`,
  { token }
);
```

The backend also updates payout status from Fapshi webhooks and periodic reconciliation. A failed payout returns the withdrawal to `APPROVED`, allowing an administrator to retry. The ledger debit is recorded only after Fapshi confirms success, when the withdrawal becomes `PAID`.

Fapshi collection and payout services use separate balances and credentials. Contributions collected through Fapshi do not automatically fund payouts; top up/transfer the payout-service balance using the Fapshi dashboard or app. Live payouts require sandbox testing and Fapshi support approval.

### Record an external/manual withdrawal

For withdrawals over Fapshi's per-request limit or when paying outside Fapshi, an administrator can pay externally and record the receipt/reference:

```js
const completed = await api(
  `/api/neighborhoods/${neighborhoodId}/funds/${fund.id}/withdrawals/${withdrawal.id}/complete`,
  {
    method: "POST",
    token,
    body: { receiptReference: "transfer-reference-or-receipt-id" }
  }
);
```

This marks the withdrawal `MANUALLY_PAID` and records a debit in the ledger. The receipt reference is recorded for audit but the app does not verify the external transfer.

## Channels

### Create a channel

```http
POST /api/neighborhoods/{neighborhoodId}/channels
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "name": "Safety Alerts",
  "description": "Urgent neighborhood alerts",
  "category": "SAFETY"
}
```

```js
const channel = await api(
  `/api/neighborhoods/${neighborhoodId}/channels`,
  {
    method: "POST",
    token,
    body: {
      name: "Safety Alerts",
      description: "Urgent neighborhood alerts",
      category: "SAFETY"
    }
  }
);
```

Valid categories:

- `GENERAL`
- `SAFETY`
- `LOST_FOUND`
- `EMERGENCY`
- `EVENTS`
- `MARKETPLACE`

Only a verified administrator or moderator can create a channel.

### List channels

```http
GET /api/neighborhoods/{neighborhoodId}/channels
Authorization: Bearer ACCESS_TOKEN
```

```js
const channels = await api(
  `/api/neighborhoods/${neighborhoodId}/channels`,
  { token }
);
```

Only verified members can list or use a neighborhood channel.

## Message reporting

```http
POST /api/neighborhoods/reports
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "messageId": "8c4e27b7-5a51-4c8c-8238-cfb8dcf87528",
  "reason": "HARASSMENT",
  "details": "Repeated unwanted and threatening messages"
}
```

```js
await api("/api/neighborhoods/reports", {
  method: "POST",
  token,
  body: {
    messageId,
    reason: "HARASSMENT",
    details: "Repeated unwanted and threatening messages"
  }
});
```

## Message history

```http
GET /api/messages/{channelId}?start=START&end=END&size=50
Authorization: Bearer ACCESS_TOKEN
```

```js
const start = "2026-01-01T00:00:00Z";
const end = "2026-12-31T23:59:59Z";

const history = await api(
  `/api/messages/${channelId}?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&size=50`,
  { token }
);
```

## Real-time WebSocket chat

Install the frontend packages:

```bash
npm install sockjs-client @stomp/stompjs
```

Connect with the access token:

```js
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";

const stomp = Stomp.over(() => new SockJS(WS_URL));

stomp.connect(
  { Authorization: `Bearer ${token}` },
  () => {
    stomp.subscribe(
      `/topic/channel.${channelId}`,
      frame => console.log(JSON.parse(frame.body))
    );

    stomp.send(
      `/app/chat.sendMessage/${channelId}`,
      {},
      JSON.stringify({
        channelId,
        mutationType: "CREATE",
        content: "There is a power outage near the market"
      })
    );
  }
);
```

STOMP destinations:

| Action | Destination |
| --- | --- |
| Send | `/app/chat.sendMessage/{channelId}` |
| Edit | `/app/chat.updateMessage/{channelId}` |
| Delete | `/app/chat.deleteMessage/{channelId}` |
| Subscribe | `/topic/channel.{channelId}` |
| Typing | `/app/channel/{channelId}/typing` |

Send/edit/delete operations require the user to be a verified member of the channel's neighborhood.

### Edit a message

```json
{
  "messageId": "8c4e27b7-5a51-4c8c-8238-cfb8dcf87528",
  "channelId": "11111111-1111-1111-1111-111111111111",
  "senderId": "user-uuid",
  "mutationType": "EDIT",
  "content": "Updated message",
  "edited": true,
  "deleted": false
}
```

```js
stomp.send(
  `/app/chat.updateMessage/${channelId}`,
  {},
  JSON.stringify({
    messageId,
    channelId,
    senderId: currentUserId,
    mutationType: "EDIT",
    content: "Updated message",
    edited: true,
    deleted: false
  })
);
```

### Delete a message

```js
stomp.send(
  `/app/chat.deleteMessage/${channelId}`,
  {},
  JSON.stringify({
    messageId,
    channelId,
    senderId: currentUserId,
    mutationType: "DELETE",
    edited: false,
    deleted: true
  })
);
```
