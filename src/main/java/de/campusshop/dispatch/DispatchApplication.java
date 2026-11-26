package de.campusshop.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CampusShop DataHub — Dispatch Manifest Hub (Coursework 2).
 *
 * <p>Registers carrier dispatch manifests: the raw manifest is archived in S3, every parcel is priced
 * through the external tariff service, and the shipment is indexed in DynamoDB.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class, args);
    }
}
