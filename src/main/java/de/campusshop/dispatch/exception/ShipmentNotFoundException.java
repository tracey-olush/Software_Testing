package de.campusshop.dispatch.exception;

/** No shipment exists for the reference. Mapped to {@code 404 SHIPMENT_NOT_FOUND}. */
public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(String shipmentRef) {
        super("No shipment exists for shipmentRef " + shipmentRef);
    }
}
