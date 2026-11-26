package de.campusshop.dispatch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.Tariff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestTariffClientTest {

    private static final String BASE_URL = "http://tariffs.test";

    private MockRestServiceServer server;
    private RestTariffClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTariffClient(builder.build(), new ManifestMapper());
    }

    @Test
    void returnsTheTariffOnSuccess() {
        String json = """
                {"tariffCode": "TRF-1", "serviceName": "Standard", "baseFee": 4.50,
                 "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                """;
        server.expect(requestTo(BASE_URL + "/tariffs/TRF-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<Tariff> result = client.findTariff("TRF-1");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("TRF-1");
        assertThat(result.get().serviceName()).isEqualTo("Standard");
        assertThat(result.get().baseFee()).isEqualByComparingTo("4.50");
        assertThat(result.get().pricePerKg()).isEqualByComparingTo("1.20");
        assertThat(result.get().fuelSurchargeRate()).isEqualByComparingTo("0.10");
        server.verify();
    }

    @Test
    void notFoundGivesAnEmptyResult() {
        server.expect(requestTo(BASE_URL + "/tariffs/TRF-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findTariff("TRF-1")).isEmpty();
        server.verify();
    }
}