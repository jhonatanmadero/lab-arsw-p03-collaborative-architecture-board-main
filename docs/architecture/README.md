# Architecture evidence

Maintain the diagrams from previous labs. For Lab #6 add the real-time path without redrawing the system as an unrelated application.

Minimum evidence:

1. **ArchiMate Application View** showing the Web Client, REST interface, WebSocket/STOMP interface, application service and repository adapter.
2. **Relevant class diagram** including `BoardEvent`, `BoardEventApplicationService`, `BoardWebSocketController` and the existing domain/application classes.
3. Label the two interaction styles explicitly: **HTTP/JSON** and **STOMP/WebSocket**.
4. Show which component publishes and which components subscribe.

The diagrams must match the implemented dependencies.
