package edu.eci.arsw.collabboard.infrastructure.web.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check of the real-time path with real STOMP clients:
 * two sessions on the same Board synchronize, a third session on another Board stays isolated,
 * and a rejected event is reported privately instead of being broadcast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoardRealtimeIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    BoardApplicationService boards;

    @Autowired
    ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> { if (s.isConnected()) s.disconnect(); });
        stompClient.stop();
    }

    private StompSession connect() throws Exception {
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private static <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return type; }
            @Override @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) { queue.add((T) payload); }
        });
        return queue;
    }

    private static BoardEvent rectangleCreated(String boardId, String elementId) {
        var rect = new BoardElement(elementId, ElementType.RECTANGLE, 100, 90, 170, 70, "Component", null, null);
        return new BoardEvent(UUID.randomUUID().toString(), boardId, BoardEventType.ELEMENT_CREATED, "client-a",
                Instant.now(), new BoardEventPayload(rect, null, null, null));
    }

    @Test
    void sameBoardSynchronizesAndOtherBoardIsIsolated() throws Exception {
        String boardA = boards.createBoard("A").id();
        String boardB = boards.createBoard("B").id();

        StompSession a1 = connect();
        StompSession a2 = connect();
        StompSession b1 = connect();
        var a1Events = subscribe(a1, "/topic/boards/" + boardA, BoardEvent.class);
        var a2Events = subscribe(a2, "/topic/boards/" + boardA, BoardEvent.class);
        var b1Events = subscribe(b1, "/topic/boards/" + boardB, BoardEvent.class);
        Thread.sleep(300); // let the simple broker register the subscriptions

        BoardEvent sent = rectangleCreated(boardA, "rect-1");
        a1.send("/app/boards/" + boardA + "/events", sent);

        BoardEvent receivedByA2 = a2Events.poll(5, TimeUnit.SECONDS);
        BoardEvent echoToA1 = a1Events.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedByA2, "second client of the same Board must receive the event");
        assertNotNull(echoToA1, "the sender also receives the accepted event");
        assertEquals(sent.eventId(), receivedByA2.eventId());
        assertEquals("rect-1", receivedByA2.payload().element().id());

        assertNull(b1Events.poll(700, TimeUnit.MILLISECONDS), "a different Board must not receive foreign events");
        assertEquals(1, boards.getBoard(boardA).elements().size(), "event was applied to the authoritative Board");
        assertTrue(boards.getBoard(boardB).elements().isEmpty());
    }

    @Test
    void rejectedEventIsReportedPrivatelyAndNotBroadcast() throws Exception {
        String boardA = boards.createBoard("A").id();

        StompSession sender = connect();
        StompSession observer = connect();
        var senderErrors = subscribe(sender, "/user/queue/errors", BoardEventRejection.class);
        var observerErrors = subscribe(observer, "/user/queue/errors", BoardEventRejection.class);
        var observerEvents = subscribe(observer, "/topic/boards/" + boardA, BoardEvent.class);
        Thread.sleep(300);

        var bad = new BoardEvent(UUID.randomUUID().toString(), boardA, BoardEventType.ELEMENT_DELETED, "client-a",
                Instant.now(), new BoardEventPayload(null, "ghost", null, null));
        sender.send("/app/boards/" + boardA + "/events", bad);

        BoardEventRejection rejection = senderErrors.poll(5, TimeUnit.SECONDS);
        assertNotNull(rejection, "sender must be told its event was rejected");
        assertEquals("INVALID_EVENT", rejection.code());
        assertEquals(boardA, rejection.boardId());
        assertNull(observerEvents.poll(700, TimeUnit.MILLISECONDS), "rejected events are never broadcast");
        assertNull(observerErrors.poll(200, TimeUnit.MILLISECONDS), "errors are private to the sender");
    }
}
