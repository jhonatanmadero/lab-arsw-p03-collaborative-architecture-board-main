# ADR-001 — Repository boundary

**Status:** Accepted — established in Lab #4, confirmed in Lab #6.

## Context
The application follows a hexagonal/clean architecture with clear layer boundaries. The repository adapter is the only component that crosses the application/domain boundary to access persistence.

## Decision
Define the repository boundary at the `BoardRepository` interface in the application layer. The domain model (`Board`, `BoardElement`, `BoardConnector`, `ElementType`) has zero dependencies on infrastructure. The `InMemoryBoardRepository` implementation lives in `infrastructure.persistence`.

## Structure

```
src/main/java/edu/eci/arsw/collabboard/
├── domain/
│   └── model/
│       ├── Board.java
│       ├── BoardElement.java
│       ├── BoardConnector.java
│       └── ElementType.java
├── application/
│   ├── port/
│   │   └── out/
│   │       └── BoardRepository.java          ← BOUNDARY INTERFACE
│   ├── service/
│   │   ├── BoardApplicationService.java
│   │   └── BoardEventApplicationService.java
│   ├── event/
│   │   ├── BoardEvent.java
│   │   ├── BoardEventType.java
│   │   └── BoardEventPayload.java
│   └── exception/
│       ├── BoardNotFoundException.java
│       └── InvalidBoardEventException.java
├── infrastructure/
│   ├── persistence/
│   │   └── InMemoryBoardRepository.java      ← IMPLEMENTATION
│   └── web/
│       ├── rest/
│       │   ├── BoardRestController.java
│       │   ├── CreateBoardRequest.java
│       │   ├── ReplaceBoardRequest.java
│       │   ├── CreateBoardResponse.java
│       │   ├── ApiError.java
│       │   └── GlobalExceptionHandler.java
│       └── ws/
│           ├── WebSocketConfig.java
│           ├── BoardWebSocketController.java
│           ├── BoardEventRejection.java
│           └── RejectionCode.java
└── CollaborativeBoardApplication.java
```

## Rules

1. **Domain → Application**: Domain classes are used by application services. No reverse dependency.
2. **Application → Infrastructure**: Application defines `BoardRepository` interface. Infrastructure implements it.
3. **Infrastructure → Domain/Application**: Controllers (REST/WS) call application services. They never access domain directly for mutations.
4. **No ORM entities in domain**: `Board`, `BoardElement`, `BoardConnector` are rich domain objects, not JPA entities.
5. **In-memory for Labs #4–#6**: `InMemoryBoardRepository` uses `ConcurrentHashMap`. Lab #7 will replace with thread-safe or external store.

## Consequences

### Benefits
- **Testability**: Application services test with mock `BoardRepository`; domain tests need no Spring context.
- **Replaceability**: Swapping to PostgreSQL, Redis, or Kafka only touches `infrastructure.persistence`.
- **Clarity**: The boundary is a single interface (`BoardRepository`) — easy to audit.

### Trade-offs
- More boilerplate than anemic Spring Data JPA repositories.
- Manual mapping in `InMemoryBoardRepository` (acceptable for course scope).

## Evidence
- `BoardRepository` interface: `application/port/out/BoardRepository.java`
- Implementation: `infrastructure/persistence/InMemoryBoardRepository.java`
- Domain model: `domain/model/*.java` — no Spring/JPA annotations
- Application services: `application/service/*.java` — depend only on `BoardRepository` interface