package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoardEventApplicationServiceTest {

    private InMemoryBoardRepository repository;
    private BoardApplicationService boards;
    private BoardEventApplicationService events;
    private String boardId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBoardRepository();
        boards = new BoardApplicationService(repository);
        events = new BoardEventApplicationService(repository);
        boardId = boards.createBoard("Demo").id();
    }

    // ---------- helpers ----------

    private BoardEvent event(BoardEventType type, BoardEventPayload payload) {
        return new BoardEvent(UUID.randomUUID().toString(), boardId, type, "client-a", Instant.now(), payload);
    }

    private static BoardElement rect(String id, double x, double y) {
        return new BoardElement(id, ElementType.RECTANGLE, x, y, 170, 70, "Component", null, null);
    }

    private static BoardElement text(String id) {
        return new BoardElement(id, ElementType.TEXT, 120, 210, 150, 30, "Text", null, null);
    }

    private static BoardElement connector(String id, String source, String target) {
        return new BoardElement(id, ElementType.CONNECTOR, 0, 0, 0, 0, "", source, target);
    }

    private BoardEvent created(BoardElement e) {
        return event(BoardEventType.ELEMENT_CREATED, new BoardEventPayload(e, null, null, null));
    }

    private BoardEvent connectorCreated(BoardElement e) {
        return event(BoardEventType.CONNECTOR_CREATED, new BoardEventPayload(e, null, null, null));
    }

    private Board board() {
        return boards.getBoard(boardId);
    }

    // ---------- ELEMENT_CREATED ----------

    @Test
    void elementCreatedAddsRectangleAndText() {
        BoardEvent accepted = events.apply(created(rect("r1", 10, 20)));
        events.apply(created(text("t1")));

        assertEquals(2, board().elements().size());
        assertEquals(BoardEventType.ELEMENT_CREATED, accepted.type());
        assertEquals("r1", accepted.payload().element().id());
        assertEquals("r1", accepted.payload().elementId(), "normalized payload exposes the element id");
    }

    @Test
    void elementCreatedRejectsDuplicateIdAndKeepsSnapshot() {
        events.apply(created(rect("r1", 10, 20)));
        assertThrows(IllegalArgumentException.class, () -> events.apply(created(rect("r1", 50, 50))));
        assertEquals(1, board().elements().size());
        assertEquals(10, board().elements().get(0).x());
    }

    @Test
    void elementCreatedRejectsConnectorType() {
        events.apply(created(rect("a", 0, 0)));
        events.apply(created(rect("b", 300, 0)));
        assertThrows(InvalidBoardEventException.class, () -> events.apply(created(connector("c", "a", "b"))));
    }

    @Test
    void elementCreatedRequiresElement() {
        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_CREATED, new BoardEventPayload(null, null, null, null))));
    }

    // ---------- ELEMENT_MOVED ----------

    @Test
    void elementMovedChangesOnlyPosition() {
        events.apply(created(rect("r1", 10, 20)));
        BoardEvent accepted = events.apply(event(BoardEventType.ELEMENT_MOVED, new BoardEventPayload(null, "r1", 420.0, 180.0)));

        BoardElement moved = board().elements().get(0);
        assertEquals(420.0, moved.x());
        assertEquals(180.0, moved.y());
        assertEquals(170, moved.width());
        assertEquals("Component", moved.text());
        assertEquals("r1", accepted.payload().elementId());
        assertNull(accepted.payload().element());
    }

    @Test
    void elementMovedIsIdempotentForTheSameFinalPosition() {
        events.apply(created(rect("r1", 10, 20)));
        var move = new BoardEventPayload(null, "r1", 300.0, 100.0);
        events.apply(event(BoardEventType.ELEMENT_MOVED, move));
        events.apply(event(BoardEventType.ELEMENT_MOVED, move));
        assertEquals(1, board().elements().size());
        assertEquals(300.0, board().elements().get(0).x());
    }

    @Test
    void elementMovedRejectsMissingElementConnectorAndCoordinates() {
        events.apply(created(rect("a", 0, 0)));
        events.apply(created(rect("b", 300, 0)));
        events.apply(connectorCreated(connector("c", "a", "b")));

        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_MOVED, new BoardEventPayload(null, "missing", 1.0, 1.0))));
        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_MOVED, new BoardEventPayload(null, "c", 1.0, 1.0))));
        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_MOVED, new BoardEventPayload(null, "a", null, 1.0))));
    }

    // ---------- CONNECTOR_CREATED ----------

    @Test
    void connectorCreatedKeepsSameEndpoints() {
        events.apply(created(rect("a", 0, 0)));
        events.apply(created(text("b")));
        BoardEvent accepted = events.apply(connectorCreated(connector("c", "a", "b")));

        BoardElement c = board().elements().stream().filter(e -> e.id().equals("c")).findFirst().orElseThrow();
        assertEquals("a", c.sourceId());
        assertEquals("b", c.targetId());
        assertEquals(ElementType.CONNECTOR, accepted.payload().element().type());
    }

    @Test
    void connectorCreatedRejectsMissingOrConnectorEndpoints() {
        events.apply(created(rect("a", 0, 0)));
        events.apply(created(rect("b", 300, 0)));
        events.apply(connectorCreated(connector("c1", "a", "b")));

        assertThrows(InvalidBoardEventException.class, () -> events.apply(connectorCreated(connector("c2", "a", "missing"))));
        assertThrows(InvalidBoardEventException.class, () -> events.apply(connectorCreated(connector("c3", "a", "c1"))));
        assertThrows(InvalidBoardEventException.class, () -> events.apply(connectorCreated(rect("x", 0, 0))));
    }

    // ---------- ELEMENT_UPDATED ----------

    @Test
    void elementUpdatedReplacesEditableProperties() {
        events.apply(created(rect("r1", 10, 20)));
        var edited = new BoardElement("r1", ElementType.RECTANGLE, 10, 20, 200, 90, "API Gateway", null, null);
        events.apply(event(BoardEventType.ELEMENT_UPDATED, new BoardEventPayload(edited, "r1", null, null)));

        assertEquals("API Gateway", board().elements().get(0).text());
        assertEquals(200, board().elements().get(0).width());
    }

    @Test
    void elementUpdatedCannotChangeType() {
        events.apply(created(rect("r1", 10, 20)));
        var asText = new BoardElement("r1", ElementType.TEXT, 10, 20, 100, 30, "x", null, null);
        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_UPDATED, new BoardEventPayload(asText, "r1", null, null))));
    }

    // ---------- ELEMENT_DELETED ----------

    @Test
    void elementDeletedAlsoRemovesDependentConnectors() {
        events.apply(created(rect("a", 0, 0)));
        events.apply(created(rect("b", 300, 0)));
        events.apply(created(rect("d", 600, 0)));
        events.apply(connectorCreated(connector("ab", "a", "b")));
        events.apply(connectorCreated(connector("bd", "b", "d")));

        events.apply(event(BoardEventType.ELEMENT_DELETED, new BoardEventPayload(null, "b", null, null)));

        List<String> ids = board().elements().stream().map(BoardElement::id).toList();
        assertEquals(List.of("a", "d"), ids);
    }

    @Test
    void elementDeletedRejectsUnknownElement() {
        assertThrows(InvalidBoardEventException.class,
                () -> events.apply(event(BoardEventType.ELEMENT_DELETED, new BoardEventPayload(null, "ghost", null, null))));
    }

    // ---------- session / board ----------

    @Test
    void eventForUnknownBoardFails() {
        var e = new BoardEvent("evt", "missing-board", BoardEventType.ELEMENT_CREATED, "client-a", Instant.now(),
                new BoardEventPayload(rect("r1", 0, 0), null, null, null));
        assertThrows(BoardNotFoundException.class, () -> events.apply(e));
    }

    @Test
    void eventsOnOneBoardDoNotTouchAnotherBoard() {
        String other = boards.createBoard("Other").id();
        events.apply(created(rect("r1", 10, 20)));
        assertTrue(boards.getBoard(other).elements().isEmpty());
    }

    @Test
    void acceptedEventKeepsEnvelopeIdentity() {
        BoardEvent original = created(rect("r1", 10, 20));
        BoardEvent accepted = events.apply(original);
        assertEquals(original.eventId(), accepted.eventId());
        assertEquals(original.boardId(), accepted.boardId());
        assertEquals(original.actorId(), accepted.actorId());
        assertEquals(original.occurredAt(), accepted.occurredAt());
    }
}
