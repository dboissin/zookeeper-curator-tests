/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.model;

import java.io.Serializable;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
public interface Event extends Serializable {

    long startTime();
    long endTime();
    long philopherId();

    @JsonbProperty("start-time")
    default long getStartTime() {
        return startTime();
    }

    @JsonbProperty("end-time")
    default long getEndTime() {
        return endTime();
    }

    default String getPhilosopher() {
        return "Philosopher " + philopherId();
    }

    default String getState() {
        return this.getClass().getSimpleName().replace("Event", "");
    }

    public static record EatEvent(
        long startTime,
        long endTime,
        long philopherId,
        long rightForkId,
        long leftForkId
    ) implements Event {

        @JsonbCreator
        public EatEvent{}

    }

    public static record ThinkEvent(
        long startTime,
        long endTime,
        long philopherId,
        long rightForkId,
        long leftForkId
    ) implements Event {

        @JsonbCreator
        public ThinkEvent{}

    }

}
