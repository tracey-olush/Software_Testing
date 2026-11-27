package de.campusshop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tariff-service.mode=simulation")
@ActiveProfiles("inmemory")
public class SystemHTTPTest {
    private static final int LOAD_REQUESTS = 500;
    private static final int LOAD_THREADS = 20;
    private static final long P95_TARGET_MS = 500;
    private static final double MIN_REQUESTS_PER_SECOND = 20;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(30));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> submit(String body) throws Exception {
        return send(request("/dispatch/manifests").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private HttpResponse<String> fetch(String path) throws Exception {
        return send(request(path).GET().build());
    }

    private static String unique() {
        return "UDE0-" + UUID.randomUUID();
    }

    @Test
    void fullShipmentLifecycle() throws Exception {
        String carrier = "FGH-" + UUID.randomUUID();
        String body = "{\"manifestNo\": \"" +
                unique() +
                "\", \"carrier\": {\"carrierId\": \"" +
                carrier +
                "\", \"name\": \"Carrier\", \"contactEmail\": \"ops@example.com\"}, \"dispatchDate\": \"2026-11-20\", \"originWarehouse\": \"WH\", \"parcels\": [{\"tariffCode\": \"TRF-1\", \"weightKg\": 2.5},{\"tariffCode\": \"TRF-1\", \"weightKg\": 2.5}]}";

        HttpResponse<String> created = submit(body);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode record = json.readTree(created.body());
        String ref = record.get("shipmentRef").asText();
        assertThat(record.get("totalCharge").decimalValue()).isEqualByComparingTo("16.50");

        assertThat(fetch("/shipments/" + ref).statusCode()).isEqualTo(200);
        assertThat(fetch("/shipments/" + ref + "/manifest").body()).isEqualTo(body);
        assertThat(fetch("/carriers/" + carrier + "/shipments").body()).contains(ref);
        assertThat(submit(body).statusCode()).isEqualTo(409);

        assertThat(send(request("/shipments/" + ref).DELETE().build()).statusCode()).isEqualTo(204);
        assertThat(fetch("/shipments/" + ref).statusCode()).isEqualTo(404);
        assertThat(fetch("/carriers/" + carrier + "/shipments").body()).doesNotContain(ref);
        assertThat(submit(body).statusCode()).as("number is reusable after delete (assumption)").isEqualTo(201);
    }

    @Test
    void healthIsUpWithoutDetails() throws Exception {
        HttpResponse<String> health = fetch("/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP").doesNotContain("diskSpace");
    }

    @Test
    void errorReportsFollowTheChecklistAndHideInternals() throws Exception {
        List<HttpResponse<String>> errors = List.of(
                submit("{"),
                submit("""
                {"manifestNo": "", "carrier": {"carrierId": "SJEF-89E", "name": "Carrier",
                 "contactEmail": "ops@example.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "WH",
                 "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}"""),
                fetch("/shipments/unknown"),
                fetch("/unknown"));

        for (HttpResponse<String> error : errors) {
            JsonNode report = json.readTree(error.body());
            assertThat(report.path("errorCode").asText()).matches("[A-Z_]+");
            assertThat(report.path("detail").asText()).isNotBlank();
            assertThat(report.path("occurredAt").asText()).isNotBlank();
            assertThat(report.path("path").asText()).startsWith("/");
            assertThat(error.body()).doesNotContain("Exception", "de.campusshop", "\tat ");
        }
    }

    @Test
    void concurrentLoadMeetsTargets() throws Exception {
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < LOAD_REQUESTS; i++) {
            tasks.add(() -> {
                long start = System.nanoTime();
                int status = submit("{\"manifestNo\": \"" +
                        unique() +
                        "\", \"carrier\": {\"carrierId\": \"SJEF-89E\", \"name\": \"Carrier\", \"contactEmail\": \"ops@example.com\"}, \"dispatchDate\": \"2026-11-20\", \"originWarehouse\": \"WH\", \"parcels\": [{\"tariffCode\": \"TRF-1\", \"weightKg\": 2.5}]}").statusCode();
                long millis = (System.nanoTime() - start) / 1_000_000;
                return status == 201 ? millis : -1L;
            });
        }
        var pool = Executors.newFixedThreadPool(LOAD_THREADS);
        List<Long> latencies = new ArrayList<>();
        int errors = 0;
        long start = System.nanoTime();
        try {
            for (Future<Long> result : pool.invokeAll(tasks)) {
                try {
                    long millis = result.get();
                    if (millis < 0) {
                        errors++;
                    } else {
                        latencies.add(millis);
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    errors++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        Collections.sort(latencies);
        assertThat(latencies).isNotEmpty();
        long p95 = latencies.get((int) Math.ceil(0.95 * latencies.size()) - 1);
        double throughput = latencies.size() / seconds;
        System.out.printf("LOAD n=%d errors=%d p95=%dms throughput=%.1f/s%n",
                LOAD_REQUESTS, errors, p95, throughput);

        assertThat(errors).isZero();
        assertThat(p95).isLessThanOrEqualTo(P95_TARGET_MS);
        assertThat(throughput).isGreaterThanOrEqualTo(MIN_REQUESTS_PER_SECOND);
    }
}
