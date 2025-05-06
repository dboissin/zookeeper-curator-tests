/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class WorkerContext {

    private static final Logger log = LoggerFactory.getLogger(WorkerContext.class);
    private static final String WORKER_COUNTER_PATH = "/counters/workers";
    private static final String DEFAULT_NAMESPACE = "test-zk-project";
    private static final String NAMESPACE_ENV = "NAMESPACE";
    private static final String SERVICE_NAME = "dining-philosophers";

    private final PrometheusMeterRegistry prometheusRegistry;

    private CuratorFramework client;
    private AtomicReference<Long> workerId = new AtomicReference<>(null);

    private WorkerContext() {
        prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

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

    public String getServiceName() {
        return SERVICE_NAME;
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

    public String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("Error getting host ip", e);
            return "";
        }
    }

    public String getIdAndHost() {
        return """
                { "id":"%s", "ip":"%s"}""".formatted(getWorkerId(), getHostIp());
    }

    public String getServiceUrl() {
        return "http://%s:8080".formatted(getHostIp());
    }

    public void traefikRegisterService(String router, String pathPrefix) throws Exception {
        final String path = "/traefik/http/services/%s/loadbalancer/servers/%s/url"
                .formatted(getServiceName(), getWorkerId());
        client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                .forPath(path, getServiceUrl().getBytes(StandardCharsets.UTF_8));
        try {
            client.create().creatingParentsIfNeeded().forPath(
                "/traefik/http/services/%s/loadbalancer/healthcheck/path".formatted(getServiceName()),
                "/health/readiness".getBytes(StandardCharsets.UTF_8));
        } catch (NodeExistsException e) {
            log.debug("Health check path already created", e);
        }
        try {
            client.create().creatingParentsIfNeeded().forPath(
                "/traefik/http/routers/%s/rule".formatted(router),
                "PathPrefix(`%s`)".formatted(pathPrefix).getBytes(StandardCharsets.UTF_8));
        } catch (NodeExistsException e) {
            log.debug("Router path already created", e);
        }
        try {
            client.create().creatingParentsIfNeeded().forPath(
                "/traefik/http/routers/%s/service".formatted(router),
                getServiceName().getBytes(StandardCharsets.UTF_8));
        } catch (NodeExistsException e) {
            log.debug("Service path already created", e);
        }
        client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                .forPath("/traefik/%s-%s-%d".formatted(router, getServiceName(), getWorkerId()),
                getIdAndHost().getBytes(StandardCharsets.UTF_8));
    }

    public CuratorFramework getClient() {
        return client;
    }

    public MeterRegistry getMeterRegistry() {
        return prometheusRegistry;
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }

}
