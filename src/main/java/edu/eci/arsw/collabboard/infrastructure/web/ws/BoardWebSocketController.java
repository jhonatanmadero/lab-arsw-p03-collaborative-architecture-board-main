package edu.eci.arsw.collabboard.infrastructure.web.ws;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.service.BoardEventApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.Instant;

/**
 * STOMP inbound adapter. It translates protocol destinations into application
 * calls and publishes accepted events. It contains no Board logic.
 *
 * <pre>
 * SEND      /app/boards/{boardId}/events   (client → server)
 * BROADCAST /topic/boards/{boardId}        (server → every subscriber of that Board)
 * PRIVATE   /user/queue/errors             (server → only the client that sent a rejected event)
 * </pre>
 */
@Controller
public class BoardWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(BoardWebSocketController.class);

    private final BoardEventApplicationService service;
    private final SimpMessagingTemplate messagingTemplate;

    public BoardWebSocketController(BoardEventApplicationService service,
                                    SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/boards/{boardId}/events")
    public void handle(@DestinationVariable String boardId, BoardEvent event) {
        if (event == null)
            throw new InvalidBoardEventException("event is required");
        if (!boardId.equals(event.boardId()))
            throw new InvalidBoardEventException(
                    "Destination boardId (" + boardId + ") does not match event.boardId (" + event.boardId() + ")");

        BoardEvent accepted = service.apply(event);           // validate + apply through the application service
        messagingTemplate.convertAndSend(topicFor(boardId), accepted); // broadcast only if accepted
    }

    public static String topicFor(String boardId) {
        return "/topic/boards/" + boardId;
    }

    @MessageExceptionHandler(BoardNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventRejection boardNotFound(BoardNotFoundException e, SimpMessageHeaderAccessor headers) {
        return reject(headers, "BOARD_NOT_FOUND", e.getMessage());
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventRejection invalidEvent(IllegalArgumentException e, SimpMessageHeaderAccessor headers) {
        return reject(headers, "INVALID_EVENT", e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventRejection unreadable(Exception e, SimpMessageHeaderAccessor headers) {
        // e.g. malformed JSON or a BoardEvent that cannot be built (missing eventId/actorId...)
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return reject(headers, "INVALID_EVENT", root.getMessage());
    }

    private BoardEventRejection reject(SimpMessageHeaderAccessor headers, String code, String message) {
        String boardId = boardIdFrom(headers.getDestination());
        log.warn("Rejected board event on board {}: {} - {}", boardId, code, message);
        return new BoardEventRejection(boardId, code, message, Instant.now());
    }

    /** Extracts {boardId} from /app/boards/{boardId}/events without trusting the payload. */
    static String boardIdFrom(String destination) {
        if (destination == null) return null;
        String[] parts = destination.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("boards".equals(parts[i])) return parts[i + 1];
        }
        return null;
    }
}
