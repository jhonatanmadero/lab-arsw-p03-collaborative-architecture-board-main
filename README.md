# ARSW Collaborative Architecture Board — Lab 06 Starter

This repository is the **recovery baseline for Lab #6**. Your primary input remains your own completed Lab #5 repository. If your previous implementation is unstable, use this starter only to recover the expected architecture and continue.

## What is already recovered from Lab #5

- Java 21 + Spring Boot backend.
- Domain / application / repository / REST boundaries.
- Board, RECTANGLE, TEXT and CONNECTOR.
- REST bootstrap/snapshot operations.
- Browser client separated into `BoardApiClient`, `BoardState`, `BoardView` and `app.js`.
- SVG rendering and local create/move/connect/delete interactions.

## Lab #6 evolution

The same Board now gains a **real-time collaboration channel**.

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

## Search for the work

Search the project for:

```text
TODO LAB-06
```

The important incomplete areas are:

1. `BoardEventApplicationService.apply(...)`
2. `BoardWebSocketController.handle(...)`
3. `BoardRealtimeClient.publish(...)`
4. `BoardState.applyEvent(...)`
5. Wiring accepted events in `app.js`

## Run

```bash
mvn test
mvn spring-boot:run
```

Open two browser windows at:

```text
http://localhost:8080/
```

Create/load the same `boardId`, connect both to the live channel and verify propagation once your implementation is complete.

## Scope boundary

Do **not** add authentication, chat, presence, cursor sharing, Kafka, Redis, CRDT/OT algorithms or database infrastructure in this lab.

Do **not** solve simultaneous-update consistency yet. The current repository and read-modify-write event path intentionally leave material for Lab #7 — Concurrent Collaboration.
