package de.campusshop.dispatch.client;

import de.campusshop.dispatch.model.Tariff;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process tariffs for offline development and tests ({@code tariff-service.mode=simulation}).
 * Produces the same values as the default catalog of the reference tariff service for
 * {@code TRF-1} … {@code TRF-500}; any other code is unknown.
 */
@Component
@ConditionalOnProperty(name = "tariff-service.mode", havingValue = "simulation")
public class SimulationTariffClient implements TariffClient {

    private static final Pattern CODE = Pattern.compile("TRF-(\\d+)");

    private static final String[] NAMES = {"Economy", "Standard", "Express", "Freight"};
    private static final String[] BASE_FEES = {"3.00", "4.50", "6.00", "12.00"};
    private static final String[] PRICES_PER_KG = {"0.80", "1.20", "2.00", "0.60"};
    private static final String[] SURCHARGES = {"0.05", "0.10", "0.15", "0.08"};

    @Override
    public Optional<Tariff> findTariff(String tariffCode) {
        Matcher matcher = CODE.matcher(tariffCode);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int n = Integer.parseInt(matcher.group(1));
        if (n < 1 || n > 500) {
            return Optional.empty();
        }
        int tier = n % NAMES.length;
        int step = n / NAMES.length;
        BigDecimal baseFee = new BigDecimal(BASE_FEES[tier]).add(new BigDecimal("0.10").multiply(BigDecimal.valueOf(step % 10)));
        BigDecimal pricePerKg = new BigDecimal(PRICES_PER_KG[tier]).add(new BigDecimal("0.05").multiply(BigDecimal.valueOf(step % 5)));
        return Optional.of(new Tariff(tariffCode, NAMES[tier], baseFee, pricePerKg, new BigDecimal(SURCHARGES[tier])));
    }
}
