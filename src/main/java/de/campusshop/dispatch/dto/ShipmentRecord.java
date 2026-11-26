package de.campusshop.dispatch.dto;

import de.campusshop.dispatch.model.ShipmentState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response of a successful registration ({@code 201}): the stored summary plus every priced parcel.
 */
public record ShipmentRecord(
        String shipmentRef,
        String manifestNo,
        String carrierId,
        LocalDate dispatchDate,
        Instant registeredAt,
        ShipmentState state,
        int parcelCount,
        BigDecimal totalCharge,
        String manifestKey,
        List<ParcelCharge> parcels) {
}
