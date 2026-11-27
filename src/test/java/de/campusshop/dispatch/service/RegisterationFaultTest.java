package de.campusshop.dispatch.service;


import de.campusshop.dispatch.client.TariffClient;
import de.campusshop.dispatch.dto.CarrierDto;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.InMemoryManifestArchive;
import de.campusshop.dispatch.repository.InMemoryShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegisterationFaultTest {
    private InMemoryShipmentRepository shipments;
    private InMemoryManifestArchive archive;
    private TariffClient tariffClient;
    private ShipmentRegistrationService service;

    @BeforeEach
    void setUp() {
        shipments = spy(new InMemoryShipmentRepository());
        archive = spy(new InMemoryManifestArchive());
        tariffClient = mock(TariffClient.class);
        service = new ShipmentRegistrationService(shipments, archive, tariffClient, new ChargeCalculator(),
                new ManifestMapper(), Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC));
    }

    @Test
    void duplicateCheckFailureLeavesNothingBehind() {
        DispatchManifest manifest = new DispatchManifest("REGVW-Q23",
                new CarrierDto("se3-fds", "Textile Road", "president@textileroad.com"),
                LocalDate.of(2026, 11, 20), "Big Warehouse",
                List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));
        doThrow(new IllegalStateException("check failed")).when(shipments).manifestExists("REGVW-Q23");

        assertThatThrownBy(() -> service.register(manifest, "{}".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("check failed");

        verify(archive, never()).archive(anyString(), any());
        assertThat(shipments.listByCarrier("se3-fds")).isEmpty();
    }

    @Test
    void uploadFailureLeavesNothingInTheIndex() {
        DispatchManifest manifest = new DispatchManifest("RVW-Q23",
                new CarrierDto("se3-fds", "Textile Road", "president@textileroad.com"),
                LocalDate.of(2026, 11, 20), "Big Warehouse",
                List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));
        doThrow(new IllegalStateException("upload failed")).when(archive).archive(anyString(), any());

        assertThatThrownBy(() -> service.register(manifest, "{}".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("upload failed");

        assertThat(shipments.listByCarrier("se3-fds")).isEmpty();
    }


    @Test
    void failedIndexWriteLeavesNothingAndCanBeRetried() {
        DispatchManifest manifest = new DispatchManifest("F-6",
                new CarrierDto("se3-fds", "Textile Road", "president@textileroad.com"),
                LocalDate.of(2026, 11, 20), "Big Warehouse",
                List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("write failed")).doCallRealMethod().when(shipments).put(any());

        assertThatThrownBy(() -> service.register(manifest, "{}".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("write failed");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(archive).archive(key.capture(), any());
        assertThat(archive.fetch(key.getValue())).isEmpty();
        assertThat(shipments.listByCarrier("se3-fds")).isEmpty();

        var record = service.register(manifest, "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(shipments.get(record.shipmentRef())).isPresent();
        assertThat(archive.fetch(record.manifestKey())).isPresent();
    }
}
