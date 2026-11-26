package de.campusshop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Drives the whole API in-process with the {@code inmemory} profile (in-memory S3 and DynamoDB
 * replacements) and simulated tariffs, so no Docker is needed.
 */
@SpringBootTest(properties = "tariff-service.mode=simulation")
@ActiveProfiles("inmemory")
@AutoConfigureMockMvc
class ShipmentApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShipmentRepository shipments;


    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void registerReadListAndDelete() throws Exception {
        String json = """
                {"manifestNo": "5467v-6578", "carrier": {"carrierId": "7687-78ed", "name": "Textile Road",
                "contactEmail": "president@textileroad.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}, {"tariffCode": "TRF-2", "weightKg": 0.8}],
                "carrierInternalNotes": "must be preserved"}
                """;

        String response = mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String shipmentRef = new ObjectMapper().readTree(response).get("shipmentRef").asText();
        mvc.perform(get("/shipments/{ref}", shipmentRef)).andExpect(status().isOk());
        mvc.perform(get("/shipments/{ref}/manifest", shipmentRef))
                .andExpect(content().string(containsString("carrierInternalNotes")))
                .andExpect(status().isOk());
        mvc.perform(get("/carriers/{carrierId}/shipments", "7687-78ed"))
                .andExpect(content().string(containsString(shipmentRef)))
                .andExpect(status().isOk());
        mvc.perform(delete("/shipments/{ref}", shipmentRef)).andExpect(status().isNoContent());
        mvc.perform(get("/shipments/{ref}", shipmentRef)).andExpect(status().isNotFound());
        assertThat(shipments.get(shipmentRef)).isEmpty();
    }

    @Test
    void invalidManifestsAreRejected() throws Exception {
        String json = """
                {"manifestNo": "57v-6578", "carrier": {"name": "Textile Road",
                "contactEmail": "president@textileroad.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}, {"tariffCode": "TRF-2", "weightKg": 0.8}],
                "carrierInternalNotes": "must be preserved"}
                """;
        String json1 = """
                {"manifestNo": "547v-6578", "carrier": {"carrierId": "7687-78ed", "name": "Textile Road",
                "contactEmail": "wrong-email-com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}, {"tariffCode": "TRF-2", "weightKg": 0.8}],
                "carrierInternalNotes": "must be preserved"}
                """;
        String json2 = """
                {"manifestNo": "467v-6578", "carrier": {"carrierId": "7687-78ed", "name": "Textile Road",
                "contactEmail": "president@textileroad.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [], "carrierInternalNotes": "must be preserved"}
                """;
        String json3 = """
                {"manifestNo": "5467v-658", "carrier": {"carrierId": "7687-78ed", "name": "Textile Road",
                "contactEmail": "president@textileroad.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 0}, {"tariffCode": "TRF-2", "weightKg": 41}],
                "carrierInternalNotes": "must be preserved"}
                """;

        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json1)).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json2)).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json3)).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(status().isBadRequest());

    }

    @Test
    void duplicateManifestIsAConflict() throws Exception {
        String json = """
                {"manifestNo": "5467v-78", "carrier": {"carrierId": "7687-78ed", "name": "Textile Road",
                "contactEmail": "president@textileroad.com"}, "dispatchDate": "2026-11-20", "originWarehouse": "Big Warehouse",
                "parcels": [{"tariffCode": "TRF-1", "weightKg": 2.5}, {"tariffCode": "TRF-2", "weightKg": 0.8}],
                "carrierInternalNotes": "must be preserved"}
                """;

        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isCreated());
        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(jsonPath("$.errorCode").value("DUPLICATE_MANIFEST"))
                .andExpect(status().isConflict());
    }
}
