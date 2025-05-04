package dev.boissin.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.model.Event;
import dev.boissin.model.Event.EatEvent;

public class StateEventsChecker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StateEventsChecker.class);
    private static final long LAG_TIME = 10_000L;

    private final ConcurrentSkipListMap<Long, List<Event>> events = new ConcurrentSkipListMap<>();
    private AtomicBoolean running = new AtomicBoolean(true);

    public void addEvent(Event event) {
        final Long startTime = ((EatEvent)event).startTime();
        // Put list before compute because compute method isn't atomic.
        events.putIfAbsent(startTime, new LinkedList<>());
        events.compute(startTime, (key, value) -> {
            value.add(event);
            return value;
        });
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
                entry.getValue().sort(Comparator.comparing(e -> ((EatEvent) e).endTime()));
                for (Event event: entry.getValue()) {
                    forksEndTime.compute(((EatEvent)event).rightForkId(), chekAndUpdateFork((EatEvent) event));
                    forksEndTime.compute(((EatEvent)event).leftForkId(), chekAndUpdateFork((EatEvent) event));
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
            }
            return event.endTime();
        };
    }

    public void close() {
        running.set(false);
    }
}
