package edu.eci.arsw.collabboard.infrastructure.web.ws;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.service.BoardEventApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The STOMP adapter must validate the session and broadcast only accepted events. */
class BoardWebSocketControllerTest {

    private final BoardEventApplicationService service = mock(BoardEventApplicationService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final BoardWebSocketController controller = new BoardWebSocketController(service, template);

    private static BoardEvent deleted(String boardId) {
        return new BoardEvent("evt", boardId, BoardEventType.ELEMENT_DELETED, "client-a", Instant.now(),
                new BoardEventPayload(null, "rect-1", null, null));
    }

    @Test
    void acceptedEventIsBroadcastToTheBoardTopic() {
        BoardEvent event = deleted("board-1");
        when(service.apply(event)).thenReturn(event);

        controller.handle("board-1", event);

        verify(service).apply(event);
        verify(template).convertAndSend("/topic/boards/board-1", event);
    }

    @Test
    void mismatchedBoardIdIsRejectedBeforeApplying() {
        assertThrows(InvalidBoardEventException.class, () -> controller.handle("board-2", deleted("board-1")));
        verifyNoInteractions(service);
        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void rejectedEventIsNeverBroadcast() {
        BoardEvent event = deleted("board-1");
        when(service.apply(event)).thenThrow(new InvalidBoardEventException("Element not found: rect-1"));

        assertThrows(InvalidBoardEventException.class, () -> controller.handle("board-1", event));
        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void boardIdIsExtractedFromTheDestination() {
        assertEquals("abc-123", BoardWebSocketController.boardIdFrom("/app/boards/abc-123/events"));
        assertNull(BoardWebSocketController.boardIdFrom(null));
    }
}
