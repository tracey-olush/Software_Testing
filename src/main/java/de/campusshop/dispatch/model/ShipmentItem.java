package de.campusshop.dispatch.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The shipment as stored in the DynamoDB table. {@code manifestKey} points at the archived raw
 * manifest in S3.
 */
public record ShipmentItem(
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
