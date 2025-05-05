package dev.boissin.exception;

public class InvalidStatusCodeException extends RuntimeException {

    public InvalidStatusCodeException(String message) {
        super(message);
    }

}
