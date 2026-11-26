package de.campusshop.dispatch.client;

import de.campusshop.dispatch.model.Tariff;

import java.util.Optional;

/**
 * Looks up the tariff for a code. The service depends only on this interface; the REST client and
 * the in-process simulation are chosen by {@code tariff-service.mode}.
 */
public interface TariffClient {

    /**
     * @return the tariff, or empty when it cannot be resolved (unknown code, timeout or any other
     *         failure). Callers treat empty as an {@code UNRATED} parcel.
     */
    Optional<Tariff> findTariff(String tariffCode);
}
