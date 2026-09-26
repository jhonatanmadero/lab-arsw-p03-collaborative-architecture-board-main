# Evidence — Lab #6 Real-Time Collaboration Demo

This folder contains screenshots from the final demo and test runs. Each image corresponds to a checklist item.

## Required screenshots

| File | Description | Taken by |
|------|-------------|----------|
| `01-board-created.png` | Browser A: New board created, shows empty canvas | Persona 1 |
| `02-browser-b-load.png` | Browser B: Load same boardId, shows empty canvas (REST load) | Persona 1 |
| `03-connect-live.png` | Both browsers: "Connect live" clicked, status shows "connected" | Persona 2 |
| `04-element-created-sync.png` | Browser A creates rectangle → appears in Browser B (ELEMENT_CREATED) | Persona 2 |
| `05-move-sync.png` | Browser A drags rectangle → Browser B updates position on drop (ELEMENT_MOVED) | Persona 2 |
| `06-connector-sync.png` | Browser A creates connector between two elements → appears in Browser B (CONNECTOR_CREATED) | Persona 2 |
| `07-delete-cascade.png` | Browser A deletes element → element + dependent connectors disappear in Browser B (ELEMENT_DELETED) | Persona 2 |
| `08-mvn-test.png` | Terminal: `mvn test` output showing **BUILD SUCCESS** (34 tests) | Persona 1 |
| `09-node-test.png` | Terminal: `node --test src/test/js/*.test.mjs` showing **12/12 passed** | Persona 2 |

## Optional / extra credit

| File | Description |
|------|-------------|
| `10-edit-text-sync.png` | Browser A edits text element → Browser B shows updated text (ELEMENT_UPDATED) |
| `11-isolation.png` | Browser C with different boardId shows no events from A/B |
| `12-reload-rest.png` | Browser B reloads, clicks Load, recovers state via REST |
| `13-rejection-error.png` | Console shows "Server rejected event" → client re-syncs via REST (expected behavior) |

## Notes

- All screenshots taken during the final demo with **3 browser windows** on `http://localhost:8080/`.
- Backend running via `mvn spring-boot:run` (Java 21, Spring Boot 3.5.5).
- No errors in browser console (F12) during demo.
- `grep -rn "TODO LAB-06" src` returns no results — all TODOs resolved.