package de.campusshop.dispatch.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestKeysTest {

    @Test
    void keyContainsTheCarrierIdAndShipmentRef() {
        assertThat(ManifestKeys.keyFor("wyu-7248", "tfg-234")).isEqualTo("manifests/wyu-7248/tfg-234.json");
    }
}
