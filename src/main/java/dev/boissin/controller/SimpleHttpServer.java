package dev.boissin.controller;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class SimpleHttpServer {

    private final HttpServer server;

    public SimpleHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
    }

    public void addRoute(String route, HttpHandler handler) {
        server.createContext(route, handler);
    }

    public void close() {
        server.stop(3);
    }

}
