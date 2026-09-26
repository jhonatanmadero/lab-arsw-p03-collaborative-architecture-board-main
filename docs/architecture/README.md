# Architecture evidence

Maintain the diagrams from previous labs. For Lab #6 add the real-time path without redrawing the system as an unrelated application.

## Lab #6 Diagrams

| Diagram | File | Purpose |
|---------|------|---------|
| **ArchiMate Application View** | `application-view-lab06.puml` | Shows Web Client, REST interface, WebSocket/STOMP interface, Application Services, Repository adapter, and STOMP broker. Labels HTTP/JSON and STOMP/WebSocket. Shows who publishes (BoardWebSocketController → broker) and who subscribes (BoardRealtimeClient ← broker). |
| **Class Diagram** | `class-diagram-lab06.puml` | Key classes: `BoardEvent`, `BoardEventType`, `BoardEventPayload`, `BoardEventApplicationService`, `BoardWebSocketController`, `InvalidBoardEventException`, `WebSocketConfig`, plus existing domain (`Board`, `BoardElement`, `BoardConnector`, `ElementType`) and persistence (`BoardRepository`, `InMemoryBoardRepository`). |
| **Realtime Sequence** | `realtime-sequence-lab06.puml` | End-to-end flow of `ELEMENT_MOVED` from Browser A → STOMP → Server (validate/apply/save) → broker → Browser A (echo) + Browser B (remote). Shows idempotent `applyEvent` on both clients. |

## Generating images

### PlantUML (VS Code)
1. Install "PlantUML" extension (jebbs.plantuml)
2. Open `.puml` file → `Alt+D` (preview) or right-click → "Export Diagram" → PNG/SVG

### PlantUML (Online)
Paste `.puml` content into https://www.plantuml.com/plantuml/uml/ → download PNG/SVG

### ArchiMate (if required by professor)
If the professor requests ArchiMate made in Archi tool:
1. Open Archi → create new "Application View" model
2. Recreate the same elements: Web Client, REST Interface, WebSocket/STOMP Interface, Application Services (BoardApplicationService, BoardEventApplicationService), Repository (InMemoryBoardRepository), STOMP Broker (SimpleBroker)
3. Connect with "Serving" and "Access" relationships labeled HTTP/JSON and STOMP/WebSocket
4. Export as image → place in `docs/architecture/` (e.g., `application-view-lab06.png`)

## Minimum evidence checklist
- [ ] ArchiMate Application View (PNG/SVG committed)
- [ ] Class Diagram (PNG/SVG committed)
- [ ] Sequence Diagram (PNG/SVG committed)
- [ ] Diagrams match implemented dependencies (no phantom classes)
- [ ] Two interaction styles explicitly labeled: HTTP/JSON and STOMP/WebSocket
- [ ] Publisher/subscriber clearly shown