package de.campusshop.dispatch.controller;

import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.service.ManifestReader;
import de.campusshop.dispatch.service.ShipmentQueryService;
import de.campusshop.dispatch.service.ShipmentRegistrationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;


@WebMvcTest({ManifestController.class, ShipmentController.class})
public class ControllerReadTest {
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ManifestReader reader;
    @MockitoBean
    private ShipmentRegistrationService registration;
    @MockitoBean
    private ShipmentQueryService queries;

    private static ShipmentSummary summary(String ref, LocalDate date) {
        return new ManifestMapper().toSummary(new ShipmentItem(ref, "M-" + ref, "iu29-fef", date, Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 1, new BigDecimal("8.25"), "manifests/" + "iu29-fef" + "/" + ref + ".json"));
    }

    @Test
    void getShipmentReturnsTheSummary() throws Exception {
        when(queries.find("56c-67h")).thenReturn(summary("56c-67h", LocalDate.of(2026, 11, 20)));

        mvc.perform(get("/shipments/56c-67h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentRef").value("56c-67h"))
                .andExpect(jsonPath("$.dispatchDate").value("2026-11-20"))
                .andExpect(jsonPath("$.totalCharge").value(8.25));
    }

    @Test
    void getUnknownShipmentIs404WithAProblemReport() throws Exception {
        when(queries.find("nope")).thenThrow(new ShipmentNotFoundException("nope"));

        mvc.perform(get("/shipments/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHIPMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/shipments/nope"))
                .andExpect(jsonPath("$.occurredAt").value("2026-11-20T09:15:00Z"))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void getManifestReturnsTheExactBytes() throws Exception {
        byte[] bytes = "{ \"b\" : 2,\n\"a\":\"UK\" }".getBytes(StandardCharsets.UTF_8);
        when(queries.manifest("56c-67h")).thenReturn(bytes);

        mvc.perform(get("/shipments/56c-67h/manifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void carrierListKeepsTheServiceOrder() throws Exception {
        when(queries.listByCarrier("iu29-fef")).thenReturn(List.of(
                summary("old", LocalDate.of(2025, 1, 1)), summary("new", LocalDate.of(2026, 1, 1))));

        mvc.perform(get("/carriers/iu29-fef/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].shipmentRef").value("old"))
                .andExpect(jsonPath("$[1].shipmentRef").value("new"));
    }

    @Test
    void emptyCarrierListIsAnEmptyArray() throws Exception {
        when(queries.listByCarrier("FJ-3T")).thenReturn(List.of());

        mvc.perform(get("/carriers/FJ-3T/shipments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deleteOfUnknownRefIs404() throws Exception {
        doThrow(new ShipmentNotFoundException("nope")).when(queries).delete("nope");

        mvc.perform(delete("/shipments/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHIPMENT_NOT_FOUND"));
    }

    @Test
    void unknownPathIs404RequestRejected() throws Exception {
        mvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_REJECTED"));
    }

    @Test
    void unexpectedErrorIs500AndHidesTheCause() throws Exception {
        when(queries.find("56c-67h")).thenThrow(new IllegalStateException("secret-internal-detail"));

        mvc.perform(get("/shipments/56c-67h"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Unexpected server error"));
    }

    @Test
    @Tag("known-defect")
    void unacceptableAcceptHeaderIs406() throws Exception {
        when(queries.find("56c-67h")).thenReturn(summary("56c-67h", LocalDate.of(2026, 11, 20)));

        mvc.perform(get("/shipments/56c-67h").accept(MediaType.TEXT_XML))
                .andExpect(status().isNotAcceptable());
    }
}
