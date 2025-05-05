package dev.boissin.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import dev.boissin.exception.InvalidStatusCodeException;
import dev.boissin.service.PhilosopherManager;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

public class EventsController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final PhilosopherManager philosopherManager;

    public EventsController(PhilosopherManager philosopherManager) {
        this.philosopherManager = philosopherManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        log.debug("Enter event controller endpoint");
        try {
            final String result;
            if (philosopherManager.getLeaderSelector().hasLeadership()) {
                result = getEvents();
            } else {
                result = proxyCallLeader();
            }
            log.debug("Result : {}", result);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, result.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(result.getBytes());
            }
        } catch (Exception e) {
            log.error("Error getting events", e);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(500, e.getMessage().getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(e.getMessage().getBytes());
            }
        }
    }

    private String getEvents() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(philosopherManager.getStateEventsChecker().getResult());
        }
    }

    private String proxyCallLeader() throws Exception {
        final String leaderIp;
        try (JsonReader reader = Json.createReader(
            new StringReader(philosopherManager.getLeaderSelector().getLeader().getId()))) {
	        leaderIp = reader.readObject().getString("ip");
	    }
        log.debug("Leader ip address : {}", leaderIp);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://%s:8080/events".formatted(leaderIp)))
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                throw new InvalidStatusCodeException("Invalid status code");
            }
        }
    }

}
