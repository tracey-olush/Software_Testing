package de.campusshop.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound body of {@code POST /dispatch/manifests}. Fields the service does not know are ignored
 * here; they survive only in the archived raw manifest.
 */
public record DispatchManifest(
        @NotBlank String manifestNo,
        @NotNull @Valid CarrierDto carrier,
        @NotNull LocalDate dispatchDate,
        String originWarehouse,
        @NotEmpty List<@NotNull @Valid ParcelDto> parcels) {
}
