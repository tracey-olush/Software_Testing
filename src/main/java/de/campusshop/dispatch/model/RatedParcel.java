package de.campusshop.dispatch.model;

import java.math.BigDecimal;

/**
 * A parcel after pricing.
 *
 * @param parcelNo 1-based position in the manifest
 * @param tariff   the resolved tariff, or {@code null} when the parcel is {@link Rating#UNRATED}
 * @param charge   the computed charge, or {@code null} when unrated
 */
public record RatedParcel(int parcelNo, String tariffCode, BigDecimal weightKg, Tariff tariff, BigDecimal charge) {

    public Rating rating() {
        return tariff == null ? Rating.UNRATED : Rating.RATED;
    }
}
