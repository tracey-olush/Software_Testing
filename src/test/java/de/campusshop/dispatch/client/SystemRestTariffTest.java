package de.campusshop.dispatch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tariff-service.mode=rest", "tariff-service.timeout-ms=500"})
@ActiveProfiles("inmemory")
public class SystemRestTariffTest {
    private static final HttpServer STUB = startStub();

    @LocalServerPort
    private int port;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/tariffs/", exchange -> {
                String code = exchange.getRequestURI().getPath().substring("/tariffs/".length());
                if (code.equals("SLOW")) {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                int status = code.equals("DOWN") ? 503 : 200;
                byte[] body = ("""
                        {"tariffCode":"%s","serviceName":"Standard","baseFee":4.50,
                         "pricePerKg":1.20,"fuelSurchargeRate":0.10,"transitDays":3}""")
                        .formatted(code).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void tariffUrl(DynamicPropertyRegistry registry) {
        registry.add("tariff-service.url", () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Test
    void healthyDownAndSlowTariffsStillGiveARegisteredShipment() throws Exception {
        String parcels = """
                [{"tariffCode": "TRF-1", "weightKg": 2.5},
                 {"tariffCode": "DOWN", "weightKg": 2.5},
                 {"tariffCode": "SLOW", "weightKg": 2.5}]""";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/dispatch/manifests"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"manifestNo\": \"" +
                                "M-" + UUID.randomUUID() +
                                "\", \"carrier\": {\"carrierId\": \"C-1\", \"name\": \"Carrier\", \"contactEmail\": \"ops@example.com\"}, \"dispatchDate\": \"2026-11-20\", \"originWarehouse\": \"WH\", \"parcels\": " +
                                parcels +
                                "}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode record = new ObjectMapper().readTree(response.body());
        java.util.List<String> ratings = new java.util.ArrayList<>();
        record.get("parcels").forEach(parcel -> ratings.add(parcel.get("rating").asText()));
        assertThat(ratings).containsExactly("RATED", "UNRATED", "UNRATED");
        assertThat(record.get("totalCharge").decimalValue()).isEqualByComparingTo("8.25");
    }
}
