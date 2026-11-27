package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;


public class InMemoryShipmentRepositoryTest {
    private final InMemoryShipmentRepository repo = new InMemoryShipmentRepository();

    @Test
    void putThenGetReturnsTheItem() {
        ShipmentItem item = new ShipmentItem("tfg-234", "2yu4-34", "wyu-7248", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/wyu-7248/tfg-234.json");

        repo.put(item);

        assertThat(repo.get("tfg-234")).contains(item);
    }

    @Test
    void unknownRefIsEmpty() {
        assertThat(repo.get("missing")).isEmpty();
    }

    @Test
    void manifestExistsOnlyAfterPut() {
        assertThat(repo.manifestExists("9vs4-12")).isFalse();

        repo.put(new ShipmentItem("sjar-56", "9vs4-12", "wyu-7248", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/wyu-7248/sjar-56.json"));

        assertThat(repo.manifestExists("9vs4-12")).isTrue();
    }

    @Test
    void listsOnlyThatCarrierOldestFirst() {
        repo.put(new ShipmentItem("new", "2yu4-24", "tays-354", LocalDate.of(2027, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/tays-354/new.json"));
        repo.put(new ShipmentItem("other", "2yu4-12", "tays-235", LocalDate.of(2026, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/tays-235/other.json"));
        repo.put(new ShipmentItem("old", "2fu4-12", "tays-354", LocalDate.of(2025, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/tays-354/old.json"));

        assertThat(repo.listByCarrier("tays-354")).extracting(ShipmentItem::shipmentRef)
                .containsExactly("old", "new");
    }

    @Test
    void removeDeletesTheItem() {
        repo.put(new ShipmentItem("ahjsr-345", "9fu4-12", "wyu-7248", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/wyu-7248/ahjsr-345.json"));

        repo.remove("ahjsr-345");

        assertThat(repo.get("ahjsr-345")).isEmpty();
        assertThat(repo.manifestExists("9fu4-12")).isFalse();
    }
}
