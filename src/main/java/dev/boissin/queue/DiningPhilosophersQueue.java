/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.queue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.model.Event;
import dev.boissin.serializer.RecordSerializer;

public class DiningPhilosophersQueue {

    private static final Logger log = LoggerFactory.getLogger(DiningPhilosophersQueue.class);
    private static final String QUEUE_PATH = "/philosophers/events/queue";
    private static final String QUEUE_LOCK_PATH = "/philosophers/events/locks";

    private final DistributedQueue<Event> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DiningPhilosophersQueue(CuratorFramework client, QueueConsumer<Event> queueConsumer) throws Exception {
        queue = QueueBuilder.builder(client, queueConsumer, new RecordSerializer<Event>(), QUEUE_PATH)
                .lockPath(QUEUE_LOCK_PATH)
                .buildQueue();
        queue.start();
    }

    public void sendEvent(Event event) throws Exception {
        if (running.get()) {
            queue.put(event);
        } else {
            log.warn("Queue is closed. Event not send : {}", event);
        }
    }

    public void close() throws IOException {
        running.set(false);
        if (queue != null) {
            queue.close();
        }
    }

}
