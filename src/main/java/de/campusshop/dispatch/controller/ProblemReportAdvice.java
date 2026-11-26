package de.campusshop.dispatch.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import de.campusshop.dispatch.dto.ProblemReport;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.exception.ManifestValidationException;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;

/**
 * Turns every failure into a {@link ProblemReport} with a stable {@code errorCode}.
 */
@RestControllerAdvice
public class ProblemReportAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemReportAdvice.class);

    private final Clock clock;

    public ProblemReportAdvice(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ManifestValidationException.class)
    ResponseEntity<ProblemReport> invalid(ManifestValidationException ex, HttpServletRequest request) {
        return report(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request, ex.violations());
    }

    /** Well-formed JSON with a value of the wrong type, e.g. a dispatchDate that is not a date. */
    @ExceptionHandler(MismatchedInputException.class)
    ResponseEntity<ProblemReport> mismatched(MismatchedInputException ex, HttpServletRequest request) {
        return report(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Manifest field has the wrong type", request,
                List.of(ex.getPathReference()));
    }

    /** Not readable as JSON at all, e.g. a truncated body. */
    @ExceptionHandler(JsonProcessingException.class)
    ResponseEntity<ProblemReport> malformed(JsonProcessingException ex, HttpServletRequest request) {
        return report(HttpStatus.BAD_REQUEST, "MALFORMED_MANIFEST", "Manifest is not valid JSON", request, List.of());
    }

    @ExceptionHandler(DuplicateManifestException.class)
    ResponseEntity<ProblemReport> duplicate(DuplicateManifestException ex, HttpServletRequest request) {
        return report(HttpStatus.CONFLICT, "DUPLICATE_MANIFEST", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ShipmentNotFoundException.class)
    ResponseEntity<ProblemReport> notFound(ShipmentNotFoundException ex, HttpServletRequest request) {
        return report(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", ex.getMessage(), request, List.of());
    }

    /** Requests Spring rejects before a controller runs (unknown path, wrong method or media type). */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ProblemReport> rejected(Exception ex, HttpServletRequest request) {
        HttpStatusCode status = ((ErrorResponse) ex).getStatusCode();
        return report(status, "REQUEST_REJECTED", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemReport> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return report(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request, List.of());
    }

    private ResponseEntity<ProblemReport> report(HttpStatusCode status, String errorCode, String detail,
                                                 HttpServletRequest request, List<String> violations) {
        return ResponseEntity.status(status)
                .body(new ProblemReport(errorCode, detail, clock.instant(), request.getRequestURI(), violations));
    }
}
