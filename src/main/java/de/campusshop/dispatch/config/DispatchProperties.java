package de.campusshop.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage settings: the S3 archive bucket, the DynamoDB shipment table and the AWS / LocalStack
 * connection. Every value comes from an environment variable (see {@code application.yml}).
 */
@ConfigurationProperties(prefix = "dispatch")
public record DispatchProperties(Archive archive, Shipments shipments, Aws aws) {

    /** @param bucket S3 bucket holding the archived manifests ({@code MANIFEST_ARCHIVE_BUCKET}) */
    public record Archive(String bucket) {
    }

    /** @param table DynamoDB shipment table ({@code SHIPMENT_TABLE}) */
    public record Shipments(String table) {
    }

    /** AWS / LocalStack connection details. */
    public record Aws(String endpoint, String region, String accessKey, String secretKey) {
    }
}
