package de.campusshop.dispatch.service;

import de.campusshop.dispatch.model.Tariff;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ChargeCalculatorTest {

    private final ChargeCalculator calculator = new ChargeCalculator();

    private static Tariff tariff(String code, String serviceName, String baseFee,
                                 String pricePerKg, String fuelSurchargeRate) {

        return new Tariff(code, "", new BigDecimal(baseFee),
                new BigDecimal(pricePerKg), new BigDecimal(fuelSurchargeRate));
    }

    @Test
    void correctCalculation() {
        Tariff tariff1 = tariff("TRF-1", "", "4.50", "1.20", "0.10");
        assertThat(calculator.charge(tariff1, new BigDecimal("2.5"))).isEqualByComparingTo("8.25");
    }

    @Test
    void round() {
        Tariff tariff2 = tariff("TRF-4", "", "1.00", "0.01", "0.00");

        assertThat(calculator.charge(tariff2, new BigDecimal("1.5"))).isEqualByComparingTo("1.02");
        assertThat(calculator.charge(tariff2, new BigDecimal("1.4"))).isEqualByComparingTo("1.01");
        assertThat(calculator.charge(tariff2, new BigDecimal("0.001"))).isEqualByComparingTo("1.00");
    }

    @Test
    void surchargeAppliesToBaseFeeAndWeightPrice() {
        Tariff tariff = new Tariff("TRF-7", "Textile Road", new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("0.50"));

        assertThat(calculator.charge(tariff, new BigDecimal("2"))).isEqualByComparingTo("30.00");
    }

    @Test
    void hasTwoDecimalPlaces() {
        Tariff tariff5 = tariff("TRF-1", "", "4.50", "1.20", "0.10");

        assertThat(calculator.charge(tariff5, new BigDecimal("2.5")).scale() == 2).isTrue();
    }

}
