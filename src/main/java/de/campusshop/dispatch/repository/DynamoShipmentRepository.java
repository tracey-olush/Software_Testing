package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * DynamoDB implementation of {@link ShipmentRepository}. The table and both GSIs are pre-created by
 * the environment; never create them here. Use {@code GetItem} and {@code Query} only — no
 * {@code Scan}.
 *
 * <ul>
 *   <li>partition key {@code shipmentRef};</li>
 *   <li>{@code manifest-index}: partition key {@code manifestNo} (duplicate check);</li>
 *   <li>{@code carrier-dispatch-index}: partition key {@code carrierId}, sort key {@code dispatchDate}.</li>
 * </ul>
 *
 * <p>implement the five methods — see CW2 spec §4.2 and Appendix D.
 */
@Repository
@Profile("!inmemory")
public class DynamoShipmentRepository implements ShipmentRepository {

    static final String MANIFEST_INDEX = "manifest-index";
    static final String CARRIER_DISPATCH_INDEX = "carrier-dispatch-index";

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoShipmentRepository(DynamoDbClient dynamo, DispatchProperties properties) {
        this.dynamo = dynamo;
        this.table = properties.shipments().table();
    }

    private ShipmentItem toShipment (Map<String, AttributeValue> item) {
        return new ShipmentItem(item.get("shipmentRef").s(), item.get("manifestNo").s(), item.get("carrierId").s(),
                LocalDate.parse(item.get("dispatchDate").s()),
                Instant.parse(item.get("registeredAt").s()), ShipmentState.valueOf(item.get("state").s()),
                Integer.parseInt(item.get("parcelCount").n()),
                new BigDecimal(item.get("totalCharge").n()), item.get("manifestKey").s());
    }

    @Override
    public void put(ShipmentItem item) {
        Map<String, AttributeValue> items = new HashMap<String, AttributeValue>();
        items.put("shipmentRef", AttributeValue.fromS(item.shipmentRef()));
        items.put("manifestNo", AttributeValue.fromS(item.manifestNo()));
        items.put("carrierId", AttributeValue.fromS(item.carrierId()));
        items.put("dispatchDate", AttributeValue.fromS(item.dispatchDate().toString()));
        items.put("registeredAt", AttributeValue.fromS(item.registeredAt().toString()));
        items.put("state", AttributeValue.fromS(item.state().toString()));
        items.put("parcelCount", AttributeValue.fromN(String.valueOf(item.parcelCount())));
        items.put("totalCharge", AttributeValue.fromN(item.totalCharge().toPlainString()));
        items.put("manifestKey", AttributeValue.fromS(item.manifestKey()));
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(items).build());
    }

    @Override
    public Optional<ShipmentItem> get(String shipmentRef) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("shipmentRef", AttributeValue.fromS(shipmentRef)))
                .consistentRead(true).build());
        if (response.hasItem()) {
            return Optional.of(toShipment(response.item()));
        }
        return Optional.empty();
    }

    @Override
    public boolean manifestExists(String manifestNo) {
        QueryResponse response = dynamo.query(QueryRequest.builder().tableName(table).indexName(MANIFEST_INDEX)
                .keyConditionExpression("manifestNo = :m")
                .expressionAttributeValues(Map.of(":m", AttributeValue.fromS(manifestNo)))
                .build());

       return (response.count() > 0);
    }

    @Override
    public List<ShipmentItem> listByCarrier(String carrierId) {
         QueryRequest response = QueryRequest.builder().tableName(table).indexName(CARRIER_DISPATCH_INDEX)
                .keyConditionExpression("carrierId = :c")
                .expressionAttributeValues(Map.of(":c", AttributeValue.fromS(carrierId)))
                 .scanIndexForward(true).build();

         return dynamo.queryPaginator(response).items().stream().map(this::toShipment).toList();
    }

    @Override
    public boolean remove(String shipmentRef) {
        DeleteItemResponse response =  dynamo.deleteItem(DeleteItemRequest.builder().tableName(table)
                .key(Map.of("shipmentRef", AttributeValue.fromS(shipmentRef))).returnValues(ReturnValue.ALL_OLD)
                .build());
        return (response.hasAttributes());
    }
}
