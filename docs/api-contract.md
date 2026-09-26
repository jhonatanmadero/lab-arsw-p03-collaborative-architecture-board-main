# API Contract — Lab 06 (extends Lab 05)

## REST — Stable operations (bootstrap & snapshot)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/boards` | Create a new board. Returns `{ boardId }`. |
| `GET` | `/api/boards/{boardId}` | Load board snapshot (elements + connectors). |
| `PUT` | `/api/boards/{boardId}` | Replace board with client snapshot (save). |

### BoardElement (REST resource)
```json
{
  "id": "rect-1",
  "type": "RECTANGLE",
  "x": 100.0,
  "y": 80.0,
  "width": 120.0,
  "height": 90.0,
  "text": "",
  "sourceId": null,
  "targetId": null
}
```
- `type`: `RECTANGLE` | `TEXT` | `CONNECTOR`
- For `CONNECTOR`: `sourceId` and `targetId` required (must reference existing non-connector elements).
- `width`/`height`/`text` ignored for connectors.

---

## STOMP/WebSocket — Real-time collaboration (Lab 06)

### Connection
- **Endpoint**: `ws://localhost:8080/ws` (SockJS fallback enabled)
- **STOMP prefix**: `/app` (client → server), `/topic` (server → clients), `/user` (private replies)

### Destinations

| Direction | Destination | Purpose |
|-----------|-------------|---------|
| Client → Server | `SEND /app/boards/{boardId}/events` | Publish a `BoardEvent` |
| Server → Clients | `SUBSCRIBE /topic/boards/{boardId}` | Receive accepted events (broadcast) |
| Server → Sender | `SUBSCRIBE /user/queue/errors` | Receive `BoardEventRejection` (private) |

### BoardEvent Envelope (JSON)
```json
{
  "eventId": "f2b2d7c6-...",
  "boardId": "board-123",
  "type": "ELEMENT_MOVED",
  "actorId": "client-abc",
  "occurredAt": "2026-09-01T12:30:00Z",
  "payload": {
    "element": null,
    "elementId": "rect-1",
    "x": 420.0,
    "y": 180.0
  }
}
```

### Event Types & Payloads

| Type | Payload Fields | Meaning |
|------|----------------|---------|
| `ELEMENT_CREATED` | `element` (BoardElement) | Add RECTANGLE or TEXT |
| `CONNECTOR_CREATED` | `element` (BoardElement with sourceId/targetId) | Add CONNECTOR |
| `ELEMENT_MOVED` | `elementId`, `x`, `y` | Move non-connector element |
| `ELEMENT_UPDATED` | `element` (BoardElement) | Replace editable props (text, size) |
| `ELEMENT_DELETED` | `elementId` | Delete element + dependent connectors |

### Rejection (private to sender)
```json
{
  "eventId": "original-event-id",
  "code": "INVALID_EVENT",
  "message": "Element not found: ghost"
}
```
| Code | When |
|------|------|
| `INVALID_EVENT` | Payload validation failed, element not found, invalid connector |
| `BOARD_NOT_FOUND` | Board does not exist |
| `ELEMENT_NOT_FOUND` | Referenced element missing |
| `INVALID_CONNECTOR` | Source/target invalid or same element |

### Client Contract (from `event-contract.md`)
1. **Server validates & applies before broadcast** — client updates state from accepted events only.
2. **Idempotent apply** — sender receives own echo; `BoardState.applyEvent` handles duplicates.
3. **Rejection → re-sync** — on `/user/queue/errors`, client reloads snapshot via `GET /api/boards/{id}`.
4. **Session isolation** — events from other `boardId` ignored by `BoardState`.
5. **No sequence/version yet** — Lab #7 adds consistency strategy.

---

## Error Responses (REST)

| Status | Body (`ApiError`) |
|--------|-------------------|
| 404 | `{ "timestamp": "...", "status": 404, "error": "Not Found", "message": "Board not found: xyz", "path": "/api/boards/xyz" }` |
| 400 | `{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Invalid connector: ...", "path": "/api/boards/xyz" }` |
| 500 | `{ "timestamp": "...", "status": 500, "error": "Internal Server Error", "message": "...", "path": "..." }` |

---

## Compatibility Notes
- REST unchanged from Lab #5 — existing clients work.
- STOMP is additive — opt-in via "Connect live" button in UI.
- Board resource shape identical in REST `GET/PUT` and `ELEMENT_CREATED` payload.