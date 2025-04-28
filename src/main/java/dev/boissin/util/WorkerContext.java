package dev.boissin.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerContext {

    private static final Logger log = LoggerFactory.getLogger(WorkerContext.class);
    private static final String WORKER_COUNTER_PATH = "/counters/workers";
    private static final String DEFAULT_NAMESPACE = "test-zk-project";
    private static final String NAMESPACE_ENV = "NAMESPACE";

    private CuratorFramework client;
    private AtomicReference<Long> workerId = new AtomicReference<>(null);

    private WorkerContext() {}

    private static class WorkerContextHolder {
        private static final WorkerContext instance = new WorkerContext();
    }

    public static WorkerContext getContext() {
        return WorkerContextHolder.instance;
    }

    public void init(String connectionString) {
        final RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.builder()
                .namespace(getNamespace())
                .connectString(connectionString)
                .retryPolicy(retryPolicy)
                .build();
        client.start();
    }

    public String getNamespace() {
        return Optional.ofNullable(System.getenv(NAMESPACE_ENV)).orElse(DEFAULT_NAMESPACE);
    }

    public long getWorkerId() {
        return workerId.updateAndGet(id -> {
            if (id == null) {
                final DistributedAtomicLong count = new DistributedAtomicLong(
                    client,
                    WORKER_COUNTER_PATH,
                    new RetryNTimes(10, 10)
                );
                try {
                    return count.increment().postValue();
                } catch (Exception e) {
                    log.error("Error when get worker id count.", e);
                }
            }
            return id;
        });
    }

    public CuratorFramework getClient() {
        return client;
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }

}
