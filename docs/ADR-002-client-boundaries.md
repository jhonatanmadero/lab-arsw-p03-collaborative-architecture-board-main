# ADR-002 — Client boundaries

## Context
The browser client must interact with a REST API for bootstrap/snapshot and a STOMP/WebSocket channel for real-time collaboration. Without clear boundaries, the client code mixes HTTP calls, WebSocket handling, state management, and DOM manipulation in a single file, making it hard to test and evolve.

## Decision
Split the client into four modules with single responsibilities:

| Module | Responsibility | Knows about |
|--------|----------------|-------------|
| `BoardApiClient` | REST calls only (create, load, save) | HTTP endpoints, JSON shape of Board resource |
| `BoardState` | Authoritative in-memory board model; applies remote events idempotently | BoardEvent contract, domain rules (no connectors drag, delete cascades) |
| `BoardView` | SVG rendering and user interaction (drag, click, connect) | BoardState (read-only), DOM/SVG |
| `BoardRealtimeClient` | STOMP connection lifecycle, publish/subscribe, error queue | STOMP destinations (`/app/boards/{id}/events`, `/topic/boards/{id}`, `/user/queue/errors`) |
| `app.js` | Orchestrates the above; wires UI events → publish, remote events → apply → render | All four modules (composition root) |

**Hard boundaries**:
- `BoardView` never calls REST or STOMP; it only emits UI intent callbacks.
- `BoardState` never touches DOM or network; it only mutates in-memory model.
- `BoardRealtimeClient` never touches DOM or BoardState directly; it only delivers parsed events to a callback.
- `app.js` is the only module that knows the full wiring.

## Consequences

### Benefits
1. **Testability**: `BoardState.applyEvent` is a pure function (12/12 JS tests pass without a browser). `BoardRealtimeClient` can be tested with a mock STOMP client.
2. **Replaceability**: Swapping STOMP for a different realtime protocol only touches `BoardRealtimeClient`.
3. **Single source of truth**: `BoardState` is the only place that mutates the board model; both local UI and remote events go through `applyEvent`.

### Trade-offs
- More files for a small app (acceptable for architectural clarity).
- `app.js` becomes a "god module" that wires everything; kept small and explicit.

## Evidence
- `src/main/resources/static/js/state/board-state.js` — pure `applyEvent` with idempotent transitions.
- `src/main/resources/static/js/realtime/board-realtime-client.js` — only STOMP logic, no DOM.
- `src/main/resources/static/js/ui/board-view.js` — only SVG + callbacks, no network.
- `src/main/resources/static/js/app.js` — composition root, ~80 lines.

## Trade-off
The team accepts slightly more boilerplate for the Labs #4–#6 progression. The payoff appears in Lab #7 when concurrent edits require a consistency strategy that only touches `BoardState` and `BoardRealtimeClient`.