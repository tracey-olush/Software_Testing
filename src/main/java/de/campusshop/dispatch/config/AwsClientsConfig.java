package de.campusshop.dispatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Builds the AWS SDK clients once, from configuration. Not active under the {@code inmemory}
 * profile, where the in-memory repositories replace S3 and DynamoDB.
 */
@Configuration
@Profile("!inmemory")
public class AwsClientsConfig {

    private final DispatchProperties.Aws aws;

    public AwsClientsConfig(DispatchProperties properties) {
        this.aws = properties.aws();
    }

    @Bean
    S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(aws.endpoint()))
                .region(Region.of(aws.region()))
                .credentialsProvider(credentials())
                .forcePathStyle(true) // LocalStack serves buckets by path, not by sub-domain
                .build();
    }

    @Bean
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(aws.endpoint()))
                .region(Region.of(aws.region()))
                .credentialsProvider(credentials())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(aws.accessKey(), aws.secretKey()));
    }
}
