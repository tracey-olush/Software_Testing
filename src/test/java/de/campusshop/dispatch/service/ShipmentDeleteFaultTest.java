package de.campusshop.dispatch.service;

import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import de.campusshop.dispatch.repository.InMemoryManifestArchive;
import de.campusshop.dispatch.repository.InMemoryShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

public class ShipmentDeleteFaultTest {
    //if one delete fails

    private InMemoryShipmentRepository shipments;
    private InMemoryManifestArchive archive;
    private ShipmentQueryService service;

    @BeforeEach
    void setUp() {
        shipments = spy(new InMemoryShipmentRepository());
        archive = spy(new InMemoryManifestArchive());
        service = new ShipmentQueryService(shipments, archive, new ManifestMapper());
    }

    @Test
    void failedIndexDeleteKeepsBothAndCanBeRetried() {
        shipments.put(new ShipmentItem("JFS-23", "fei0-eq", "se3-fds", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/se3-fds/JFS-23.json"));
        archive.archive("manifests/se3-fds/JFS-23.json", new byte[]{1});
        doThrow(new IllegalStateException("index delete failed")).doCallRealMethod()
                .when(shipments).remove("JFS-23");

        assertThatThrownBy(() -> service.delete("JFS-23")).hasMessage("index delete failed");

        assertThat(shipments.get("JFS-23")).isPresent();
        assertThat(archive.fetch("manifests/se3-fds/JFS-23.json")).isPresent();

        service.delete("JFS-23");

        assertThat(shipments.get("JFS-23")).isEmpty();
        assertThat(archive.fetch("manifests/se3-fds/JFS-23.json")).isEmpty();
    }
}
