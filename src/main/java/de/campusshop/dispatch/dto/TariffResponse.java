package de.campusshop.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON returned by {@code GET /tariffs/{tariffCode}} on the external tariff service. Mapped to the
 * internal {@link de.campusshop.dispatch.model.Tariff} straight away and not used elsewhere.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TariffResponse(
        String tariffCode,
        String serviceName,
        BigDecimal baseFee,
        BigDecimal pricePerKg,
        BigDecimal fuelSurchargeRate,
        int transitDays) {
}
