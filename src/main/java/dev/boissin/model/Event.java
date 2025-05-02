package dev.boissin.model;

import java.io.Serializable;

public interface Event extends Serializable {

    public static record EatEvent(
        long startTime,
        long endTime,
        long philopherId,
        long rightForkId,
        long leftForkId
    ) implements Event {}

}
