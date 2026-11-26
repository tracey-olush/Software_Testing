package de.campusshop.dispatch.model;

import java.math.BigDecimal;

/**
 * Internal tariff model, independent of the tariff service's JSON.
 */
public record Tariff(
        String code,
        String serviceName,
        BigDecimal baseFee,
        BigDecimal pricePerKg,
        BigDecimal fuelSurchargeRate) {
}
