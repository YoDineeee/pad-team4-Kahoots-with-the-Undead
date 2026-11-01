package md.utm.faf.pad.player.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Lab 3: GET /status and the request id / instance id headers. */
@SpringBootTest
@AutoConfigureMockMvc
class StatusEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void statusReportsServiceInstanceAndLoad() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("player-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.instance_id").isNotEmpty())
                .andExpect(jsonPath("$.requests_total").isNumber());
    }

    @Test
    void everyResponseCarriesRequestIdAndInstanceId() throws Exception {
        mvc.perform(get("/api/does-not-exist").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(header().exists("X-Instance-Id"));
    }
}
