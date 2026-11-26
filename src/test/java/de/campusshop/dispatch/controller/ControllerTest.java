package de.campusshop.dispatch.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import de.campusshop.dispatch.dto.CarrierDto;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.model.ShipmentState;
import de.campusshop.dispatch.service.ManifestReader;
import de.campusshop.dispatch.service.ShipmentQueryService;
import de.campusshop.dispatch.service.ShipmentRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ManifestController.class, ShipmentController.class})
public class ControllerTest {
    @TestConfiguration
    static class ClockConfig {
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

    private final DispatchManifest manifest = new DispatchManifest("1345-234r53",
            new CarrierDto("2345te-234r", "Textile Road", "president@textileroad.com"),
            LocalDate.of(2026, 11, 20), "Big Warehouse",
            List.of(new ParcelDto("TRF-1", new BigDecimal("2.5"))));

    @Test
    void valid201() throws Exception {
        ShipmentRecord record = new ShipmentRecord("56c-67h", "7623-24e2", "iu29-fef", LocalDate.of(2026, 11, 20),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.values()[0], 1,
                new BigDecimal("8.25"), "manifests/56c-67h.json", List.of());
        when(reader.read(any())).thenReturn(manifest);
        when(registration.register(eq(manifest), any())).thenReturn(record);

        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentRef").value("56c-67h"));
    }

    @Test
    void malformed400() throws Exception {
        when(reader.read(any())).thenThrow(new JsonParseException((JsonParser) null, "Unexpected end-of-input"));

        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_MANIFEST"));
    }

    @Test
    void duplicate409() throws Exception {
        when(reader.read(any())).thenReturn(manifest);
        when(registration.register(eq(manifest), any())).thenThrow(new DuplicateManifestException("7623-24e2"));

        mvc.perform(post("/dispatch/manifests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_MANIFEST"));
    }

    @Test
    void delete204() throws Exception {
        mvc.perform(delete("/shipments/{ref}", "56c-67h")).andExpect(status().isNoContent());

        verify(queries).delete("56c-67h");
    }

    @Test
    void unsupportedMethod() throws Exception {
        mvc.perform(put("/shipments/{ref}", "56c-67h"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_REJECTED"));
    }
}


