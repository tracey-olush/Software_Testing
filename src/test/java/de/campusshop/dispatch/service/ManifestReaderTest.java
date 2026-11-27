package de.campusshop.dispatch.service;

import de.campusshop.dispatch.exception.ManifestValidationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ManifestReaderTest {
    private final ManifestReader reader = new ManifestReader(Jackson2ObjectMapperBuilder.json().build(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void readsAValidManifest() throws IOException {
        String json = """
                {"manifestNo": "1345-234r53", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        var manifest = reader.read(json.getBytes(StandardCharsets.UTF_8));

        assertThat(manifest.manifestNo()).isEqualTo("1345-234r53");
        assertThat(manifest.carrier().carrierId()).isEqualTo("2345te-234r");
        assertThat(manifest.parcels()).hasSize(1);
        assertThat(manifest.parcels().getFirst().weightKg()).isEqualByComparingTo("2.5");
    }

    @Test
    void rejectsZeroWeight() {
        String json = """
                {"manifestNo": "35EH-WTW5", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 0}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("parcels[0].weightKg:"));
    }

    @Test
    void rejectsNegativeWeight() {
        String json = """
                {"manifestNo": "1DSI-3T4", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": -0.001}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("parcels[0].weightKg:"));
    }

    @Test
    void rejectsMissingWeight() {
        String json = """
                {"manifestNo": "DSIH-UW3T", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1"}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("parcels[0].weightKg:"));
    }


    @Test
    void rejectsEmptyManifestNo() {
        String json = """
                {"manifestNo": "", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("manifestNo:"));
    }


    @Test
    void rejectsMissingManifestNo() {
        String json = """
                {"carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("manifestNo:"));
    }

    @Test
    void rejectsMissingCarrier() {
        String json = """
                {"manifestNo": "3TS4-WTYE", "dispatchDate": "2026-11-20",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("carrier:"));
    }

    @Test
    void rejectsBlankCarrierId() {
        String json = """
                {"manifestNo": "1345-234r50", "carrier": {"carrierId": "  ", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("carrier.carrierId:"));
    }

    @Test
    void rejectsInvalidEmail() {
        String json = """
                {"manifestNo": "1345-234r61", "carrier": {"carrierId": "2345te-234r", "contactEmail": "wrong-email-com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString()
                                .startsWith("carrier.contactEmail:"));
    }

    @Test
    void rejectsMissingEmail() {
        String json = """
                {"manifestNo": "1345-234r32", "carrier": {"carrierId": "2345te-234r"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString()
                                .startsWith("carrier.contactEmail:"));
    }

    @Test
    void rejectsMissingDispatchDate() {
        String json = """
                {"manifestNo": "1345-234r23", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("dispatchDate:"));
    }

    @Test
    void rejectsEmptyParcelList() {
        String json = """
                {"manifestNo": "1345-234r14", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": []}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("parcels:"));
    }

    @Test
    void rejectsMissingParcelList() {
        String json = """
                {"manifestNo": "1345-234r95", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20"}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString().startsWith("parcels:"));
    }

    @Test
    void rejectsBlankTariffCode() {
        String json = """
                {"manifestNo": "1345-264r97", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [{"tariffCode": "  ", "weightKg": 2.5}]}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).singleElement().asString()
                                .startsWith("parcels[0].tariffCode:"));
    }

    @Test
    void reportsEveryViolationInSortedOrder() {
        String json = """
                {"manifestNo": "", "carrier": {"carrierId": "", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": []}
                """;

        assertThatThrownBy(() -> reader.read(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ManifestValidationException.class, e ->
                        assertThat(e.violations()).hasSize(3).isSorted());
    }

    @Test
    void acceptsAThousandParcels() throws IOException {
        String json = """
                {"manifestNo": "1945-264r98", "carrier": {"carrierId": "2345te-234r", "contactEmail": "ops@example.com"},
                "dispatchDate": "2026-11-20", "parcels": [%s{"tariffCode": "TRF-1", "weightKg": 2.5}]}
                """.formatted("{\"tariffCode\": \"TRF-1\", \"weightKg\": 2.5},".repeat(999));

        var manifest = reader.read(json.getBytes(StandardCharsets.UTF_8));

        assertThat(manifest.parcels()).hasSize(1000);
    }

    @Test
    void emptyBodyThrows() {
        assertThatThrownBy(() -> reader.read(new byte[0]))
                .isInstanceOf(IOException.class);
    }

}
