package de.campusshop.dispatch.service;


import de.campusshop.dispatch.dto.CarrierDto;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.InMemoryManifestArchive;
import de.campusshop.dispatch.repository.InMemoryShipmentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class LifecyleTest {
    //random register and delete are tested

        @Test
        void randomRegisterAndDeleteSequencesMatchTheModel() {
            for (long seed : new long[]{1, 2, 3, 42, 99, 123, 2026, 31337}) {
                InMemoryShipmentRepository shipments = new InMemoryShipmentRepository();
                InMemoryManifestArchive archive = new InMemoryManifestArchive();
                ShipmentRegistrationService registration = new ShipmentRegistrationService(shipments, archive,
                        code -> Optional.empty(), new ChargeCalculator(), new ManifestMapper(), Clock.systemUTC());
                ShipmentQueryService queries = new ShipmentQueryService(shipments, archive, new ManifestMapper());
                Map<String, String> model = new HashMap<>(); // manifestNo -> shipmentRef
                Random random = new Random(seed);

                for (int step = 0; step < 300; step++) {
                    String manifestNo = "jowr-" + random.nextInt(8);
                    String ref = model.get(manifestNo);
                    if (random.nextBoolean()) {
                        DispatchManifest manifest = new DispatchManifest(manifestNo,
                                new CarrierDto("sd-qjew9", "Textile Road", "president@textileroad.com"),
                                LocalDate.of(2026, 11, 20), "Big Warehouse",
                                List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));
                        if (ref == null) {
                            model.put(manifestNo, registration
                                    .register(manifest, "{}".getBytes(StandardCharsets.UTF_8)).shipmentRef());
                        } else {
                            assertThatThrownBy(() -> registration
                                    .register(manifest, "{}".getBytes(StandardCharsets.UTF_8)))
                                    .isInstanceOf(DuplicateManifestException.class);
                        }
                    } else if (ref != null) {
                        queries.delete(ref);
                        model.remove(manifestNo);
                        assertThatThrownBy(() -> queries.find(ref)).isInstanceOf(ShipmentNotFoundException.class);
                    }

                    assertThat(queries.listByCarrier("sd-qjew9"))
                            .as("seed %d, step %d", seed, step)
                            .extracting(ShipmentSummary::shipmentRef)
                            .containsExactlyInAnyOrderElementsOf(model.values());
                    for (String liveRef : model.values()) {
                        assertThat(queries.manifest(liveRef)).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
}

