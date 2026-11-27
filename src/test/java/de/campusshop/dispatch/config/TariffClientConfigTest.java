package de.campusshop.dispatch.config;

import com.sun.net.httpserver.HttpServer;
import de.campusshop.dispatch.client.RestTariffClient;
import de.campusshop.dispatch.mapper.ManifestMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(20)
public class TariffClientConfigTest {

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void fastResponseReturnsTheTariff() {
        server.createContext("/tariffs/", exchange -> {
            byte[] body = """
                    {"tariffCode": "TRF-1", "serviceName": "Standard", "baseFee": 4.50,
                    "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        RestClient restClient = new TariffClientConfig()
                .tariffRestClient(RestClient.builder(), new TariffServiceProperties("rest", url, 500));

        assertThat(new RestTariffClient(restClient, new ManifestMapper()).findTariff("TRF-1")).isPresent();
    }

    @Test
    void slowResponseTimesOutAndGivesEmpty() {
        server.createContext("/tariffs/", exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            byte[] body = """
                    {"tariffCode": "TRF-1", "serviceName": "Standard", "baseFee": 4.50,
                    "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        RestClient restClient = new TariffClientConfig()
                .tariffRestClient(RestClient.builder(), new TariffServiceProperties("rest", url, 500));
        long start = System.nanoTime();

        assertThat(new RestTariffClient(restClient, new ManifestMapper()).findTariff("TRF-1")).isEmpty();

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isBetween(Duration.ofMillis(450), Duration.ofMillis(3_000));
    }

    @Test
    void droppedConnectionGivesEmpty() {
        server.createContext("/tariffs/", exchange -> exchange.close());
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        RestClient restClient = new TariffClientConfig()
                .tariffRestClient(RestClient.builder(), new TariffServiceProperties("rest", url, 500));

        assertThat(new RestTariffClient(restClient, new ManifestMapper()).findTariff("TRF-1")).isEmpty();
    }

    @Test
    void unreachableServiceGivesEmpty() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        server.stop(0);
        RestClient restClient = new TariffClientConfig()
                .tariffRestClient(RestClient.builder(), new TariffServiceProperties("rest", url, 500));

        assertThat(new RestTariffClient(restClient, new ManifestMapper()).findTariff("TRF-1")).isEmpty();
    }

}
