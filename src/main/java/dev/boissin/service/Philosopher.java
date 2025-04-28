package dev.boissin.service;

import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.util.WorkerContext;

public class Philosopher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Philosopher.class);

    private final Random rnd;
    private final long id;
    private final InterProcessSemaphoreV2 semaphore;
    private final CuratorFramework client;

    // TODO replace by micrometer counters
    private AtomicLong totalThinkDuration = new AtomicLong(0L);
    private AtomicLong totalEatDuration = new AtomicLong(0L);
    private AtomicLong totalTakeForkDuration = new AtomicLong(0L);
    private AtomicLong totalReleaseForkDuration = new AtomicLong(0L);

    private final InterProcessMutex rightFork;
    private InterProcessMutex leftFork;
    private CuratorCache forkPathCache;
    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicLong leftForkId = new AtomicLong(-1L);

    public Philosopher(long id, long seed, SharedCount sharedCount) throws Exception {
        this.rnd = new Random(seed);
        this.id = id;
        this.client = WorkerContext.getContext().getClient();
        this.semaphore = new InterProcessSemaphoreV2(client, PhilosopherManager.SEMAPHORE_PATH, sharedCount);
        this.rightFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH + "/" + id + "/fork");
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
        totalTakeForkDuration.addAndGet(duration);
        return lease;
    }

    private void releaseForks(Lease lease) throws Exception {
        final long start = System.nanoTime();
        rightFork.release();
        leftFork.release();
        lease.close();
        final long duration = (System.nanoTime() - start) / 1_000_000L;
        log.info("Philosher {} is waiting {}ms to release forks.", id, duration);
        totalReleaseForkDuration.addAndGet(duration);
    }

    private void think() throws InterruptedException {
        final long start = System.nanoTime();
        Thread.sleep(rnd.nextLong(200, 800));
        log.debug("before acquire latch {}", this.id);
        latch.await();
        final long duration = (System.nanoTime() - start) / 1_000_000L;
        log.info("Philosher {} is thinking {}ms.", id, duration);
        totalThinkDuration.addAndGet(duration);
    }

    private void eat() throws InterruptedException {
        final long duration = rnd.nextLong(200, 800);
        log.info("Philosher {} is eating {}ms.", id, duration);
        Thread.sleep(duration);
        totalEatDuration.addAndGet(duration);
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
        }
    }

    private void updateLeftFork() throws Exception {
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

        // InterProcessMutex tmpLeftFork = leftFork;
        // if (tmpLeftFork != null) {
        //     tmpLeftFork.acquire();
        // }
        if (previousId > 0 && previousId != this.id) {
            log.info("Philosopher {} select left fork {}.", id, previousId);
            this.leftFork = new InterProcessMutex(client, PhilosopherManager.FORKS_PATH + "/" + previousId);
            this.leftForkId.set(previousId);
            if (this.latch.getCount() > 0) {
                this.latch.countDown();
            }
        } else if (this.leftFork != null) {
            log.info("Philosopher {} remove left fork {}.", id, this.leftFork);
            this.latch = new CountDownLatch(1);
            this.leftFork = null;
        }
        // if (tmpLeftFork != null) {
        //     tmpLeftFork.release();
        // }
    }

    private void handleForkPathChange(ChildData childdata) {
        try {
            updateLeftFork();
        } catch (Exception e) {
            log.error("Error when updating left fork", e);
        }
    }
    
}
