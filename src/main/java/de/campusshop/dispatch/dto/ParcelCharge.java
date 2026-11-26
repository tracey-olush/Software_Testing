package de.campusshop.dispatch.dto;

import de.campusshop.dispatch.model.Rating;

import java.math.BigDecimal;

/**
 * One priced parcel in a {@link ShipmentRecord}. For an {@code UNRATED} parcel every tariff field
 * and {@code charge} are {@code null}.
 */
public record ParcelCharge(
        int parcelNo,
        String tariffCode,
        String serviceName,
        BigDecimal weightKg,
        BigDecimal baseFee,
        BigDecimal pricePerKg,
        BigDecimal fuelSurchargeRate,
        BigDecimal charge,
        Rating rating) {
}
