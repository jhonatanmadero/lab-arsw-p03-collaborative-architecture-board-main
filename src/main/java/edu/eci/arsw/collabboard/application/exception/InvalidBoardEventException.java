package edu.eci.arsw.collabboard.application.exception;

/**
 * Signals that a collaboration event violates the event contract or cannot be
 * applied to the current Board state. It is an application concern, not a
 * transport concern: the STOMP adapter decides how to report it to the client.
 */
public class InvalidBoardEventException extends IllegalArgumentException {
    public InvalidBoardEventException(String message) {
        super(message);
    }
}
