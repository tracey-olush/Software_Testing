package de.campusshop.dispatch.repository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;


public class InMemoryManifestArchiveTest {
    private final InMemoryManifestArchive archive = new InMemoryManifestArchive();

    @Test
    void storesAndReturnsTheExactBytes() {
        byte[] bytes = "{ \"x\": \"Load\" }\r\n".getBytes(StandardCharsets.UTF_8);

        archive.archive("manifests/ehye-34/reye5-231.json", bytes);

        assertThat(archive.fetch("manifests/ehye-34/reye5-231.json").orElseThrow()).isEqualTo(bytes);
    }

    @Test
    void changingTheCallersArrayDoesNotChangeTheArchive() {
        byte[] bytes = {1, 2, 3};
        archive.archive("manifests/ehye-34/w4gw9.json", bytes);

        bytes[0] = 9;

        assertThat(archive.fetch("manifests/ehye-34/w4gw9.json").orElseThrow()).containsExactly(1, 2, 3);
    }

    @Test
    void missingKeyIsEmpty() {
        assertThat(archive.fetch("missing")).isEmpty();
    }

    @Test
    void removeDeletesTheObject() {
        archive.archive("manifests/ehye-34/2f24g-3.json", new byte[]{1});

        archive.remove("manifests/ehye-34/2f24g-3.json");

        assertThat(archive.fetch("manifests/ehye-34/2f24g-3.json")).isEmpty();
    }

    @Test
    void removingAMissingKeyIsNotAnError() {
        assertThatCode(() -> archive.remove("missing")).doesNotThrowAnyException();
    }
}
