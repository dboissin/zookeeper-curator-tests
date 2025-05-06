/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.exception.IllegalConcurrentForkUsageException;
import dev.boissin.model.Event;
import dev.boissin.model.StateEventCheckerResult;
import dev.boissin.model.Event.EatEvent;

public class StateEventsChecker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StateEventsChecker.class);
    private static final long LAG_TIME = 10_000L;
    private static final long EVENTS_RETENTION = 45_000L;

    private final ConcurrentSkipListMap<Long, List<Event>> events = new ConcurrentSkipListMap<>();
    private final ConcurrentLinkedDeque<Event> verifiedEvents = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Event> errorEvents = new ConcurrentLinkedDeque<>();
    private AtomicBoolean running = new AtomicBoolean(true);

    public void addEvent(Event event) {
        if (event instanceof EatEvent) {
            final Long startTime = event.startTime();
            // Put list before compute because compute method isn't atomic.
            events.putIfAbsent(startTime, new LinkedList<>());
            events.compute(startTime, (key, value) -> {
                value.add(event);
                return value;
            });
        } else {
            verifiedEvents.add(event);
        }
    }

    @Override
    public void run() {
        // Key forkId : value event endTime
        final Map<Long, Long> forksEndTime = new HashMap<>();

        int countProcessedEvents = 0;
        long lastCountLog = System.currentTimeMillis();

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            final long now = System.currentTimeMillis();
            final long waitingTime =  ((events.isEmpty() ? now : events.firstKey()) + LAG_TIME) - now;
            if (waitingTime <= 0) {
                Map.Entry<Long, List<Event>> entry = events.pollFirstEntry();
                entry.getValue().sort(Comparator.comparing(Event::endTime));
                for (Event event: entry.getValue()) {
                    try {
                        forksEndTime.compute(((EatEvent)event).rightForkId(), chekAndUpdateFork((EatEvent) event));
                        forksEndTime.compute(((EatEvent)event).leftForkId(), chekAndUpdateFork((EatEvent) event));
                        verifiedEvents.add(event);
                    } catch (IllegalConcurrentForkUsageException e) {
                        errorEvents.add(event);
                    }
                }
                countProcessedEvents++;
            } else {
                try {
                    log.debug("Wait {}ms before check Events", waitingTime);
                    Thread.sleep(waitingTime);
                } catch (InterruptedException e) {
                    running.set(false);
                    log.error("Thread interrupted", e);
                }
            }

            if ((lastCountLog + 60_000L) < now) {
                log.info("StateEventsChecker has verifed {} events.", countProcessedEvents);
                lastCountLog = now;
                verifiedEvents.removeIf(event -> event.startTime() < (now - EVENTS_RETENTION));
            }
        }
    }

    private BiFunction<Long, Long, Long> chekAndUpdateFork(EatEvent event) {
        return (forkId, expireTime) -> {
            log.debug("compute verif forks - {} : {}", forkId, expireTime);
            if (event.rightForkId() == event.leftForkId()) {
                log.error("Twice use of same fork {} is not possible.", event.rightForkId());
            }
            if (forkId != null && expireTime != null && event.startTime() < expireTime) {
                log.error("Concurrent use of fork {} is not possible.", forkId);
                throw new IllegalConcurrentForkUsageException();
            }
            return event.endTime();
        };
    }

    public StateEventCheckerResult getResult() {
        final StateEventCheckerResult result = new StateEventCheckerResult();
        final long delay = System.currentTimeMillis() - LAG_TIME;
        result.setErrorEvents(errorEvents.stream().toList());
        result.setVerifiedEvents(verifiedEvents.stream().filter(event -> event.startTime() < delay).toList());
        return result;
    }

    public void close() {
        running.set(false);
    }
}
