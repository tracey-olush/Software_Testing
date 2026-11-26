package de.campusshop.dispatch.exception;

import java.util.List;

/** The manifest failed validation. Mapped to {@code 400 VALIDATION_FAILED}. */
public class ManifestValidationException extends RuntimeException {

    private final List<String> violations;

    public ManifestValidationException(List<String> violations) {
        super("Manifest validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
