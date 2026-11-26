package de.campusshop.dispatch.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ManifestArchive} for tests and IDE runs ({@code inmemory} profile).
 */
@Repository
@Profile("inmemory")
public class InMemoryManifestArchive implements ManifestArchive {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void archive(String manifestKey, byte[] manifest) {
        objects.put(manifestKey, manifest.clone());
    }

    @Override
    public Optional<byte[]> fetch(String manifestKey) {
        return Optional.ofNullable(objects.get(manifestKey)).map(byte[]::clone);
    }

    @Override
    public void remove(String manifestKey) {
        objects.remove(manifestKey);
    }
}
