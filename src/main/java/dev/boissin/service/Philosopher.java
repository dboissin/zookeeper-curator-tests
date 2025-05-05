package dev.boissin.service;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
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
import org.apache.curator.framework.recipes.shared.VersionedValue;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.model.Event;
import dev.boissin.model.Event.EatEvent;
import dev.boissin.model.Event.ThinkEvent;
import dev.boissin.queue.DiningPhilosophersQueue;
import dev.boissin.util.WorkerContext;
import io.micrometer.core.instrument.Timer;

public class Philosopher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Philosopher.class);

    private static final int MAX_RANDOM_TIME_MS = 800;
    private static final int MIN_RANDOM_TIME_MS = 200;
    private static final int RETRY_UPDATE_FORK_LIMIT = 30;
    private static final long UPDATE_FORK_ACQUIRE_TIMEOUT_MS = 100L;

    private final Random rnd;
    private final long id;
    private final SharedCount sharedCount;
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
    private ReentrantLock updateLeftForkMutex = new ReentrantLock();
    private AtomicLong leftForkId = new AtomicLong(-1L);
    private ExecutorService updateForkExecutorService;
    private final DiningPhilosophersQueue queue;
    private AtomicBoolean running = new AtomicBoolean(true);

    public Philosopher(long id, SharedCount sharedCount, DiningPhilosophersQueue queue) throws Exception {
        this.rnd = new Random();
        this.id = id;
        this.sharedCount = sharedCount;
        this.queue = queue;
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
        this.rightFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH_MUTEX + id);

        this.updateForkExecutorService = Executors.newSingleThreadExecutor();
        this.forkPathCache = CuratorCache.build(client, PhilosopherManager.FORKS_PATH);
        final CuratorCacheListener listener = CuratorCacheListener.builder()
            .forCreates(this::handleForkPathChange)
            .forDeletes(this::handleForkPathChange)
            .build();
        this.forkPathCache.listenable().addListener(listener);
        this.forkPathCache.start();
    }

    private Lease takeForks() throws Exception {
        final long start = System.currentTimeMillis();
        log.debug("before acquire semaphore {}", this.id);
        final Lease lease = this.semaphore.acquire();
        log.debug("before acquire right mutex {}", this.id);
        rightFork.acquire();
        log.debug("before acquire update left mutex : {} - {}", this.leftForkId.get(), this.id);
        updateLeftForkMutex.lock();
        log.debug("before acquire left mutex : {} - {}", this.leftForkId.get(), this.id);
        leftFork.acquire();
        recordDuration(start, "Philosher {} is waiting {}ms to take forks.", takeForkTimer);
        return lease;
    }

    private void releaseForks(Lease lease) throws Exception {
        final long start = System.currentTimeMillis();
        rightFork.release();
        leftFork.release();
        updateLeftForkMutex.unlock();
        lease.close();
        recordDuration(start, "Philosher {} is waiting {}ms to release forks.", releaseTimer);
    }

    private void think() throws InterruptedException {
        final long start = System.currentTimeMillis();
        Thread.sleep(rnd.nextLong(MIN_RANDOM_TIME_MS, MAX_RANDOM_TIME_MS));
        log.debug("before acquire latch {}", this.id);
        latch.await();
        recordDuration(start, "Philosher {} is thinking {}ms.", thinkTimer, ThinkEvent.class);
    }

    private void eat() throws InterruptedException {
        final long start = System.currentTimeMillis();
        Thread.sleep(rnd.nextLong(MIN_RANDOM_TIME_MS, MAX_RANDOM_TIME_MS));
        recordDuration(start, "Philosher {} is eating {}ms.", eatTimer, EatEvent.class);
    }

    @Override
    public void run() {
        log.info("Run thread philopher {}", this.id);
        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                think();
                final Lease lease = takeForks();
                eat();
                releaseForks(lease);
            }
        } catch (Exception e) {
            log.error("Philosopher error", e);
        } finally {
            close();
        }
    }

    private void updateLeftFork(int retry) throws Exception {
        log.debug("Philosopher {} updating left fork.", id);
        final Set<Long> forksIds = forkPathCache.stream()
        .filter(forkNode -> forkNode.getPath().length() > PhilosopherManager.FORKS_PATH.length() + 1)
        .map(forkNode -> forkNode.getPath().substring(PhilosopherManager.FORKS_PATH.length() + 1))
        .filter(forkSubPath -> !forkSubPath.contains("/"))
        .map(Long::decode)
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

        if (!updateLeftForkMutex.tryLock(UPDATE_FORK_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
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
        if (previousId > 0 && previousId != this.id) {
            log.info("Philosopher {} select left fork {}.", id, previousId);
            this.leftFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH_MUTEX + previousId);
            this.leftForkId.set(previousId);
            if (this.latch.getCount() > 0) {
                this.latch.countDown();
            }
            // update semaphore leases counter at half number of fork
            setLeasesCounter(forksIds.size() -1);
        } else if (this.leftFork != null) {
            log.error("Philosopher {} no longer has a left fork {}. So, it will be stopped.", id, this.leftFork);
            running.set(false);
        }
        updateLeftForkMutex.unlock();
    }

    private void handleForkPathChange(ChildData childdata) {
        try {
            updateForkExecutorService.submit(() -> {
                try {
                    updateLeftFork(0);
                } catch (Exception e) {
                    log.error("Error when updating left fork", e);
                }
            });
        } catch (Exception e) {
            log.error("Error when submit updating left fork", e);
        }
    }

    private void recordDuration(final long start, String logMessage, Timer timer) {
        recordDuration(start, logMessage, timer, null);
    }

    private void recordDuration(final long start, String logMessage, Timer timer, Class<? extends Event> eventType) {
        final long end = System.currentTimeMillis();
        final long duration = (end - start);
        log.info(logMessage, id, duration);
        timer.record(duration, TimeUnit.MILLISECONDS);
        if (eventType != null) {
            try {
                final Constructor<? extends Event> constructor = eventType.getDeclaredConstructor(
                long.class, long.class, long.class, long.class, long.class);
                final Event event = constructor.newInstance(start, end, id, id, leftForkId.get());
                queue.sendEvent(event);
            } catch (Exception e) {
                log.error("Error generating event", e);
            }
        }
    }

    private void setLeasesCounter(int value) throws Exception {
        boolean updated = (sharedCount.getCount() == value);
        while (!updated) {
            sharedCount.getCount();
            final VersionedValue<Integer> vv = sharedCount.getVersionedValue();
            updated = sharedCount.trySetCount(vv, value);
            if (updated) {
                log.info("Semaphore shared counter : {}", sharedCount.getCount());
            }
        }
    }

    public void close() {
        running.set(false);
        if (client != null && CuratorFrameworkState.STARTED == client.getState()) {
            try {
                if (forkPathCache != null) {
                    forkPathCache.close();
                }
                final String path = PhilosopherManager.FORKS_PATH + "/" + id;
                if (client.checkExists().forPath(path) != null) {
                    client.delete().guaranteed().forPath(path);
                }
                if (updateForkExecutorService != null) {
                    updateForkExecutorService.close();
                }
            } catch (Exception e1) {
                log.error("Error deleting fork of stopped philosopher", e1);
            }
        }
    }

}
