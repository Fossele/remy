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
