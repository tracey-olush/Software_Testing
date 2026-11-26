package de.campusshop.dispatch.exception;

/**
 * A manifest with this {@code manifestNo} was already registered. Mapped to
 * {@code 409 DUPLICATE_MANIFEST}; nothing is written.
 */
public class DuplicateManifestException extends RuntimeException {

    public DuplicateManifestException(String manifestNo) {
        super("Manifest " + manifestNo + " has already been registered");
    }
}
