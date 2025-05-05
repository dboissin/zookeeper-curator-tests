package dev.boissin.controller;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class MetricsController implements HttpHandler {

    private final PrometheusMeterRegistry prometheusRegistry;

    public MetricsController(PrometheusMeterRegistry prometheusMeterRegistry) {
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
