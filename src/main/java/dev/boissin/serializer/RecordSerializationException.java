/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.serializer;

public class RecordSerializationException extends RuntimeException {

    public RecordSerializationException(String message) {
        super(message);
    }

    public RecordSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

}
