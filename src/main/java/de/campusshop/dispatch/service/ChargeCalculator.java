package de.campusshop.dispatch.service;

import de.campusshop.dispatch.model.Tariff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prices one parcel: {@code (baseFee + pricePerKg × weightKg) × (1 + fuelSurchargeRate)}, rounded
 * half-up to two decimals.
 *
 * <p>
 */
@Component
public class ChargeCalculator {

    public BigDecimal charge(Tariff tariff, BigDecimal weightKg) {
        return tariff.baseFee()
                .add(tariff.pricePerKg().multiply(weightKg))
                .multiply(BigDecimal.ONE.add(tariff.fuelSurchargeRate()))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
