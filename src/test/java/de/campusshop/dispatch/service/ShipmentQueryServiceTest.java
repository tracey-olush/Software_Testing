package de.campusshop.dispatch.service;

import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import de.campusshop.dispatch.model.ShipmentState;
import java.math.BigDecimal;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)

public class ShipmentQueryServiceTest {
    @Mock
    private ShipmentRepository shipments;
    @Mock
    private ManifestArchive archive;

    private ShipmentQueryService service;

    private final ShipmentItem item = new ShipmentItem("rghj-45tg", "1345-234r53", "2345te-234r", LocalDate.of(2026, 11, 20), Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1, new BigDecimal("8.25"), "manifests/" + "2345te-234r" + "/" + "rghj-45tg" + ".json");

    @BeforeEach
    void setUp() {
        service = new ShipmentQueryService(shipments, archive, new ManifestMapper());
    }

    @Test
    void findReturnsTheSummary() {
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));

        assertThat(service.find("rghj-45tg")).isEqualTo(new ManifestMapper().toSummary(item));
    }

    @Test
    void findOfUnknownRefThrowsNotFound() {
        when(shipments.get("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find("nope"))
                .isInstanceOf(ShipmentNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void listByCarrierSortsOldestDispatchDateFirst() {
        when(shipments.listByCarrier("2345te-234r")).thenReturn(List.of(
                new ShipmentItem("new", "5467-473I98", "2345te-234r", LocalDate.of(2027, 1, 1), Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1, new BigDecimal("8.25"), "manifests/" + "2345te-234r" + "/" + "new" + ".json"),
                new ShipmentItem("old", "1345-234r53", "2345te-234r", LocalDate.of(2025, 12, 31), Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1, new BigDecimal("8.25"), "manifests/" + "2345te-234r" + "/" + "old" + ".json"),
                new ShipmentItem("mid", "4569-679G75", "2345te-234r", LocalDate.of(2026, 6, 15), Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1, new BigDecimal("8.25"), "manifests/" + "2345te-234r" + "/" + "mid" + ".json")));

        assertThat(service.listByCarrier("2345te-234r"))
                .extracting(ShipmentSummary::shipmentRef)
                .containsExactly("old", "mid", "new");
    }

    @Test
    void listByCarrierWithoutShipmentsIsEmpty() {
        when(shipments.listByCarrier("C-9")).thenReturn(List.of());

        assertThat(service.listByCarrier("C-9")).isEmpty();
    }

    @Test
    void manifestReturnsTheArchivedBytes() {
        byte[] bytes = {1, 2, 3};
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));
        when(archive.fetch(item.manifestKey())).thenReturn(Optional.of(bytes));

        assertThat(service.manifest("rghj-45tg")).containsExactly(1, 2, 3);
    }

    @Test
    void manifestOfUnknownRefDoesNotTouchTheArchive() {
        when(shipments.get("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.manifest("nope")).isInstanceOf(ShipmentNotFoundException.class);

        verifyNoInteractions(archive);
    }

    @Test
    void manifestThrowsWhenTheArchiveObjectIsMissing() {
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));
        when(archive.fetch(item.manifestKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.manifest("rghj-45tg")).isInstanceOf(ShipmentNotFoundException.class);
    }

    @Test
    void deleteRemovesFromBothStores() {
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));

        service.delete("rghj-45tg");

        verify(shipments).remove("rghj-45tg");
        verify(archive).remove(item.manifestKey());
    }

    @Test
    void deleteOfUnknownRefRemovesNothing() {
        when(shipments.get("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("nope")).isInstanceOf(ShipmentNotFoundException.class);

        verify(shipments, never()).remove(any());
        verifyNoInteractions(archive);
    }

    @Test
    void deleteDoesNotTouchTheArchiveWhenTheIndexDeleteFails() {
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));
        doThrow(new IllegalStateException("db down")).when(shipments).remove("rghj-45tg");

        assertThatThrownBy(() -> service.delete("rghj-45tg")).hasMessage("db down");

        verifyNoInteractions(archive);
    }

    @Test
    void deletePropagatesAnArchiveFailure() {
        when(shipments.get("rghj-45tg")).thenReturn(Optional.of(item));
        doThrow(new IllegalStateException("s3 down")).when(archive).remove(item.manifestKey());

        assertThatThrownBy(() -> service.delete("rghj-45tg")).hasMessage("s3 down");
    }
}
