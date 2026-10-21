package io.consulting.domain;

/** A business rule failed; the HTTP adapter translates this to a client error. */
public class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}
