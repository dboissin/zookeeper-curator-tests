package dev.boissin.service;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.util.WorkerContext;
import io.micrometer.core.instrument.Timer;

public class Philosopher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Philosopher.class);

    private static final int MAX_RANDOM_TIME_MS = 800;
    private static final int MIN_RANDOM_TIME_MS = 200;
    private static final int RETRY_UPDATE_FORK_LIMIT = 10;
    private static final long UPDATE_FORK_ACQUIRE_TIMEOUT_MS = 100L;

    private final Random rnd;
    private final long id;
    private final InterProcessSemaphoreV2 semaphore;
    private final CuratorFramework client;

    private Timer thinkTimer;
    private Timer eatTimer;
    private Timer takeForkTimer;
    private Timer releaseTimer;

    private final InterProcessMutex rightFork;
    private InterProcessMutex leftFork;
    private CuratorCache forkPathCache;
    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicLong leftForkId = new AtomicLong(-1L);

    public Philosopher(long id, long seed, SharedCount sharedCount) throws Exception {
        this.rnd = new Random(seed);
        this.id = id;
        final WorkerContext context = WorkerContext.getContext();

        thinkTimer = Timer.builder("philosophers.state.duration")
                .description("Duration in each state")
                .tag("philosopher", "" + this.id)
                .tag("state", "think")
                .register(context.getMeterRegistry());
        eatTimer = Timer.builder("philosophers.state.duration")
                .description("Duration in each state")
                .tag("philosopher", "" + this.id)
                .tag("state", "eat")
                .register(context.getMeterRegistry());
        takeForkTimer = Timer.builder("philosophers.state.duration")
                .description("Duration in each state")
                .tag("philosopher", "" + this.id)
                .tag("state", "take-fork")
                .register(context.getMeterRegistry());
        releaseTimer = Timer.builder("philosophers.state.duration")
                .description("Duration in each state")
                .tag("philosopher", "" + this.id)
                .tag("state", "release-fork")
                .register(context.getMeterRegistry());

        this.client = context.getClient();
        this.semaphore = new InterProcessSemaphoreV2(client, PhilosopherManager.SEMAPHORE_PATH, sharedCount);
        this.client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                .forPath(PhilosopherManager.FORKS_PATH + "/" + id,
                        ("Philosopher " + id + " right fork").getBytes(StandardCharsets.UTF_8));
        this.rightFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH + "-mutex/" + id + "/fork");

        this.forkPathCache = CuratorCache.build(client, PhilosopherManager.FORKS_PATH);
        final CuratorCacheListener listener = CuratorCacheListener.builder()
            .forCreates(this::handleForkPathChange)
            .forDeletes(this::handleForkPathChange)
            .build();
        this.forkPathCache.listenable().addListener(listener);
        this.forkPathCache.start();
        this.rightFork.acquire();
        Thread.sleep(1000L);
        this.rightFork.release();
    }

    private Lease takeForks() throws Exception {
        final long start = System.nanoTime();
        log.info("before acquire semaphore {}", this.id);
        Lease lease = this.semaphore.acquire();
        log.info("before acquire right mutex {}", this.id);
        rightFork.acquire();
        log.info("before acquire left mutex : {} - {}", this.leftForkId.get(), this.id);
        leftFork.acquire();
        final long duration = (System.nanoTime() - start) / 1_000_000L;
        log.info("Philosher {} is waiting {}ms to take forks.", id, duration);
        takeForkTimer.record(duration, TimeUnit.MILLISECONDS);
        return lease;
    }

    private void releaseForks(Lease lease) throws Exception {
        final long start = System.nanoTime();
        rightFork.release();
        leftFork.release();
        lease.close();
        final long duration = (System.nanoTime() - start) / 1_000_000L;
        log.info("Philosher {} is waiting {}ms to release forks.", id, duration);
        releaseTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    private void think() throws InterruptedException {
        final long start = System.nanoTime();
        Thread.sleep(rnd.nextLong(MIN_RANDOM_TIME_MS, MAX_RANDOM_TIME_MS));
        log.debug("before acquire latch {}", this.id);
        latch.await();
        final long duration = (System.nanoTime() - start) / 1_000_000L;
        log.info("Philosher {} is thinking {}ms.", id, duration);
        thinkTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    private void eat() throws InterruptedException {
        final long duration = rnd.nextLong(MIN_RANDOM_TIME_MS, MAX_RANDOM_TIME_MS);
        log.info("Philosher {} is eating {}ms.", id, duration);
        Thread.sleep(duration);
        eatTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        log.info("Run thread philopher {}", this.id);
        try {
            while (true) {
                think();
                final Lease lease = takeForks();
                eat();
                releaseForks(lease);
            }
        } catch (Exception e) {
            log.error("Philosopher error", e);
            if (client != null && CuratorFrameworkState.STARTED == client.getState()) {
                try {
                    client.delete().guaranteed().forPath(PhilosopherManager.FORKS_PATH + "/" + id);
                } catch (Exception e1) {
                    log.error("Error deleting fork of stopped philosopher", e1);
                }
            }
        }
    }

    private void updateLeftFork(int retry) throws Exception {
        log.debug("Philosopher {} updating left fork.", id);
        final Set<Long> forksIds = forkPathCache.stream()
        .filter(forkNode -> forkNode.getPath().length() > PhilosopherManager.FORKS_PATH.length() + 1)
        .map(forkNode -> forkNode.getPath().substring(PhilosopherManager.FORKS_PATH.length() + 1))
        .filter(forkSubPath -> !forkSubPath.contains("/"))
        .map(forkSubPath -> Long.decode(forkSubPath))
        .collect(Collectors.toSet());

        log.debug("Philosopher {} list forks {}", id, forksIds);

        final long previousId = forksIds.stream()
            .filter(nodeId -> nodeId < this.id)
            .max(Comparator.naturalOrder())
            .orElse(
                forksIds.stream()
                .max(Comparator.naturalOrder())
                .orElse(-1L)
            );

        if (previousId == leftForkId.get()) {
            log.debug("Philosopher {} previous fork {} actual configured left fork {}.", id, previousId, leftForkId.get());
            return;
        }

        log.debug("Philosopher {} previous fork {}", id, previousId);

        final InterProcessMutex tmpLeftFork = leftFork;
        if (tmpLeftFork != null) {
            if (!tmpLeftFork.acquire(UPDATE_FORK_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("Philosopher {} can't acquire lock for update left fork {} by {}.",
                        id, leftForkId.get(), previousId);
                if (retry < RETRY_UPDATE_FORK_LIMIT) {
                    log.warn("Philosopher {} retry update fork : {}", id, (retry + 1));
                    updateLeftFork(retry + 1);
                } else {
                    log.error("Philosopher {} can't update left fork {} by {}.",
                            id, leftForkId.get(), previousId);
                }
                return;
            }
        }
        if (previousId > 0 && previousId != this.id) {
            log.info("Philosopher {} select left fork {}.", id, previousId);
            this.leftFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH + "-mutex/" + previousId);
            this.leftForkId.set(previousId);
            if (this.latch.getCount() > 0) {
                this.latch.countDown();
            }
        } else if (this.leftFork != null) {
            log.info("Philosopher {} remove left fork {}.", id, this.leftFork);
            this.latch = new CountDownLatch(1);
            this.leftFork = null;
        }
        if (tmpLeftFork != null) {
            tmpLeftFork.release();
        }
    }

    private void handleForkPathChange(ChildData childdata) {
        try {
            updateLeftFork(0);
        } catch (Exception e) {
            log.error("Error when updating left fork", e);
        }
    }
    
}
