package de.campusshop.dispatch.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestKeysTest {

    @Test
    void keyContainsTheCarrierIdAndShipmentRef() {
        assertThat(ManifestKeys.keyFor("C-1", "S-1")).isEqualTo("manifests/C-1/S-1.json");
    }
}
