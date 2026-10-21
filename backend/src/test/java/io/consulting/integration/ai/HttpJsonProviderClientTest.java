package io.consulting.integration.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.consulting.integration.ai.HttpJsonProviderClient;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpJsonProviderClientTest {

    @TempDir
    Path directory;

    @Test
    void configAdapterValidatesVerbatimCoverageAndHidesCredentials() throws Exception {
        var response = new AtomicReference<>(
            "{\"segments\":[{\"source\":\"Today 2h testing Audiom\",\"project\":\"Audiom\",\"category\":\"Testing and Debugging\"}]}"
        );
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            var json = new ObjectMapper();
            var path = directory.resolve("providers.json");
            Files.writeString(
                path,
                json.writeValueAsString(
                    Map.of(
                        "providers",
                        List.of(
                            Map.of(
                                "id",
                                "test",
                                "name",
                                "Fixture",
                                "enabled",
                                true,
                                "model",
                                "fixture",
                                "endpoint",
                                "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                                "credentials",
                                Map.of("KEY", "fixture-secret-never-exposed"),
                                "headers",
                                Map.of("Authorization", "Bearer {{secret:KEY}}"),
                                "request",
                                Map.of("input", "{{input}}", "system", "{{instructions}}"),
                                "responsePointer",
                                ""
                            )
                        )
                    )
                )
            );
            var providers = new HttpJsonProviderClient(json, path.toString());
            assertFalse(providers.available().toString().contains("fixture-secret"));
            assertEquals(1, providers.extract("test", "Today 2h testing Audiom", List.of()).size());
            assertEquals("Bearer fixture-secret-never-exposed", authorization.get());
            assertThrows(RuntimeException.class, () ->
                providers.extract("test", "-Today 2h testing Audiom", List.of())
            );
            assertThrows(RuntimeException.class, () ->
                providers.extract(
                    "test",
                    "Today 2h testing Audiom\nYesterday 1h research",
                    List.of()
                )
            );
            response.set("{\"segments\":[{\"source\":\"Invented 8h work\"}]}");
            assertThrows(RuntimeException.class, () ->
                providers.extract("test", "Today 2h testing Audiom", List.of())
            );
            Files.writeString(path, "{\"providers\":[]}");
            assertTrue(providers.available().isEmpty());
            assertThrows(RuntimeException.class, () ->
                providers.extract("test", "Today 2h testing Audiom", List.of())
            );
        } finally {
            server.stop(0);
        }
    }
}
