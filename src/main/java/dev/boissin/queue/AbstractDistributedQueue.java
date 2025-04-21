package dev.boissin.queue;

import java.io.IOException;
import java.io.Serializable;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDistributedQueue<T extends Record & Serializable> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractDistributedQueue.class);

    protected final String queuePath;
    protected final String lockPath;
    protected final QueueSerializer<T> queueSerializer;
    protected final QueueConsumer<T> queueConsumer;
    protected CuratorFramework client;
    protected DistributedQueue<T> queue;

    public AbstractDistributedQueue(String queuePath, String lockPath, QueueSerializer<T> queueSerializer, QueueConsumer<T> queueConsumer) {
        this.queueSerializer = queueSerializer;
        this.queuePath = queuePath;
        this.lockPath = lockPath;
        this.queueConsumer = queueConsumer;
    }
    
    public void init(String connectionString) throws Exception {
        final RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
        client.start();

        queue = QueueBuilder.builder(client, queueConsumer, queueSerializer, queuePath)
                .lockPath(lockPath)
                .buildQueue();

        queue.start();
    }

    public void close() throws IOException {
        if (queue != null) {
            queue.close();
            logger.info("Queue closed for path: {}", queuePath);
        }
        if (client != null) {
            client.close();
            logger.info("Curator client closed");
        }
    }
}
