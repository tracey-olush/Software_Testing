package de.campusshop.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the external tariff service.
 *
 * @param mode      {@code rest} (call the service) or {@code simulation} (in-process tariffs)
 * @param url       base URL of the tariff service ({@code TARIFF_SERVICE_URL})
 * @param timeoutMs connect and read timeout in milliseconds
 */
@ConfigurationProperties(prefix = "tariff-service")
public record TariffServiceProperties(String mode, String url, int timeoutMs) {
}
