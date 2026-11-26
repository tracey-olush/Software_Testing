package de.campusshop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.exception.ManifestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Parses and validates the raw request bytes. The controller keeps the bytes for the archive and
 * passes them here, so the archived manifest is never a re-serialised DTO.
 */
@Component
public class ManifestReader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ManifestReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * @throws IOException                 when the bytes are not readable JSON for a manifest
     * @throws ManifestValidationException when a field fails validation
     */
    public DispatchManifest read(byte[] body) throws IOException {
        DispatchManifest manifest = objectMapper.readValue(body, DispatchManifest.class);
        List<String> violations = validator.validate(manifest).stream()
                .map(ManifestReader::describe)
                .sorted()
                .toList();
        if (!violations.isEmpty()) {
            throw new ManifestValidationException(violations);
        }
        return manifest;
    }

    private static String describe(ConstraintViolation<DispatchManifest> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
