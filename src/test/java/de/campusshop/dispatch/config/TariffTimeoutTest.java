package de.campusshop.dispatch.config;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.campusshop.dispatch.client.RestTariffClient;
import de.campusshop.dispatch.mapper.ManifestMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;


@Timeout(20)
public class TariffTimeoutTest {
    private static final int TIMEOUT_MS = 500;
    private static final String JSON = """
            {"tariffCode":"TRF-1","serviceName":"Standard","baseFee":4.50,
             "pricePerKg":1.20,"fuelSurchargeRate":0.10,"transitDays":3}""";

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static HttpHandler jsonAfter(long delayMs) {
        return exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            byte[] body = JSON.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        };
    }

    private RestTariffClient client() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        RestClient restClient = new TariffClientConfig()
                .tariffRestClient(RestClient.builder(), new TariffServiceProperties("rest", url, TIMEOUT_MS));
        return new RestTariffClient(restClient, new ManifestMapper());
    }

    @Test
    void fastResponseSucceeds() {
        server.createContext("/tariffs/", jsonAfter(0));

        assertThat(client().findTariff("TRF-1")).isPresent();
    }

    @Test
    void slowResponseTimesOutAndGivesEmpty() {
        server.createContext("/tariffs/", jsonAfter(5_000));
        long start = System.nanoTime();

        assertThat(client().findTariff("TRF-1")).isEmpty();

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isBetween(Duration.ofMillis(TIMEOUT_MS - 50), Duration.ofMillis(3_000));
    }

    @Test
    void droppedConnectionGivesEmpty() {
        server.createContext("/tariffs/", exchange -> exchange.close());

        assertThat(client().findTariff("TRF-1")).isEmpty();
    }

    @Test
    void unreachableServiceGivesEmpty() {
        RestTariffClient client = client();
        server.stop(0);

        assertThat(client.findTariff("TRF-1")).isEmpty();
    }
}
