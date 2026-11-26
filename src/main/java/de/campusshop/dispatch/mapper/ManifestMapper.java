package de.campusshop.dispatch.mapper;

import de.campusshop.dispatch.dto.ParcelCharge;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.dto.TariffResponse;
import de.campusshop.dispatch.model.RatedParcel;
import de.campusshop.dispatch.model.Rating;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.Tariff;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Converts between the external tariff JSON, the internal model and the response DTOs.
 *
 * <p>
 */
@Component
public class ManifestMapper {

    /** External tariff JSON to the internal {@link Tariff}. */
    public Tariff toTariff(TariffResponse response) {

        return new Tariff(response.tariffCode(), response.serviceName(), response.baseFee(),
                response.pricePerKg(), response.fuelSurchargeRate());
    }

    /** One priced parcel. For an UNRATED parcel all tariff fields and the charge are null. */
    public ParcelCharge toParcelCharge(RatedParcel parcel) {
        if (parcel.rating() == Rating.UNRATED){
            return new ParcelCharge(parcel.parcelNo(), parcel.tariffCode(), null,
                    parcel.weightKg(), null, null, null, null,
                    parcel.rating());
        }
        return new ParcelCharge(parcel.parcelNo(), parcel.tariffCode(), parcel.tariff().serviceName(),
                parcel.weightKg(), parcel.tariff().baseFee(), parcel.tariff().pricePerKg(),
                parcel.tariff().fuelSurchargeRate(), parcel.charge(), parcel.rating());
    }

    /** The full registration response: the stored item plus every priced parcel. */
    public ShipmentRecord toRecord(ShipmentItem item, List<RatedParcel> parcels) {
        List<ParcelCharge> changedParcels = parcels.stream().map(this::toParcelCharge).toList();
        return new ShipmentRecord(item.shipmentRef(), item.manifestNo(), item.carrierId(),
                item.dispatchDate(), item.registeredAt(), item.state(), item.parcelCount(),
                item.totalCharge(), item.manifestKey(), changedParcels);
    }

    /** The stored item as returned by the read endpoints (no parcels). */
    public ShipmentSummary toSummary(ShipmentItem item) {
        return new ShipmentSummary(item.shipmentRef(), item.manifestNo(), item.carrierId(),
                item.dispatchDate(), item.registeredAt(), item.state(), item.parcelCount(),
                item.totalCharge(), item.manifestKey());
    }
}
