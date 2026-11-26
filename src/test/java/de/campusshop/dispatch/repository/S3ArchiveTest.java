package de.campusshop.dispatch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import de.campusshop.dispatch.config.DispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ArchiveTest {

    private static final String BUCKET = "manifests-test";
    private static final String KEY = "manifests/S-1.json";

    @Mock
    private S3Client s3;

    private S3ManifestArchive archive;

    @BeforeEach
    void setUp() {
        DispatchProperties properties = mock(DispatchProperties.class, RETURNS_DEEP_STUBS);
        when(properties.archive().bucket()).thenReturn(BUCKET);
        archive = new S3ManifestArchive(s3, properties);
    }

    @Test
    void archiveUploadsTheExactBytesAsJson() throws IOException {
        byte[] manifest = "{\"carrierInternalNotes\": \"must be preserved\"}".getBytes(StandardCharsets.UTF_8);

        archive.archive(KEY, manifest);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(requestCaptor.capture(), bodyCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(KEY);
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/json");
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
                .isEqualTo(manifest);
    }

    @Test
    void fetchReturnsTheStoredBytes() {
        byte[] stored = "{}".getBytes(StandardCharsets.UTF_8);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), stored));

        Optional<byte[]> result = archive.fetch(KEY);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(stored);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void fetchOfAMissingKeyIsEmpty() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        assertThat(archive.fetch("missing")).isEmpty();
    }

    @Test
    void fetchDoesNotSwallowOtherS3Errors() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        assertThatThrownBy(() -> archive.fetch(KEY)).isInstanceOf(S3Exception.class);
    }

    @Test
    void removeDeletesTheObjectFromTheBucket() {
        archive.remove(KEY);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }
}