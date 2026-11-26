package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.TariffClient;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelCharge;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.RatedParcel;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import de.campusshop.dispatch.model.Tariff;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/**
 * Registers a validated manifest. Dependencies are wired for you through constructor injection.
 *
 * <p>implement {@link #register} in the order of CW2 spec §4.0 — duplicate check,
 * archive in S3, price every parcel, index in DynamoDB, and remove the archived object again if the
 * DynamoDB write fails.
 */
@Service
public class ShipmentRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentRegistrationService.class);

    private final ShipmentRepository shipments;
    private final ManifestArchive archive;
    private final TariffClient tariffClient;
    private final ChargeCalculator calculator;
    private final ManifestMapper mapper;
    private final Clock clock;

    public ShipmentRegistrationService(ShipmentRepository shipments, ManifestArchive archive, TariffClient tariffClient,
                                       ChargeCalculator calculator, ManifestMapper mapper, Clock clock) {
        this.shipments = shipments;
        this.archive = archive;
        this.tariffClient = tariffClient;
        this.calculator = calculator;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param manifest    the parsed and validated manifest
     * @param rawManifest the request body exactly as received; this is what goes to S3
     */
    public ShipmentRecord register(DispatchManifest manifest, byte[] rawManifest) {
        if (shipments.manifestExists(manifest.manifestNo())) {
            throw new DuplicateManifestException(manifest.manifestNo());
        }

        String shipmentRef = UUID.randomUUID() .toString();
        String manifestKey = "manifests/" + manifest.carrier().carrierId() + "/" + shipmentRef + ".json";

        archive.archive(manifestKey, rawManifest);

        List<RatedParcel> parcels = new ArrayList<>();

        int noOfParcel = 1;

        for (ParcelDto parcel : manifest.parcels()) {
            Optional<Tariff> tariff = tariffClient.findTariff(parcel.tariffCode());

            if (tariff.isPresent()) {
                BigDecimal charge = calculator.charge(tariff.get(), parcel.weightKg());
                parcels.add(new RatedParcel(noOfParcel, parcel.tariffCode(), parcel.weightKg(), tariff.get(), charge));
            }

            else {
                log.warn("No price for parcel for shipment={}, code ={}", shipmentRef, parcel.tariffCode());
                parcels.add(new RatedParcel(noOfParcel, parcel.tariffCode(), parcel.weightKg(), null, null));
            }

            noOfParcel++;
        }

        ShipmentItem item = new ShipmentItem(shipmentRef, manifest.manifestNo(), manifest.carrier().carrierId(),
                manifest.dispatchDate(), clock.instant(), ShipmentState.REGISTERED, parcels.size(),
                parcels.stream().map(RatedParcel::charge).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                manifestKey);

        try {
            shipments.put(item);
        }
        catch (RuntimeException e) {
            log.error("Cannot put shipment={} in DynamoDB, removed", item.shipmentRef(), e);
            archive.remove(manifestKey);
            throw e;
        }

        log.info("Registered shipment={} for manifest={}", shipmentRef, manifest.manifestNo());
        return mapper.toRecord(item, parcels);
    }
}
