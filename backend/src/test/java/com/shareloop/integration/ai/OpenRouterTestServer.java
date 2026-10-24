package com.shareloop.integration.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class OpenRouterTestServer implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final JsonMapper mapper = new JsonMapper();

    OpenRouterTestServer(String response, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    OpenRouterGateway gateway() {
        return new OpenRouterGateway(
                mapper,
                HttpClient.newHttpClient(),
                "test-key",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"));
    }

    JsonNode body() {
        return mapper.readTree(requestBody.get());
    }

    String authorization() {
        return authorization.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
