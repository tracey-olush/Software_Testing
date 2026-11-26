package de.campusshop.dispatch.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationTariffClientTest {

    private final SimulationTariffClient client = new SimulationTariffClient();

    @Test
    void matchesTheReferenceCatalog() {
        var standard = client.findTariff("TRF-1").orElseThrow();
        assertThat(standard.serviceName()).isEqualTo("Standard");
        assertThat(standard.baseFee()).isEqualByComparingTo("4.50");
        assertThat(standard.pricePerKg()).isEqualByComparingTo("1.20");

        var economy = client.findTariff("TRF-4").orElseThrow();
        assertThat(economy.serviceName()).isEqualTo("Economy");
        assertThat(economy.baseFee()).isEqualByComparingTo("3.10");
        assertThat(economy.pricePerKg()).isEqualByComparingTo("0.85");
    }

    @Test
    void codesOutsideTheCatalogAreUnknown() {
        assertThat(client.findTariff("TRF-0")).isEmpty();
        assertThat(client.findTariff("TRF-501")).isEmpty();
        assertThat(client.findTariff("PKG-1")).isEmpty();
    }
}
