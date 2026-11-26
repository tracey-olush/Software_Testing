package de.campusshop.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Body of every error response.
 *
 * @param violations validation messages; omitted when empty
 */
public record ProblemReport(
        String errorCode,
        String detail,
        Instant occurredAt,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> violations) {
}
