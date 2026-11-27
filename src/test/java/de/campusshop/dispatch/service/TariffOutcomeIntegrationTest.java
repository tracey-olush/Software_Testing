package de.campusshop.dispatch.service;
import de.campusshop.dispatch.client.RestTariffClient;
import de.campusshop.dispatch.dto.CarrierDto;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.Rating;
import de.campusshop.dispatch.repository.InMemoryManifestArchive;
import de.campusshop.dispatch.repository.InMemoryShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class TariffOutcomeIntegrationTest {
    private InMemoryShipmentRepository shipments;
    private InMemoryManifestArchive archive;
    private MockRestServiceServer server;
    private ShipmentRegistrationService service;

    private final byte[] raw = "{}".getBytes(StandardCharsets.UTF_8);
    private final DispatchManifest manifest = new DispatchManifest("1345-234r53",
            new CarrierDto("2345te-234r", "Textile Road", "president@textileroad.com"),
            LocalDate.of(2026, 11, 20), "Big Warehouse",
            List.of(new ParcelDto("TRF-1", new BigDecimal("2.5")),
                    new ParcelDto("TRF-7", new BigDecimal("0.8"))));

    private final DispatchManifest manifest1 = new DispatchManifest("1345-234r53",
            new CarrierDto("2345te-234r", "Textile Road", "president@textileroad.com"),
            LocalDate.of(2026, 11, 20), "Big Warehouse",
            List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));

    @BeforeEach
    void setUp() {
        shipments = new InMemoryShipmentRepository();
        archive = new InMemoryManifestArchive();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://tariffs.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ShipmentRegistrationService(shipments, archive,
                new RestTariffClient(builder.build(), new ManifestMapper()), new ChargeCalculator(),
                new ManifestMapper(), Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC));
    }

    @Test
    void foundTariffRatesTheParcel() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withSuccess("""
                        {"tariffCode": "TRF-1", "serviceName": "Standard", "baseFee": 4.50, "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                        """, MediaType.APPLICATION_JSON));

        ShipmentRecord record = service.register(manifest1, raw);

        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.RATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("8.25");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void notFoundLeavesTheParcelUnrated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ShipmentRecord record = service.register(manifest1, raw);

        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("0");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void serverErrorLeavesTheParcelUnrated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ShipmentRecord record = service.register(manifest1, raw);

        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("0");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void timeoutLeavesTheParcelUnrated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        ShipmentRecord record = service.register(manifest1, raw);

        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("0");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void malformedJsonLeavesTheParcelUnrated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));


        ShipmentRecord record = service.register(manifest1, raw);

        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("0");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }


    @Test
    void timeoutOnTheSecondParcelKeepsTheFirstRated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withSuccess("""
                        {"tariffCode": "TRF-1", "serviceName": "Standard", "baseFee": 4.50, "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));


        ShipmentRecord record = service.register(manifest, raw);

        assertThat(record.parcels().get(0).rating()).isEqualTo(Rating.RATED);
        assertThat(record.parcels().get(1).rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("8.25");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void notFoundOnTheFirstParcelKeepsTheSecondRated() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-7"))
                .andRespond(withSuccess("""
                        {"tariffCode": "TRF-7", "serviceName": "Standard", "baseFee": 4.50, "pricePerKg": 1.20, "fuelSurchargeRate": 0.10, "transitDays": 3}
                        """, MediaType.APPLICATION_JSON));

        DispatchManifest manifest3 = new DispatchManifest("1345-234r53",
                new CarrierDto("2345te-234r", "Textile Road", "president@textileroad.com"),
                LocalDate.of(2026, 11, 20), "Big Warehouse",
                List.of(new ParcelDto("TRF-1", new BigDecimal("2.5")),
                        new ParcelDto("TRF-7", new BigDecimal("2.5"))));

        ShipmentRecord record = service.register(manifest3, raw);

        assertThat(record.parcels().get(0).rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.parcels().get(1).rating()).isEqualTo(Rating.RATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("8.25");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }

    @Test
    void twoFailuresStillRegisterTheShipment() {
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://tariffs.test/tariffs/TRF-7"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        ShipmentRecord record = service.register(manifest, raw);

        assertThat(record.parcels().get(0).rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.parcels().get(1).rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualByComparingTo("0");
        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
        server.verify();
    }
}
