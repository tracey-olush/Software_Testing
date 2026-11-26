package de.campusshop.dispatch.mapper;

import de.campusshop.dispatch.dto.ParcelCharge;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.dto.TariffResponse;
import de.campusshop.dispatch.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MapperTest {
    private final ManifestMapper mapper = new ManifestMapper();

    @Test
    void tariffTest() {
        TariffResponse response = new TariffResponse("TRF-1", "Standard", new BigDecimal("4.50"),
        new BigDecimal("1.20"), new BigDecimal("0.10"), 8);

        Tariff tariff = mapper.toTariff(response);

        assertThat(tariff.code()).isEqualTo("TRF-1");
        assertThat(tariff.serviceName()).isEqualTo("Standard");
        assertThat(tariff.baseFee()).isEqualByComparingTo("4.50");
        assertThat(tariff.pricePerKg()).isEqualByComparingTo("1.20");
        assertThat(tariff.fuelSurchargeRate()).isEqualByComparingTo("0.10");
    }

    @Test
    void ratedParcelTest() {
        Tariff tariff = new Tariff("TRF-1", "Standard", new BigDecimal("4.50"),
                new BigDecimal("1.20"), new BigDecimal("0.10"));
        RatedParcel parcel = new RatedParcel(12324, "TRF-1", new BigDecimal("2.5"), tariff,
                new BigDecimal("8.25"));

        ParcelCharge parcelCharge = mapper.toParcelCharge(parcel);

        assertThat(parcelCharge.parcelNo()).isEqualTo(12324);
        assertThat(parcelCharge.tariffCode()).isEqualTo("TRF-1");
        assertThat(parcelCharge.serviceName()).isEqualTo("Standard");
        assertThat(parcelCharge.weightKg()).isEqualByComparingTo("2.5");
        assertThat(parcelCharge.baseFee()).isEqualByComparingTo("4.50");
        assertThat(parcelCharge.pricePerKg()).isEqualByComparingTo("1.20");
        assertThat(parcelCharge.fuelSurchargeRate()).isEqualByComparingTo("0.10");
        assertThat(parcelCharge.charge()).isEqualByComparingTo("8.25");
        assertThat(parcelCharge.rating()).isEqualTo(Rating.RATED);
    }

    @Test
    void unratedParcelTest() {
        RatedParcel parcel = new RatedParcel(12, "TRF-5", new BigDecimal("0.8"), null, null);

        ParcelCharge parcelCharge = mapper.toParcelCharge(parcel);

        assertThat(parcelCharge.baseFee()).isNull();
        assertThat(parcelCharge.pricePerKg()).isNull();
        assertThat(parcelCharge.fuelSurchargeRate()).isNull();
        assertThat(parcelCharge.rating()).isEqualTo(Rating.UNRATED);
        assertThat(parcelCharge.serviceName()).isNull();
        assertThat(parcelCharge.charge()).isNull();

    }
    @Test
    void recordTest() {
        ShipmentItem item = new ShipmentItem("hgw-23e", "WE-RF7", "7687-78ed",
                LocalDate.of(2026, 11, 20), Instant.parse("2026-11-20T09:15:00Z"),
                ShipmentState.REGISTERED, 2, new BigDecimal("16.99"), "manifests/hgw-23e.json");

        ShipmentSummary summary = mapper.toSummary(item);

        assertThat(summary.shipmentRef()).isEqualTo(item.shipmentRef());
        assertThat(summary.manifestNo()).isEqualTo(item.manifestNo());
        assertThat(summary.carrierId()).isEqualTo(item.carrierId());
        assertThat(summary.dispatchDate()).isEqualTo(item.dispatchDate());
        assertThat(summary.registeredAt()).isEqualTo(item.registeredAt());
        assertThat(summary.state()).isEqualTo(item.state());
        assertThat(summary.parcelCount()).isEqualTo(item.parcelCount());
        assertThat(summary.totalCharge()).isEqualByComparingTo(item.totalCharge());
        assertThat(summary.manifestKey()).isEqualTo(item.manifestKey());
    }

    @Test
    void summaryTest() {
        ShipmentItem item = new ShipmentItem("hgw-23e", "WE-RF7", "7687-78ed",
                LocalDate.of(2026, 11, 20), Instant.parse("2026-11-20T09:15:00Z"),
                ShipmentState.REGISTERED, 2, new BigDecimal("16.99"), "manifests/hgw-23e.json");

        Tariff tariff = new Tariff("TRF-1", "Standard", new BigDecimal("4.50"),
                new BigDecimal("1.20"), new BigDecimal("0.10"));

        RatedParcel rated = new RatedParcel(13, "TRF-1", new BigDecimal("2.5"), tariff, new BigDecimal("8.25"));
        RatedParcel unrated = new RatedParcel(12, "TRF-5", new BigDecimal("0.8"), null, null);

        ShipmentRecord record = mapper.toRecord(item, List.of(rated, unrated));

        assertThat(record.shipmentRef()).isEqualTo(item.shipmentRef());
        assertThat(record.manifestNo()).isEqualTo(item.manifestNo());
        assertThat(record.carrierId()).isEqualTo(item.carrierId());
        assertThat(record.dispatchDate()).isEqualTo(item.dispatchDate());
        assertThat(record.registeredAt()).isEqualTo(item.registeredAt());
        assertThat(record.state()).isEqualTo(item.state());
        assertThat(record.parcelCount()).isEqualTo(item.parcelCount());
        assertThat(record.totalCharge()).isEqualByComparingTo(item.totalCharge());
        assertThat(record.manifestKey()).isEqualTo(item.manifestKey());

        assertThat(record.parcels().get(0).charge()).isEqualByComparingTo("8.25");
        assertThat(record.parcels().get(0).rating()).isEqualTo(Rating.RATED);
        assertThat(record.parcels().get(1).charge()).isNull();
        assertThat(record.parcels().get(1).rating()).isEqualTo(Rating.UNRATED);
    }

}

