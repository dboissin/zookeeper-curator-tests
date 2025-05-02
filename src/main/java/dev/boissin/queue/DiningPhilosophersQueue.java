package dev.boissin.queue;

import java.io.IOException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;

import dev.boissin.model.Event;
import dev.boissin.serializer.RecordSerializer;

public class DiningPhilosophersQueue {

    private static final String QUEUE_PATH = "/philosophers/events/queue";
    private static final String QUEUE_LOCK_PATH = "/philosophers/events/locks";

    private final DistributedQueue<Event> queue;

    public DiningPhilosophersQueue(CuratorFramework client, QueueConsumer<Event> queueConsumer) throws Exception {
        queue = QueueBuilder.builder(client, queueConsumer, new RecordSerializer<Event>(), QUEUE_PATH)
                .lockPath(QUEUE_LOCK_PATH)
                .buildQueue();
        queue.start();
    }

    public void sendEvent(Event event) throws Exception {
        queue.put(event);
    }

    public void close() throws IOException {
        if (queue != null) {
            queue.close();
        }
    }

}
