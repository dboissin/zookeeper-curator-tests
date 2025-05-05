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

}
