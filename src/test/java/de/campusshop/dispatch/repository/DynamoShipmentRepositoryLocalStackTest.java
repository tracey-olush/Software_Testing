package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import org.junit.jupiter.api.*;
//import org.testcontainers.containers.localstack.LocalStackContainer;
//import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Real DynamoDB behaviour on LocalStack. Needs Docker. CHECK the table against spec Appendix D.
@Disabled
@Tag("localstack")
class DynamoShipmentRepositoryLocalStackTest {

    private static LocalStackContainer localstack;
    private static DynamoDbClient dynamo;
    private static DynamoShipmentRepository repo;

    @BeforeAll
    static void setUp() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(LocalStackContainer.Service.DYNAMODB);
        localstack.start();
        dynamo = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        dynamo.createTable(table -> table
                .tableName("shipments-it")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("shipmentRef")
                                .attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("manifestNo")
                                .attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("carrierId")
                                .attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("dispatchDate")
                                .attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("shipmentRef").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder().indexName("manifest-index")
                                .keySchema(KeySchemaElement.builder().attributeName("manifestNo")
                                        .keyType(KeyType.HASH).build())
                                .projection(p -> p.projectionType(ProjectionType.ALL))
                                .build(),
                        GlobalSecondaryIndex.builder().indexName("carrier-dispatch-index")
                                .keySchema(
                                        KeySchemaElement.builder().attributeName("carrierId")
                                                .keyType(KeyType.HASH).build(),
                                        KeySchemaElement.builder().attributeName("dispatchDate")
                                                .keyType(KeyType.RANGE).build())
                                .projection(p -> p.projectionType(ProjectionType.ALL))
                                .build()));
        dynamo.waiter().waitUntilTableExists(r -> r.tableName("shipments-it"));
        repo = new DynamoShipmentRepository(dynamo,
                new DispatchProperties(null, new DispatchProperties.Shipments("shipments-it"), null));
    }

    @AfterAll
    static void tearDown() {
        if (dynamo != null) {
            dynamo.close();
        }
        if (localstack != null) {
            localstack.stop();
        }
    }


    @Test
    void putThenGetReturnsTheItem() {
        ShipmentItem item = new ShipmentItem("wiue-023", "jds-203", "fei0-eq", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/fei0-eq/wiue-023.json");

        repo.put(item);

        assertThat(repo.get("wiue-023")).contains(item);
    }

    @Test
    void unknownRefIsEmpty() {
        assertThat(repo.get("missing")).isEmpty();
    }

    @Test
    void manifestExistsOnlyAfterPut() {
        assertThat(repo.manifestExists("jds-234")).isFalse();

        repo.put(new ShipmentItem("wiue-0121", "jds-234", "fei0-efww", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/fei0-efww/wiue-0121.json"));

        assertThat(repo.manifestExists("jds-234")).isTrue();
    }

    @Test
    void listsOnlyThatCarrierOldestFirst() {
        repo.put(new ShipmentItem("new", "rtr-234", "gei0-efww", LocalDate.of(2027, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/gei0-efww/new.json"));
        repo.put(new ShipmentItem("other", "rtr-we4", "gei0-e922", LocalDate.of(2026, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/gei0-e922/other.json"));
        repo.put(new ShipmentItem("old", "ewh-we2", "gei0-efww", LocalDate.of(2025, 1, 1),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/gei0-efww/old.json"));

        assertThat(repo.listByCarrier("gei0-efww")).extracting(ShipmentItem::shipmentRef)
                .containsExactly("old", "new");
    }

    @Test
    void removeDeletesTheItemAndReturnsTrue() {
        repo.put(new ShipmentItem("weffw-0121", "ewh-w23", "gei0-e9wqe", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1,
                new BigDecimal("8.25"), "manifests/gei0-e9wqe/weffw-0121.json"));

        assertThat(repo.remove("weffw-0121")).isTrue();
        assertThat(repo.get("weffw-0121")).isEmpty();
    }

    @Test
    void removeIsFalseForAMissingItem() {
        assertThat(repo.remove("missing")).isFalse();
    }
}
*/