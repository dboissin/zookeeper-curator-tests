/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

 package dev.boissin.controller;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class MetricsHandler implements HttpHandler {

    private final PrometheusMeterRegistry prometheusRegistry;

    public MetricsHandler(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusRegistry = prometheusMeterRegistry;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String response = prometheusRegistry.scrape();
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

}
