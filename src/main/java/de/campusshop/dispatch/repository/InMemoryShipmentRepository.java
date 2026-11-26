package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.model.ShipmentItem;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ShipmentRepository} for tests and IDE runs ({@code inmemory} profile). Returns
 * carrier listings in the same order as the DynamoDB index.
 */
@Repository
@Profile("inmemory")
public class InMemoryShipmentRepository implements ShipmentRepository {

    private final Map<String, ShipmentItem> items = new ConcurrentHashMap<>();

    @Override
    public void put(ShipmentItem item) {
        items.put(item.shipmentRef(), item);
    }

    @Override
    public Optional<ShipmentItem> get(String shipmentRef) {
        return Optional.ofNullable(items.get(shipmentRef));
    }

    @Override
    public boolean manifestExists(String manifestNo) {
        return items.values().stream().anyMatch(i -> i.manifestNo().equals(manifestNo));
    }

    @Override
    public List<ShipmentItem> listByCarrier(String carrierId) {
        return items.values().stream()
                .filter(i -> i.carrierId().equals(carrierId))
                .sorted(Comparator.comparing(ShipmentItem::dispatchDate))
                .toList();
    }

    @Override
    public boolean remove(String shipmentRef) {
        items.remove(shipmentRef);
        return false;
    }
}
