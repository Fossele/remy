# Neighborhood Messaging Platform

Backend for a trusted local-community messaging network. It combines JWT authentication, real-time STOMP/WebSocket chat, neighborhood memberships, moderated channels, message history, and message reporting.

A neighborhood location describes the shared area. A resident's exact house address should not be stored in a public profile or sent through chat. The product can later add separate, restricted, member-verification records with quarter, landmark, and approximate coordinates.

## Core features

- User registration and login
- JWT access tokens and refresh tokens
- Logout and refresh-token revocation
- REST API for users, neighborhood discovery, channels, history, and reports
- SockJS/STOMP real-time chat
- Send, edit, delete, and typing events
- Neighborhood membership states: pending, verified, suspended, removed
- Roles: member, moderator, administrator
- Channel categories: general, safety, lost/found, emergency, events, marketplace
- Membership and ownership checks on mutations
- Reporting of abusive or unsafe messages

## Technology

- Java 21
- Spring Boot
- Spring Security
- OAuth2 resource-server JWT support
- STOMP over SockJS
- Spring Data JPA
- MySQL
- Maven Wrapper

## Requirements

- Java 21
- MySQL 8 or compatible MySQL server
- Maven Wrapper is included
- Optional: Docker for container execution

## Database setup

Create the database:

```sql
CREATE DATABASE chat_db;
```

The default datasource is configured in `src/main/resources/application.yaml`:

```text
jdbc:mysql://localhost:3306/chat_db
```

Set credentials and security values before starting the application:

```bash
export DB_USER=root
export DB_PASSWORD='your-database-password'
export JWT_SECRET='replace-with-a-long-random-secret'
export JWT_ACCESS_TOKEN_TTL=15m
export JWT_REFRESH_TOKEN_TTL=30d
```

The current development defaults are convenience values only. Never use the default JWT secret in production.

## Run locally

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` by default.

## Run tests

```bash
./mvnw test
```

The suite covers:

- Registration and login validation
- JWT claims
- Refresh and logout behavior
- User lookup
- Message send/edit/delete authorization
- Message history
- WebSocket authentication
- Neighborhood membership and moderation rules

## Authentication flow

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "displayName": "Alice"
}
```

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "displayName": "Alice"
  }'
```

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "password123"
}
```

The response contains:

- `accessToken`
- `refreshToken`
- `expiresIn`
- `user`

### Refresh

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "stored-refresh-token"
}
```

### Logout

```http
POST /api/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "stored-refresh-token"
}
```

Logout revokes the matching stored refresh token.

### Authenticated user

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## REST endpoints

Send the bearer token on every protected request:

```http
Authorization: Bearer ACCESS_TOKEN
```

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Register |
| `POST` | `/api/auth/login` | Login |
| `POST` | `/api/auth/refresh` | Refresh access token |
| `POST` | `/api/auth/logout` | Revoke refresh token |
| `GET` | `/api/users/me` | Read current user |
| `GET` | `/api/neighborhoods` | List active neighborhoods |
| `POST` | `/api/neighborhoods` | Create a neighborhood |
| `POST` | `/api/neighborhoods/{id}/join` | Join or request membership |
| `POST` | `/api/neighborhoods/{id}/members/{memberId}/verify` | Verify a member |
| `POST` | `/api/neighborhoods/{id}/channels` | Create a channel |
| `GET` | `/api/neighborhoods/{id}/channels` | List channels |
| `POST` | `/api/neighborhoods/reports` | Report a message |
| `GET` | `/api/messages/{channelId}` | Read channel history |

## Neighborhood model

A neighborhood is a shared local area. The `location` field should contain a broad, recognizable location such as a city, district, quarter, and landmark.

```json
{
  "name": "Bepanda",
  "location": "Douala III, Bepanda, near Bepanda Omnisports",
  "description": "Community for Bepanda residents"
}
```

Do not use this field for one resident's private house address.

Membership lifecycle:

```text
PENDING -> VERIFIED
VERIFIED -> SUSPENDED
VERIFIED -> REMOVED
```

Roles:

- `MEMBER`: uses verified neighborhood channels
- `MODERATOR`: verifies members and manages channels
- `ADMIN`: receives administrator privileges when creating a neighborhood

## Neighborhood APIs

### Create

```bash
curl -X POST http://localhost:8080/api/neighborhoods \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Bepanda",
    "location": "Douala III, Bepanda, near Bepanda Omnisports",
    "description": "Community for Bepanda residents"
  }'
```

The creator is stored as a verified administrator.

### Join

```bash
curl -X POST \
  "http://localhost:8080/api/neighborhoods/$NEIGHBORHOOD_ID/join" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The response starts with status `PENDING`.

### Verify

Only an administrator or moderator can verify a member:

```bash
curl -X POST \
  "http://localhost:8080/api/neighborhoods/$NEIGHBORHOOD_ID/members/$MEMBER_ID/verify" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Create a channel

```bash
curl -X POST \
  "http://localhost:8080/api/neighborhoods/$NEIGHBORHOOD_ID/channels" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Safety Alerts",
    "description": "Urgent safety information",
    "category": "SAFETY"
  }'
