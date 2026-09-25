package edu.eci.arsw.collabboard.infrastructure.web.ws;

import java.time.Instant;

/**
 * Private notification sent only to the client whose event was rejected
 * (destination {@code /user/queue/errors}). It is NOT broadcast to the Board topic,
 * so other participants never see rejected changes.
 */
public record BoardEventRejection(String boardId, String code, String message, Instant rejectedAt) {
}
