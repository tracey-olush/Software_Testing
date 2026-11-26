package de.campusshop.dispatch.client;

import de.campusshop.dispatch.dto.TariffResponse;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.Tariff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Calls the external tariff service ({@code GET {url}/tariffs/{tariffCode}}).
 *
 * <p>Base URL and timeouts are already set on the injected {@link RestClient} in
 * {@link de.campusshop.dispatch.config.TariffClientConfig}.
 *
 * <p> implement {@link #findTariff(String)} — see CW2 spec §3.
 */
@Component
@ConditionalOnProperty(name = "tariff-service.mode", havingValue = "rest", matchIfMissing = true)
public class RestTariffClient implements TariffClient {

    private static final Logger log = LoggerFactory.getLogger(RestTariffClient.class);

    private final RestClient restClient;
    private final ManifestMapper mapper;

    public RestTariffClient(RestClient tariffRestClient, ManifestMapper mapper) {
        this.restClient = tariffRestClient;
        this.mapper = mapper;
    }

    /**
     * Calls the tariff service and maps the JSON to the internal model. A {@code 404}, a timeout or
     * any other failure must end as an empty result, never as an exception.
     */
    @Override
    public Optional<Tariff> findTariff(String tariffCode) {
        try {
            TariffResponse tariff = restClient.get().uri("/tariffs/{tariffCode}", tariffCode)
            .retrieve().body(TariffResponse.class);

            return Optional.ofNullable(tariff).map(mapper::toTariff);
        }
        catch (RestClientException e) {
            log.warn("Cannot get traffic code={} : {}", tariffCode, e.getMessage());
            return Optional.empty();
        }
    }
}
