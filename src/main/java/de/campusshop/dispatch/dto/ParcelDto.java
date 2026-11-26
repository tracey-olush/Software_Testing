package de.campusshop.dispatch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One inbound parcel. The partner sends only the tariff code and the weight; the fees come from
 * the tariff service.
 */
public record ParcelDto(
        @NotBlank String tariffCode,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal weightKg) {
}
