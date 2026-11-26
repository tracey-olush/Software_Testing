package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.model.ShipmentItem;

import java.util.List;
import java.util.Optional;

/**
 * The shipment index. Implemented with DynamoDB at runtime and in memory for tests.
 */
public interface ShipmentRepository {

    /** PutItem. */
    void put(ShipmentItem item);

    /** GetItem by partition key {@code shipmentRef}. */
    Optional<ShipmentItem> get(String shipmentRef);

    /** Query on {@code manifest-index}: has this manifest number been registered? */
    boolean manifestExists(String manifestNo);

    /** Query on {@code carrier-dispatch-index}, oldest {@code dispatchDate} first. */
    List<ShipmentItem> listByCarrier(String carrierId);

    /**
     * DeleteItem by partition key.
     *
     * @return
     */
    boolean remove(String shipmentRef);
}
