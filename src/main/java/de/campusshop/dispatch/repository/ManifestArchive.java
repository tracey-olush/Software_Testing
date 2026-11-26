package de.campusshop.dispatch.repository;

import java.util.Optional;

/**
 * Archive of raw manifests, addressed by their S3 key. Implemented with S3 at runtime and in memory
 * for tests.
 */
public interface ManifestArchive {

    /** Stores the manifest bytes exactly as received (PutObject). */
    void archive(String manifestKey, byte[] manifest);

    /** The stored bytes, or empty when there is no object under the key (GetObject). */
    Optional<byte[]> fetch(String manifestKey);

    /** Removes the object; removing a missing key is not an error (DeleteObject). */
    void remove(String manifestKey);
}
