package edu.eci.arsw.collabboard.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the BoardEvent envelope documented in docs/event-contract.md. */
class BoardEventContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final BoardEventPayload MOVE = new BoardEventPayload(null, "rect-1", 10.0, 20.0);

    @Test
    void eventRequiresBoardAndActorIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "", BoardEventType.ELEMENT_MOVED, "client-a", Instant.now(), MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "", Instant.now(), MOVE));
    }

    @Test
    void eventRequiresIdTypeInstantAndPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent(null, "board", BoardEventType.ELEMENT_MOVED, "client-a", Instant.now(), MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", null, "client-a", Instant.now(), MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", null, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", Instant.now(), null));
    }

    @Test
    void eventKeepsExplicitTypeAndPayload() {
        var event = new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", Instant.parse("2026-09-01T12:30:00Z"), MOVE);
        assertEquals(BoardEventType.ELEMENT_MOVED, event.type());
        assertEquals("rect-1", event.payload().elementId());
    }

    @Test
    void contractSupportsTheFiveMinimumEventTypes() {
        for (String name : new String[]{"ELEMENT_CREATED", "ELEMENT_MOVED", "ELEMENT_UPDATED", "ELEMENT_DELETED", "CONNECTOR_CREATED"}) {
            assertDoesNotThrow(() -> BoardEventType.valueOf(name));
        }
    }

    @Test
    void documentedJsonIsReadableByTheServer() throws Exception {
        String json = """
                {
                  "eventId": "f2b2d7c6-0001",
                  "boardId": "board-123",
                  "type": "ELEMENT_MOVED",
                  "actorId": "client-abc",
                  "occurredAt": "2026-09-01T12:30:00Z",
                  "payload": { "element": null, "elementId": "rect-1", "x": 420.0, "y": 180.0 }
                }
                """;
        BoardEvent event = mapper.readValue(json, BoardEvent.class);
        assertEquals("board-123", event.boardId());
        assertEquals(BoardEventType.ELEMENT_MOVED, event.type());
        assertEquals(Instant.parse("2026-09-01T12:30:00Z"), event.occurredAt());
        assertEquals(420.0, event.payload().x());
    }

    @Test
    void createdEventCarriesAFullElement() throws Exception {
        String json = """
                {"eventId":"e1","boardId":"b1","type":"ELEMENT_CREATED","actorId":"client-a","occurredAt":"2026-09-01T12:30:00Z",
                 "payload":{"element":{"id":"rect-1","type":"RECTANGLE","x":100,"y":90,"width":170,"height":70,
                 "text":"Component","sourceId":null,"targetId":null},"elementId":null,"x":null,"y":null}}
                """;
        BoardEvent event = mapper.readValue(json, BoardEvent.class);
        assertEquals(ElementType.RECTANGLE, event.payload().element().type());
        assertEquals(170, event.payload().element().width());
    }

    @Test
    void jsonWithoutActorIsRejected() {
        String json = """
                {"eventId":"e1","boardId":"b1","type":"ELEMENT_DELETED","occurredAt":"2026-09-01T12:30:00Z",
                 "payload":{"elementId":"rect-1"}}
                """;
        assertThrows(Exception.class, () -> mapper.readValue(json, BoardEvent.class));
    }

    @Test
    void serializedEventUsesIsoInstantAndEnumName() throws Exception {
        var event = new BoardEvent("evt", "board", BoardEventType.ELEMENT_DELETED, "client-a",
                Instant.parse("2026-09-01T12:30:00Z"), new BoardEventPayload(null, "rect-1", null, null));
        String json = mapper.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"ELEMENT_DELETED\""));
        assertTrue(json.contains("\"occurredAt\":\"2026-09-01T12:30:00Z\""));
    }
}
