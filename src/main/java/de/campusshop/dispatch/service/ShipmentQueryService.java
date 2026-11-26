package de.campusshop.dispatch.service;

import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read and delete operations on registered shipments. Unknown shipments end in
 * {@code ShipmentNotFoundException} (404).
 * <p>Deletion policy: deletion removes from both DynamoDB and S3. it returns 204 </p>
 * <p>implement the four methods and document your deletion policy here — see
 * CW2 spec §2.1.
 */
@Service
public class ShipmentQueryService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentQueryService.class);

    private final ShipmentRepository shipments;
    private final ManifestArchive archive;
    private final ManifestMapper mapper;

    public ShipmentQueryService(ShipmentRepository shipments, ManifestArchive archive, ManifestMapper mapper) {
        this.shipments = shipments;
        this.archive = archive;
        this.mapper = mapper;
    }

    public ShipmentSummary find(String shipmentRef) {
        return shipments.get(shipmentRef).map(mapper::toSummary).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
    }

    /** Oldest dispatchDate first. */
    public List<ShipmentSummary> listByCarrier(String carrierId) {
        return shipments.listByCarrier(carrierId).stream().sorted(Comparator.comparing(ShipmentItem::dispatchDate))
                .map(mapper::toSummary).toList();
    }

    /** The archived manifest bytes. */
    public byte[] manifest(String shipmentRef) {
        ShipmentItem shipmentItem = shipments.get(shipmentRef).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
        return archive.fetch(shipmentItem.manifestKey()).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
    }

    public void delete(String shipmentRef) {
        ShipmentItem shipmentItem = shipments.get(shipmentRef).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
        shipments.remove(shipmentRef);
        archive.remove(shipmentItem.manifestKey());
        log.info("Deleted shipment={}, its archived manifest={} (manifest deleted with shipment)", shipmentRef, shipmentItem.manifestKey());
    }
}
