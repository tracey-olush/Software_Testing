package de.campusshop.dispatch.controller;

import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.service.ManifestReader;
import de.campusshop.dispatch.service.ShipmentRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Accepts dispatch manifests. The body is taken as raw bytes so exactly what the partner sent can be
 * archived; parsing and validation happen on a copy of those bytes.
 */
@RestController
public class ManifestController {

    private final ManifestReader reader;
    private final ShipmentRegistrationService registration;

    public ManifestController(ManifestReader reader, ShipmentRegistrationService registration) {
        this.reader = reader;
        this.registration = registration;
    }

    /**
     * Register a dispatch manifest. Archives the raw manifest in S3, prices every parcel through the
     * tariff service and indexes the shipment in DynamoDB. 400 for unreadable or invalid manifests,
     * 409 for a manifestNo that was already registered.
     */
    @PostMapping(value = "/dispatch/manifests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentRecord register(@RequestBody byte[] body) throws IOException {
        DispatchManifest manifest = reader.read(body);
        return registration.register(manifest, body);
    }
}
