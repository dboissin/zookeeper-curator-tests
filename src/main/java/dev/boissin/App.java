/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.boissin;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.controller.EventsHandler;
import dev.boissin.controller.MetricsHandler;
import dev.boissin.controller.ReadinessHandler;
import dev.boissin.controller.SimpleHttpServer;
import dev.boissin.controller.ViewHandler;
import dev.boissin.service.PhilosopherManager;
import dev.boissin.util.WorkerContext;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static void main(String[] args) {
        try {
            final String appId;
            Thread.sleep(3000L);
            final WorkerContext context = WorkerContext.getContext();
            context.init(System.getenv("ZOOKEEPER_CONNECT"));
            appId = "worker-" + context.getWorkerId();

            final PhilosopherManager dinner = new PhilosopherManager(Integer.parseInt(Optional
                    .ofNullable(System.getenv("PHILOSOPHERS_NB")).orElse("3")));
            dinner.launch();

            final SimpleHttpServer simpleHttpServer = new SimpleHttpServer();
            simpleHttpServer.addRoute("/health/metrics", new MetricsHandler(
                    (PrometheusMeterRegistry) context.getMeterRegistry()));
            simpleHttpServer.addRoute("/events", new EventsHandler(dinner));
            simpleHttpServer.addRoute("/", new ViewHandler());
            simpleHttpServer.addRoute("/health/readiness", new ReadinessHandler());
            simpleHttpServer.start();


            final String router = Optional.ofNullable(System.getenv("LB_ROUTER"))
                    .orElse("default-router");
            final String pathPrefix = Optional.ofNullable(System.getenv("LB_PATH_PREFIX"))
                    .orElse("/" + context.getServiceName());
            context.traefikRegisterService(router, pathPrefix);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Application {} is shutting down...", appId);
                    if (dinner != null) {
                        dinner.close();
                    }
                    if (simpleHttpServer != null) {
                        simpleHttpServer.close();
                    }
                    WorkerContext.getContext().close();
                    LATCH.countDown();
                } catch (IOException ioe) {
                    logger.error("Error when stopping parser queue", ioe);
                }
            }));

            logger.info("Starting as worker with ID: {}", appId);
            logger.info("Worker is running. Send SIGTERM to gracefully shut down.");
            try {
                LATCH.await();
            } catch (InterruptedException e) {
                logger.warn("Worker was interrupted while waiting");
            }
            logger.info("Worker has completed shutdown process");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error in main " + e.getMessage(), e);
            printHelp();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar app.jar [options]");
        System.out.println("Options:");
    }

}
