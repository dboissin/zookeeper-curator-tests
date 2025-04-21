package dev.boissin.serializer;

public class RecordSerializationException extends RuntimeException {

    public RecordSerializationException(String message) {
        super(message);
    }

    public RecordSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

}
