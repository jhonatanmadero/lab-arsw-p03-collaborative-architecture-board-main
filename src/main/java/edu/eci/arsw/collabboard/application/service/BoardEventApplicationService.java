package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies collaboration events to the authoritative Board state.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Load the Board (fail with {@link BoardNotFoundException} if missing).</li>
 *   <li>Translate {@code event.type + payload} into a new immutable {@link Board}.</li>
 *   <li>Let the domain constructor enforce invariants (duplicate ids, valid connectors).</li>
 *   <li>Save the resulting snapshot through the {@link BoardRepository} port.</li>
 *   <li>Return a normalized event that is safe to broadcast.</li>
 * </ol>
 *
 * <p>This class knows nothing about STOMP, topics or sessions.
 *
 * <p><strong>Known limitation (intentional, Lab #7):</strong> the load → transform → save
 * sequence is a non-atomic read-modify-write over a non-thread-safe repository.
 * Two simultaneous events on the same Board may cause a lost update.
 */
@Service
public class BoardEventApplicationService {

    private final BoardRepository repository;

    public BoardEventApplicationService(BoardRepository repository) {
        this.repository = repository;
    }

    public BoardEvent apply(BoardEvent event) {
        if (event == null) throw new InvalidBoardEventException("event is required");

        Board current = repository.findById(event.boardId())
                .orElseThrow(() -> new BoardNotFoundException(event.boardId()));

        BoardEventPayload payload = event.payload();
        Board next;
        BoardEventPayload normalized;

        switch (event.type()) {
            case ELEMENT_CREATED -> {
                BoardElement element = requireElement(payload);
                if (element.type() == ElementType.CONNECTOR)
                    throw new InvalidBoardEventException("ELEMENT_CREATED only accepts RECTANGLE or TEXT; use CONNECTOR_CREATED");
                next = withAdded(current, element);
                normalized = new BoardEventPayload(element, element.id(), element.x(), element.y());
            }
            case CONNECTOR_CREATED -> {
                BoardElement connector = requireElement(payload);
                if (connector.type() != ElementType.CONNECTOR)
                    throw new InvalidBoardEventException("CONNECTOR_CREATED requires an element of type CONNECTOR");
                requireShape(current, connector.sourceId(), "sourceId");
                requireShape(current, connector.targetId(), "targetId");
                next = withAdded(current, connector);
                normalized = new BoardEventPayload(connector, connector.id(), null, null);
            }
            case ELEMENT_MOVED -> {
                String id = requireElementId(payload);
                if (payload.x() == null || payload.y() == null)
                    throw new InvalidBoardEventException("ELEMENT_MOVED requires x and y");
                BoardElement target = find(current, id)
                        .orElseThrow(() -> new InvalidBoardEventException("Element not found: " + id));
                if (target.type() == ElementType.CONNECTOR)
                    throw new InvalidBoardEventException("Connectors cannot be moved directly");
                BoardElement moved = new BoardElement(target.id(), target.type(), payload.x(), payload.y(),
                        target.width(), target.height(), target.text(), target.sourceId(), target.targetId());
                next = withReplaced(current, moved);
                normalized = new BoardEventPayload(null, id, payload.x(), payload.y());
            }
            case ELEMENT_UPDATED -> {
                BoardElement updated = requireElement(payload);
                BoardElement existing = find(current, updated.id())
                        .orElseThrow(() -> new InvalidBoardEventException("Element not found: " + updated.id()));
                if (existing.type() != updated.type())
                    throw new InvalidBoardEventException("ELEMENT_UPDATED cannot change the element type");
                if (updated.type() == ElementType.CONNECTOR) {
                    requireShape(current, updated.sourceId(), "sourceId");
                    requireShape(current, updated.targetId(), "targetId");
                }
                next = withReplaced(current, updated);
                normalized = new BoardEventPayload(updated, updated.id(), null, null);
            }
            case ELEMENT_DELETED -> {
                String id = requireElementId(payload);
                if (find(current, id).isEmpty())
                    throw new InvalidBoardEventException("Element not found: " + id);
                next = withRemoved(current, id);
                normalized = new BoardEventPayload(null, id, null, null);
            }
            default -> throw new InvalidBoardEventException("Unsupported event type: " + event.type());
        }

        repository.save(next);
        return new BoardEvent(event.eventId(), event.boardId(), event.type(), event.actorId(), event.occurredAt(), normalized);
    }

    // ---------- payload validation ----------

    private static BoardElement requireElement(BoardEventPayload payload) {
        if (payload.element() == null)
            throw new InvalidBoardEventException("payload.element is required for this event type");
        return payload.element();
    }

    private static String requireElementId(BoardEventPayload payload) {
        if (payload.elementId() == null || payload.elementId().isBlank())
            throw new InvalidBoardEventException("payload.elementId is required for this event type");
        return payload.elementId();
    }

    private static void requireShape(Board board, String id, String field) {
        BoardElement e = find(board, id)
                .orElseThrow(() -> new InvalidBoardEventException("Connector " + field + " does not exist: " + id));
        if (e.type() == ElementType.CONNECTOR)
            throw new InvalidBoardEventException("Connector " + field + " cannot reference another connector");
    }

    // ---------- immutable Board transitions ----------

    private static Optional<BoardElement> find(Board board, String id) {
        return board.elements().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static Board withAdded(Board board, BoardElement element) {
        List<BoardElement> elements = new ArrayList<>(board.elements());
        elements.add(element);
        return new Board(board.id(), board.name(), elements); // domain rejects duplicate ids
    }

    private static Board withReplaced(Board board, BoardElement element) {
        List<BoardElement> elements = board.elements().stream()
                .map(e -> e.id().equals(element.id()) ? element : e)
                .toList();
        return new Board(board.id(), board.name(), elements);
    }

    private static Board withRemoved(Board board, String id) {
        List<BoardElement> elements = board.elements().stream()
                .filter(e -> !e.id().equals(id))
                .filter(e -> !id.equals(e.sourceId()) && !id.equals(e.targetId()))
                .toList();
        return new Board(board.id(), board.name(), elements);
    }
}
