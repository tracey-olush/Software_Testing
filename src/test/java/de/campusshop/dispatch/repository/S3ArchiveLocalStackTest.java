package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import org.junit.jupiter.api.*;
//import org.testcontainers.containers.localstack.LocalStackContainer;
//import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Real S3 behaviour on LocalStack. Needs Docker.
@Disabled
@Tag("localstack")
class S3ArchiveLocalStackTest {

    private static LocalStackContainer localstack;
    private static S3Client s3;
    private static S3ManifestArchive archive;

    @BeforeAll
    static void setUp() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(LocalStackContainer.Service.S3);
        localstack.start();
        s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3.createBucket(b -> b.bucket("manifests-it"));
        archive = new S3ManifestArchive(s3,
                new DispatchProperties(new DispatchProperties.Archive("manifests-it"), null, null));
    }

    @AfterAll
    static void tearDown() {
        if (s3 != null) {
            s3.close();
        }
        if (localstack != null) {
            localstack.stop();
        }
    }

    @Test
    void storesAndReturnsTheExactBytes() {
        byte[] bytes = "{ \"x\": \"UK\" }\r\n".getBytes(StandardCharsets.UTF_8);

        archive.archive("manifests/weu-23/jneo-q23.json", bytes);

        assertThat(archive.fetch("manifests/weu-23/jneo-q23.json").orElseThrow()).isEqualTo(bytes);
    }

    @Test
    void missingKeyIsEmpty() {
        assertThat(archive.fetch("missing")).isEmpty();
    }

    @Test
    void removeDeletesTheObject() {
        archive.archive("manifests/weu-23/neo-q21.json", new byte[]{1});

        archive.remove("manifests/weu-23/neo-q21.json");

        assertThat(archive.fetch("manifests/weu-23/neo-q21.json")).isEmpty();
    }

    @Test
    void removingAMissingKeyIsNotAnError() {
        assertThatCode(() -> archive.remove("missing")).doesNotThrowAnyException();
    }
}*/
