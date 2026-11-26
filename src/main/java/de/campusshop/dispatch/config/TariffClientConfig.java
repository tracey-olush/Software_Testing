package de.campusshop.dispatch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} for the tariff service with its base URL and timeouts.
 */
@Configuration
@ConditionalOnProperty(name = "tariff-service.mode", havingValue = "rest", matchIfMissing = true)
public class TariffClientConfig {

    @Bean
    RestClient tariffRestClient(RestClient.Builder builder, TariffServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        return builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
    }
}
