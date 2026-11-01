package com.example.resources;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Lab 3: GET /status counts the requests this instance handled. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void statusCountsRequestsButNotItself() throws Exception {
        mvc.perform(get("/api/resources/points")).andExpect(status().isOk());
        mvc.perform(get("/api/resources/points")).andExpect(status().isOk());
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Instance-Id"))
                .andExpect(jsonPath("$.service").value("resource-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.requests_total").value(2));
    }
}
