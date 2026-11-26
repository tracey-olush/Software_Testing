package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * S3 implementation of {@link ManifestArchive}. The bucket is pre-created by the environment; never
 * create it here.
 *
 * <p>implement the three methods with the injected {@link S3Client} — see CW2 spec §4.1.
 */
@Repository
@Profile("!inmemory")
public class S3ManifestArchive implements ManifestArchive {

    private final S3Client s3;
    private final String bucket;

    public S3ManifestArchive(S3Client s3, DispatchProperties properties) {
        this.s3 = s3;
        this.bucket = properties.archive().bucket();
    }

    /** PutObject: the exact bytes, content type application/json. */
    @Override
    public void archive(String manifestKey, byte[] manifest) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(manifestKey)
                .contentType("application/json").build(), RequestBody.fromBytes(manifest));
    }

    /** GetObject: the stored bytes, or empty when the key does not exist. */
    @Override
    public Optional<byte[]> fetch(String manifestKey) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket)
                    .key(manifestKey).build()).asByteArray());
        }
        catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    /** DeleteObject. */
    @Override
    public void remove(String manifestKey) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(manifestKey).build());
    }
}