```

Available categories:

| Category | Typical use |
| --- | --- |
| `GENERAL` | Everyday community discussion |
| `SAFETY` | Crime, fire, and safety notices |
| `LOST_FOUND` | Missing people, pets, or belongings |
| `EMERGENCY` | Time-sensitive emergencies |
| `EVENTS` | Meetings, gatherings, and public events |
| `MARKETPLACE` | Local buying, selling, and recommendations |

Only verified members can list channels. Channel creation requires moderator or administrator privileges.

### Report a message

```bash
curl -X POST http://localhost:8080/api/neighborhoods/reports \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "messageId": "8c4e27b7-5a51-4c8c-8238-cfb8dcf87528",
    "reason": "HARASSMENT",
    "details": "Repeated unwanted and threatening messages"
  }'
```

Reports are stored with `OPEN` status for moderator review.

## Message history

```http
GET /api/messages/{channelId}?start=START&end=END&size=50&sort=createdAt,DESC
```

Example:

```bash
curl "http://localhost:8080/api/messages/$CHANNEL_ID?start=2026-01-01T00:00:00Z&end=2026-12-31T23:59:59Z&size=50" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The time parameters use ISO-8601 dates. Pagination follows Spring Data `Pageable` conventions.

## WebSocket and STOMP

SockJS endpoint:

```text
http://localhost:8080/chatt
```

Connect with the access token in the STOMP connect headers:

```json
{
  "Authorization": "Bearer ACCESS_TOKEN"
}
```

JavaScript example:

```bash
npm install sockjs-client @stomp/stompjs
```

```js
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";

const stomp = Stomp.over(() => new SockJS("http://localhost:8080/chatt"));

stomp.connect(
  { Authorization: `Bearer ${accessToken}` },
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
| Typing | `/app/channel/{channelId}/typing` |
| Subscribe | `/topic/channel.{channelId}` |

Send, edit, and delete operations require:

- A valid JWT principal
- An existing channel
- Verified neighborhood membership
- Message ownership for edit/delete operations

## Frontend integration

See [`docs/frontend-integration.md`](docs/frontend-integration.md) for REST and JavaScript examples, including authentication storage, neighborhood joining, channel creation, message reporting, history requests, and STOMP subscriptions.

## Community project funds

Verified neighborhood members can contribute to active project funds through Fapshi's hosted checkout. Fund balances are derived from an append-only ledger and include the net amount Fapshi reports after fees. A contribution is credited only after its payment is confirmed by Fapshi's status API or verified webhook.

Create a fund as a neighborhood moderator or administrator:

```http
POST /api/neighborhoods/{neighborhoodId}/funds
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json
```

```json
{
  "name": "Road repair",
  "description": "Gravel and drainage work",
  "targetAmount": 500000
}
```

Contribute to a fund as a verified member:

```http
POST /api/neighborhoods/{neighborhoodId}/funds/{fundId}/contributions
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json
```

```json
{
  "amount": 5000
}
```

The response contains a `paymentLink` for Fapshi checkout. Amounts are integer XAF and the minimum contribution is 100 XAF.

A verified member can request a withdrawal with a beneficiary name and Cameroon mobile number. The request reserves the amount while pending. Every verified neighborhood administrator must approve it; any rejection ends the request. The admin roster is captured when the request is made, and a roster change cancels the request.

After unanimous approval, a listed administrator can initiate a Fapshi payout from the app. The app checks the payout-service balance, initiates the payout, and debits the fund only after Fapshi confirms success. Payouts are limited to 100–500,000 XAF per request; larger withdrawals can be paid externally and recorded with a receipt.

Fapshi collection and payout services have separate credentials and balances. Funds collected by the collection service do not automatically appear in the payout service; transfer/top-up between them through the Fapshi dashboard or app. Live payouts require sandbox testing and approval from Fapshi support.

Configure Fapshi for sandbox testing with:

```bash
export FAPSHI_BASE_URL=https://sandbox.fapshi.com
export FAPSHI_COLLECTION_API_USER='your-collection-api-user'
export FAPSHI_COLLECTION_API_KEY='your-collection-api-key'
export FAPSHI_PAYOUT_API_USER='your-payout-api-user'
export FAPSHI_PAYOUT_API_KEY='your-payout-api-key'
export FAPSHI_WEBHOOK_SECRET='your-webhook-secret'
export FAPSHI_WEBHOOK_URL='https://your-public-host/api/webhooks/fapshi'
export FAPSHI_REDIRECT_URL='https://your-frontend.example/payment-return'
```

Keep credentials out of source control. The sandbox base URL is the default; use the live URL only after the Fapshi account is configured for production. See [`docs/fapshi_api_integration_guide_for_ai_agents.md`](docs/fapshi_api_integration_guide_for_ai_agents.md) for provider constraints.

## Docker

Build:

```bash
docker build -t neighborhood-messaging .
```

Run:

```bash
docker run --rm -p 8080:8080 \
  -e DB_USER=root \
  -e DB_PASSWORD='your-database-password' \
  -e JWT_SECRET='replace-with-a-long-random-secret' \
  neighborhood-messaging
```

The application and Docker setup require MySQL to be reachable from the container. If MySQL runs on the Docker host, configure the datasource accordingly instead of using `localhost` inside the container.

Note: the current `Dockerfile` declares port `9000`, while the Spring Boot default is port `8080`. `-p 8080:8080` matches the application default; update the Docker exposure and Spring server port together if port `9000` is intended.

## Privacy and trust considerations

- Do not expose exact member house locations through public APIs.
- Use broad neighborhood locations only in public neighborhood records.
- Restrict verification evidence to authorized moderators.
- Do not log bearer tokens or refresh tokens.
- Treat emergency reports as urgent but verify false or duplicate reports.
- Add rate limits, anti-abuse controls, audit logs, and account deletion before public rollout.
