package dev.boissin.service;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.SharedCountListener;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.model.Event;
import dev.boissin.queue.DiningPhilosophersQueue;
import dev.boissin.util.WorkerContext;

public class PhilosopherManager implements SharedCountListener, QueueConsumer<Event>, LeaderSelectorListener {

    private static final Logger log = LoggerFactory.getLogger(PhilosopherManager.class);

    static final String SEMAPHORE_PATH = "/philosophers/semaphore";
    static final String LEASE_COUNT_PATH = "/philosophers/lease-count";
    static final String FORKS_PATH = "/philosophers/forks";
    static final String FORKS_PATH_MUTEX = FORKS_PATH + "-mutex/";
    private static final String LEADER_ELECTION_PATH = "/philosophers/leader";

    private final CuratorFramework client;

    private final long id;
    private Philosopher[] localPhilosophers;
    private DiningPhilosophersQueue queue;
    private final SharedCount sharedCount;
    private final CountDownLatch stopLeaderLatch = new CountDownLatch(1);
    private final CountDownLatch electionDoneLatch = new CountDownLatch(1);
    private final LeaderSelector leaderSelector;
    private StateEventsChecker stateEventsChecker;
    private final int workerThreadNb;

    public PhilosopherManager(int workerThreadNb) throws Exception {
        this.workerThreadNb = workerThreadNb;
        this.id = WorkerContext.getContext().getWorkerId() * 1000;
        this.client = WorkerContext.getContext().getClient();

        this.leaderSelector = new LeaderSelector(client, LEADER_ELECTION_PATH, this);
        this.leaderSelector.setId(WorkerContext.getContext().getIdAndHost());
        this.leaderSelector.start();

        this.sharedCount = new SharedCount(client, LEASE_COUNT_PATH, 0);
        this.sharedCount.addListener(this);
        this.sharedCount.start();
    }

    public void launch() throws Exception {
        electionDoneLatch.await(1, TimeUnit.SECONDS);
        if (leaderSelector.hasLeadership()) {
            return; // don't instanciate philosphers on leader instance
        }
        queue = new DiningPhilosophersQueue(client, null);

        localPhilosophers = new Philosopher[workerThreadNb];
        for (int i = 0; i < localPhilosophers.length; i++) {
            final Philosopher philosopher = new Philosopher(id + i, sharedCount, this.queue);
            localPhilosophers[i] = philosopher;
            new Thread(philosopher).start();
        }
    }

    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        if (!newState.isConnected()) {
            stopLeaderLatch.countDown();
            log.warn("Service {} lost curator connection", id);
        }
    }

    @Override
    public void countHasChanged(SharedCountReader sharedCount, int newCount) throws Exception {
        log.info("SharedCount value change : {}", newCount);
    }

    private void closePhilosophers() {
        if (localPhilosophers != null) {
            for (Philosopher p: localPhilosophers) {
                p.close();
            }
         }
    }

    public void close() throws IOException {
        closePhilosophers();
        if (sharedCount != null) {
            sharedCount.close();
        }
        if (queue != null) {
            queue.close();
        }
        if (leaderSelector != null) {
            leaderSelector.close();
        }
        if (stateEventsChecker != null) {
            stateEventsChecker.close();
        }
    }

    @Override
    public void consumeMessage(Event message) throws Exception {
        stateEventsChecker.addEvent(message);
    }

    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        log.info("Instance {} take leadership.", id);
        try {
            closePhilosophers();
            stateEventsChecker = new StateEventsChecker();
            Thread.startVirtualThread(stateEventsChecker);
            if (queue != null) {
                queue.close();
            }
            queue = new DiningPhilosophersQueue(client, PhilosopherManager.this);
            electionDoneLatch.countDown();
            stopLeaderLatch.await();
        } finally {
            log.info("Service {} lost leadership", id);
            close(); // TODO add reconnect
        }
    }

    public LeaderSelector getLeaderSelector() {
        return leaderSelector;
    }

    public StateEventsChecker getStateEventsChecker() {
        return stateEventsChecker;
    }

}