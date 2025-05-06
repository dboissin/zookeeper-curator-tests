/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.exception;

public class InvalidStatusCodeException extends RuntimeException {

    public InvalidStatusCodeException(String message) {
        super(message);
    }

}
