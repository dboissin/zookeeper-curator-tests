package dev.boissin.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class MetricsController implements HttpHandler {

    private final HttpServer server;
    private final PrometheusMeterRegistry prometheusRegistry;

    public MetricsController(PrometheusMeterRegistry prometheusMeterRegistry) throws IOException {
        this.prometheusRegistry = prometheusMeterRegistry;
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health/metrics", this);
        server.start();
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

    public void close() {
        server.stop(3);
    }

}
