# ADR-003 — Separate snapshot operations from live collaboration

**Status:** Accepted — completed during Lab #6.

## Context
The application already has a REST API and now needs multiple browsers to observe changes in the same Board without polling.

## Decision
Keep REST for bootstrap/snapshot operations (create board, load board, save snapshot) and use STOMP/WebSocket for real-time collaboration events.

## Comparison

| Dimension | REST (HTTP/JSON) | STOMP/WebSocket |
|-----------|------------------|-----------------|
| Interaction style | Request/response | Asynchronous notification (pub/sub) |
| Data model | Full resource snapshot | Granular interaction event |
| Client coupling | Client decides when to fetch | Server pushes; client reacts |
| Failure/reconnection | Stateless retry; idempotent GET | Explicit reconnection; session resumption via REST load |
| Effect on contract | Board resource representation | BoardEvent envelope + payload types |

## Consequences

### Benefits
1. **Clear separation of concerns**: REST handles authoritative state persistence and bootstrap; WebSocket handles ephemeral live updates.
2. **Simpler client logic**: Initial load and recovery use familiar REST; live updates are additive deltas applied to existing state.

### Costs
1. **Two communication stacks** to maintain, test, and secure (HTTP + WebSocket/STOMP).
2. **Eventual consistency window**: Between REST snapshot and first STOMP event, clients may briefly diverge if not careful.

### Limitations accepted for course scope
- No authentication/authorization on WebSocket (inherited from Lab #6 scope).
- No message ordering guarantees beyond single-session STOMP broker behavior.
- No CRDT/OT for concurrent edits (deferred to Lab #7).

## Rationale
REST is a natural fit for "give me the current state of board X" and "persist this snapshot". STOMP is a natural fit for "notify everyone that element Y moved". Mixing them would force polling or long-lived HTTP connections, adding complexity without benefit for the course scope.