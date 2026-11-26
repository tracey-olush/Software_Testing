package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.TariffClient;
import de.campusshop.dispatch.dto.CarrierDto;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.Rating;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.Tariff;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ShipmentRegistrationService} with <b>mocked</b> repository, archive and
 * tariff client. Cover the success path, the UNRATED path, the duplicate path and the clean-up when
 * the DynamoDB write fails. The mocks are wired for you below.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentRegistrationServiceTest {

    @Mock
    private ShipmentRepository shipments;
    @Mock
    private ManifestArchive archive;
    @Mock
    private TariffClient tariffClient;

    private ShipmentRegistrationService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC);

    private DispatchManifest manifest = new DispatchManifest("1345-234r53",
            new CarrierDto("2345te-234r", "Textile Road", "president@textileroad.com"),
            LocalDate.of(2026, 11, 20), "Big Warehouse",
            List.of(new ParcelDto("TRF-1", new BigDecimal("2.5")),
                    new ParcelDto("TRF-2", new BigDecimal("0.8"))));

    private byte[] raw = "{}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        service = new ShipmentRegistrationService(shipments, archive, tariffClient, new ChargeCalculator(),
                new ManifestMapper(), clock);
    }

    @Test
    void registersArchivesAndIndexesAShipment() {
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.of(new Tariff("TRF-1", "Textile Road",
                new BigDecimal("4.50"), new BigDecimal("1.20"), new BigDecimal("0.10"))));
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.of(new Tariff("TRF-2", "Textile Road",
                new BigDecimal("6.0"), new BigDecimal("2.00"), new BigDecimal("0.15"))));

        ShipmentRecord response = service.register(manifest, raw);
        assertThat(response.parcels().get(0).charge()).isEqualByComparingTo("8.25");
        assertThat(response.parcels().get(1).charge()).isEqualByComparingTo("8.74");
        assertThat(response.totalCharge()).isEqualByComparingTo("16.99");

        ArgumentCaptor<String> archieveCaptorKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> archiveCaptorByte = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<ShipmentItem> shipmentCaptor = ArgumentCaptor.forClass(ShipmentItem.class);
        verify(archive).archive(archieveCaptorKey.capture(), archiveCaptorByte.capture());
        verify(shipments).put(shipmentCaptor.capture());
    }

    @Test
    void unknownTariffLeavesTheParcelUnrated() {
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.empty());
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.of(new Tariff("TRF-2", "Textile Road",
                new BigDecimal("6.0"), new BigDecimal("2.00"), new BigDecimal("0.15"))));

        ShipmentRecord response = service.register(manifest, raw);

        assertThat(response.parcels().get(0).rating()).isEqualTo(Rating.UNRATED);
        assertThat(response.parcelCount()).isEqualTo(2);
        assertThat(response.totalCharge()).isEqualByComparingTo("8.74");
        assertThat(response.parcels().get(0).charge()).isNull();
    }

    @Test
    void duplicateManifestIsRejectedBeforeAnythingIsWritten() {
        when(shipments.manifestExists("1345-234r53")).thenReturn(true);

        assertThatThrownBy(() -> service.register(manifest, raw)).isInstanceOf(DuplicateManifestException.class);

        verify(shipments, never()).put(any());
        verify(archive, never()).archive(any(), any());
        verifyNoInteractions(tariffClient);

    }

    @Test
    void failedIndexWriteRemovesTheArchivedManifest() {
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.of(new Tariff("TRF-1", "Textile Road",
                new BigDecimal("4.50"), new BigDecimal("1.20"), new BigDecimal("0.10"))));
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.of(new Tariff("TRF-2", "Textile Road",
                new BigDecimal("6.0"), new BigDecimal("2.00"), new BigDecimal("0.15"))));

        doThrow(new RuntimeException("Failed")).when(shipments).put(any(ShipmentItem.class));

        assertThatThrownBy(() -> service.register(manifest, raw)).isInstanceOf(RuntimeException.class);
        ArgumentCaptor<String> archieveCaptorKey = ArgumentCaptor.forClass(String.class);

        verify(archive).archive(archieveCaptorKey.capture(), eq(raw));
        verify(archive).remove(archieveCaptorKey.getValue());
    }
}
