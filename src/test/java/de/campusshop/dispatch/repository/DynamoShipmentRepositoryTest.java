package de.campusshop.dispatch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import de.campusshop.dispatch.config.DispatchProperties;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.paginators.QueryIterable;

@ExtendWith(MockitoExtension.class)
class DynamoShipmentRepositoryTest {

    private static final String TABLE = "shipments-test";

    @Mock
    private DynamoDbClient dynamo;

    private DynamoShipmentRepository repo;

    @BeforeEach
    void setUp() {
        DispatchProperties properties = mock(DispatchProperties.class, RETURNS_DEEP_STUBS);
        when(properties.shipments().table()).thenReturn(TABLE);
        repo = new DynamoShipmentRepository(dynamo, properties);
    }

    private static ShipmentItem item(String ref, BigDecimal totalCharge) {
        return new ShipmentItem(ref, "KW-53", "HG-Y7", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 2,
                totalCharge, "manifests/" + ref + ".json");
    }

    private static Map<String, AttributeValue> dynamoItem(String ref) {
        return Map.of(
                "shipmentRef", AttributeValue.fromS(ref),
                "manifestNo", AttributeValue.fromS("KW-53"),
                "carrierId", AttributeValue.fromS("HG-Y7"),
                "dispatchDate", AttributeValue.fromS("2026-11-20"),
                "registeredAt", AttributeValue.fromS("2026-11-20T09:15:00Z"),
                "state", AttributeValue.fromS("REGISTERED"),
                "parcelCount", AttributeValue.fromN("2"),
                "totalCharge", AttributeValue.fromN("16.99"),
                "manifestKey", AttributeValue.fromS("manifests/" + ref + ".json"));
    }

    @Test
    void putWritesEveryAttributeToTheTable() {
        repo.put(item("hb-23d", new BigDecimal("16.99")));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(captor.capture());
        PutItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE);
        Map<String, AttributeValue> written = request.item();
        assertThat(written).hasSize(9);
        assertThat(written.get("shipmentRef").s()).isEqualTo("hb-23d");
        assertThat(written.get("manifestNo").s()).isEqualTo("KW-53");
        assertThat(written.get("carrierId").s()).isEqualTo("HG-Y7");
        assertThat(written.get("dispatchDate").s()).isEqualTo("2026-11-20");
        assertThat(written.get("registeredAt").s()).isEqualTo("2026-11-20T09:15:00Z");
        assertThat(written.get("state").s()).isEqualTo("REGISTERED");
        assertThat(written.get("parcelCount").n()).isEqualTo("2");
        assertThat(written.get("totalCharge").n()).isEqualTo("16.99");
        assertThat(written.get("manifestKey").s()).isEqualTo("manifests/hb-23d.json");
    }

    @Test
    void putWritesTotalChargeAsAPlainNumber() {
        // toPlainString: 1E+1 must be sent as 10, not in scientific notation
        repo.put(item("hb-23d", new BigDecimal("1E+1")));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(captor.capture());

        assertThat(captor.getValue().item().get("totalCharge").n()).isEqualTo("10");
    }

    @Test
    void getReturnsTheMappedItemUsingAConsistentRead() {
        when(dynamo.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(dynamoItem("hb-23d")).build());

        var found = repo.get("hb-23d");

        assertThat(found).contains(item("hb-23d", new BigDecimal("16.99")));

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamo).getItem(captor.capture());
        assertThat(captor.getValue().tableName()).isEqualTo(TABLE);
        assertThat(captor.getValue().key()).containsEntry("shipmentRef", AttributeValue.fromS("hb-23d"));
        assertThat(captor.getValue().consistentRead()).isTrue();
    }

    @Test
    void getOfAnUnknownRefIsEmpty() {
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        assertThat(repo.get("missing")).isEmpty();
    }

    @Test
    void manifestExistsIsTrueWhenTheIndexHasAMatch() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().count(1).build());

        assertThat(repo.manifestExists("QERF-1")).isTrue();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE);
        assertThat(request.indexName()).isEqualTo("manifest-index");
        assertThat(request.keyConditionExpression()).isEqualTo("manifestNo = :m");
        assertThat(request.expressionAttributeValues()).containsEntry(":m", AttributeValue.fromS("QERF-1"));
    }

    @Test
    void manifestExistsIsFalseWhenTheIndexHasNoMatch() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().count(0).build());

        assertThat(repo.manifestExists("MAN-unknown")).isFalse();
    }

    @Test
    void listByCarrierQueriesTheDispatchIndexOldestFirstAndFollowsPages() {
        QueryResponse page1 = QueryResponse.builder()
                .items(dynamoItem("hb-23d"))
                .lastEvaluatedKey(Map.of("shipmentRef", AttributeValue.fromS("hb-23d")))
                .build();
        QueryResponse page2 = QueryResponse.builder().items(dynamoItem("ytgt-6")).build();
        when(dynamo.queryPaginator(any(QueryRequest.class)))
                .thenAnswer(invocation -> new QueryIterable(dynamo, invocation.getArgument(0)));
        when(dynamo.query(any(QueryRequest.class))).thenReturn(page1, page2);

        List<ShipmentItem> result = repo.listByCarrier("HG-Y7");

        assertThat(result).extracting(ShipmentItem::shipmentRef).containsExactly("hb-23d", "ytgt-6");

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo, times(2)).query(captor.capture());
        for (QueryRequest request : captor.getAllValues()) {
            assertThat(request.tableName()).isEqualTo(TABLE);
            assertThat(request.indexName()).isEqualTo("carrier-dispatch-index");
            assertThat(request.keyConditionExpression()).isEqualTo("carrierId = :c");
            assertThat(request.expressionAttributeValues()).containsEntry(":c", AttributeValue.fromS("HG-Y7"));
            assertThat(request.scanIndexForward()).isTrue();
        }
    }

    @Test
    void listByCarrierWithNoItemsIsEmpty() {
        when(dynamo.queryPaginator(any(QueryRequest.class)))
                .thenAnswer(invocation -> new QueryIterable(dynamo, invocation.getArgument(0)));
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().build());

        assertThat(repo.listByCarrier("nobody")).isEmpty();
    }

    @Test
    void removeIsTrueWhenDynamoReturnsTheOldItem() {
        when(dynamo.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(DeleteItemResponse.builder().attributes(dynamoItem("hb-23d")).build());

        assertThat(repo.remove("hb-23d")).isTrue();

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamo).deleteItem(captor.capture());
        assertThat(captor.getValue().tableName()).isEqualTo(TABLE);
        assertThat(captor.getValue().key()).containsEntry("shipmentRef", AttributeValue.fromS("hb-23d"));
        assertThat(captor.getValue().returnValues()).isEqualTo(ReturnValue.ALL_OLD);
    }

    @Test
    void removeIsFalseWhenNothingWasDeleted() {
        when(dynamo.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

        assertThat(repo.remove("missing")).isFalse();
    }
}