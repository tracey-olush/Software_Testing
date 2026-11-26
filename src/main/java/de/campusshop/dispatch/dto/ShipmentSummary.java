package de.campusshop.dispatch.dto;

import de.campusshop.dispatch.model.ShipmentState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A shipment as stored in DynamoDB, returned by the read endpoints. Parcel detail is not indexed;
 * the archived manifest holds the original parcels.
 */
public record ShipmentSummary(
        String shipmentRef,
        String manifestNo,
        String carrierId,
        LocalDate dispatchDate,
        Instant registeredAt,
        ShipmentState state,
        int parcelCount,
        BigDecimal totalCharge,
        String manifestKey) {
}
