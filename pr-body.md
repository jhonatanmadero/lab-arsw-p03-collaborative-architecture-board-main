## Summary

Completes Persona 3 deliverables for Lab #6 Real-Time Collaboration.

### Changes

**ADRs completed:**
- `docs/ADR-002-client-boundaries.md` — Client module boundaries (BoardApiClient, BoardState, BoardView, BoardRealtimeClient, app.js) with trade-offs and evidence mapping
- `docs/ADR-003-rest-vs-realtime.md` — Why REST + STOMP coexist: comparison table, 2 benefits, 2 costs, 1 limitation

**Documentation:**
- `docs/AI_USAGE.md` — Real AI usage declaration per team member (4 tools/activities)
- `README.md` — Team table, architecture docs index, branch strategy, acceptance criteria

**Diagrams (PlantUML):**
- `docs/architecture/application-view-lab06.puml` — ArchiMate Application View with HTTP/JSON vs STOMP/WebSocket labels
- `docs/architecture/class-diagram-lab06.puml` — Key Lab #6 classes (BoardEvent, BoardEventApplicationService, BoardWebSocketController, etc.)
- `docs/architecture/realtime-sequence-lab06.puml` — ELEMENT_MOVED sequence Browser A -> Server -> Broker -> Browser A+B (idempotent)

**Evidence:**
- `docs/evidence/README.md` — 9 required + 4 optional screenshot checklist for demo

### Verification

- `mvn test` -> 34/34 pass (BUILD SUCCESS)
- `node --test src/test/js/*.test.mjs` -> 13/13 pass
- `grep -rn "TODO LAB-06" src/` -> no results

### Merge order

This is the **last PR** to merge (per team agreement):
1. `chore/cleanup-lab06` (done)
2. `feature/backend-realtime` (done)
3. `feature/frontend-realtime` (done)
4. `feature/docs-architecture` <- **this PR**

### Demo evidence

Screenshots to be added to `docs/evidence/` during final demo with 3 browser windows.