# ARSW Collaborative Architecture Board — Lab 06

This repository implements **Lab #6: Real-Time Collaboration** for the ARSW course. The application evolves the Lab #5 collaborative board with a STOMP/WebSocket real-time channel so multiple browsers can edit the same board simultaneously.

## Team

| Person | Role | GitHub |
|--------|------|--------|
| Jhonatan David Madero | Persona 1 — Backend STOMP, tests, integration & delivery | [@jhonatanmadero](https://github.com/jhonatanmadero) |
| [Nombre Compañero 2] | Persona 2 — Frontend real-time, JS tests, functional validation | [@usuario2](https://github.com/usuario2) |
| [Nombre Compañero 3] | Persona 3 — Architecture diagrams, ADRs, AI declaration, evidence, README | [@usuario3](https://github.com/usuario3) |

## What is already recovered from Lab #5

- Java 21 + Spring Boot backend.
- Domain / application / repository / REST boundaries.
- Board, RECTANGLE, TEXT and CONNECTOR.
- REST bootstrap/snapshot operations.
- Browser client separated into `BoardApiClient`, `BoardState`, `BoardView` and `app.js`.
- SVG rendering and local create/move/connect/delete interactions.

## Lab #6 evolution

The same Board now gains a **real-time collaboration channel**:

```text
Browser A                         Browser B
   |                                 |
   | SEND /app/boards/{id}/events    |
   +--------------+------------------+
                  v
            STOMP Controller
                  |
          Application Service
                  |
          authoritative Board
                  |
                  v
            /topic/boards/{id}
                 |          |
                 v          v
             Browser A   Browser B
```

REST is still used to create/load a Board and to save/recover a snapshot. Live interaction must travel through STOMP/WebSocket.

## Run

```bash
mvn test
mvn spring-boot:run
```

Open two (or three) browser windows at:

```text
http://localhost:8080/
```

Create/load the same `boardId`, connect both to the live channel and verify propagation.

## Architecture documentation

| Document | Description |
|----------|-------------|
| `docs/event-contract.md` | STOMP destinations, event envelope, 5 event types, validation rules |
| `docs/ADR-002-client-boundaries.md` | Client module boundaries (ApiClient, State, View, RealtimeClient, app.js) |
| `docs/ADR-003-rest-vs-realtime.md` | Why REST + STOMP coexist; benefits, costs, limitations |
| `docs/architecture/application-view-lab06.puml` | ArchiMate Application View (HTTP/JSON vs STOMP/WebSocket) |
| `docs/architecture/class-diagram-lab06.puml` | Key classes for Lab #6 real-time path |
| `docs/architecture/realtime-sequence-lab06.puml` | ELEMENT_MOVED sequence A → server → broker → A+B |
| `docs/AI_USAGE.md` | AI tool usage declaration per team member |
| `docs/evidence/` | Screenshots of demo and test runs |

## Scope boundary

Do **not** add authentication, chat, presence, cursor sharing, Kafka, Redis, CRDT/OT algorithms or database infrastructure in this lab.

Do **not** solve simultaneous-update consistency yet. The current repository and read-modify-write event path intentionally leave material for Lab #7 — Concurrent Collaboration.

## Branch strategy (per team agreement)

| Branch | Author | Purpose |
|--------|--------|---------|
| `chore/cleanup-lab06` | Persona 1 | Remove `target/`, fix `.gitignore` |
| `feature/backend-realtime` | Persona 1 | STOMP config, event service, WS controller, backend tests |
| `feature/frontend-realtime` | Persona 2 | BoardRealtimeClient, BoardState.applyEvent, BoardView fixes, app.js wiring, JS tests |
| `feature/docs-architecture` | Persona 3 | ADRs, diagrams, AI_USAGE, README, evidence screenshots |

**Merge order to main:** `chore/cleanup-lab06` → `feature/backend-realtime` → `feature/frontend-realtime` → `feature/docs-architecture`

## Acceptance criteria (verified in demo)

- Two browsers with same `boardId` receive `ELEMENT_CREATED`.
- Move in A updates position in B without reload (on drop).
- Connector created in A appears in B with same endpoints.
- Delete element removes dependent connectors in all clients.
- Different `boardId` (window C) receives no events.
- Initial load still via REST.
- STOMP callback updates `BoardState` only — no direct DOM manipulation.
- Accepted events go through `BoardEventApplicationService` before broadcast.
- Repo preserves Lab #4/#5 structure (domain/application/infrastructure layers).

## License

MIT — see `LICENSE`.