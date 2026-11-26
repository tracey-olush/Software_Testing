package de.campusshop.dispatch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * The carrier block of an inbound manifest.
 */
public record CarrierDto(
        @NotBlank String carrierId,
        String name,
        @NotBlank @Email String contactEmail) {
}
