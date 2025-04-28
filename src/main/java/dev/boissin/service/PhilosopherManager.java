package dev.boissin.service;

import java.io.IOException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.SharedCountListener;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.recipes.shared.VersionedValue;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.util.WorkerContext;

public class PhilosopherManager implements SharedCountListener {

    private static final Logger log = LoggerFactory.getLogger(PhilosopherManager.class);

    static final String SEMAPHORE_PATH = "/philosophers/semaphore";
    static final String LEASE_COUNT_PATH = "/philosophers/lease-count";
    static final String FORKS_PATH = "/philosophers/forks";

    private final long id;
    private final long seed;
    private final Philosopher[] localPhilosophers;
    
    private final SharedCount sharedCount;

    public PhilosopherManager(int workerThreadNb, long seed) throws Exception {
        this.id = WorkerContext.getContext().getWorkerId() * 1000;
        localPhilosophers = new Philosopher[workerThreadNb];
        this.seed = seed;
        final CuratorFramework client = WorkerContext.getContext().getClient();
        this.sharedCount = new SharedCount(client, LEASE_COUNT_PATH, -1);
        this.sharedCount.addListener(this);
        this.sharedCount.start();
    }

    public void launch() throws Exception {
        int countLocalPilosophers = 0;
        for (int i = 0; i < localPhilosophers.length; i++) {
            final Philosopher philosopher = new Philosopher(id + i, seed, sharedCount);
            localPhilosophers[i] = philosopher;
            new Thread(philosopher).start();
            countLocalPilosophers++;
        }
        incrementLeasesCounter(countLocalPilosophers);
    }

    private void incrementLeasesCounter(int value) throws Exception {
        if (value == 0) return;
        boolean updated = false;
        while (!updated) {
            sharedCount.getCount();
            final VersionedValue<Integer> vv = sharedCount.getVersionedValue();
            updated = sharedCount.trySetCount(vv, vv.getValue() + value);    
        }
        log.info("Semaphore shared counter : {}", sharedCount.getCount());
    }

    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        log.info("SharedCount state change : {}", newState);
    }

    @Override
    public void countHasChanged(SharedCountReader sharedCount, int newCount) throws Exception {
        log.info("SharedCount value change : {}", newCount);
    }

    public void close() throws IOException {
        if (sharedCount != null) {
            sharedCount.close();
        }
    }

}
