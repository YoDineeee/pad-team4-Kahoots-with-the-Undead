package com.example.zombies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sends real HTTP requests to the controller (MockMvc) and checks the responses.
 * Profile "test" = in-memory H2 database instead of PostgreSQL (see application-test.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ZombieTypeApiTest {

    private static final String URL = "/api/zombies/types";
    private static final String TOURIST = """
            {
              "type_id": "tourist_zombie",
              "name": "Tourist Zombie",
              "health": 80,
              "damage": 12,
              "speed": 1.5,
              "loot_table_id": "loot-tourist-01",
              "behaviours": [
                {"trigger": "player_nearby", "action": "chase", "range_tiles": 5},
                {"trigger": "day_phase", "action": "hide", "location": "exam_hall"}
              ]
            }
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    ZombieTypeRepository repository;

    @BeforeEach
    void emptyDatabase() {
        repository.deleteAll();
    }

    // --- small helpers so each test reads like a sentence ---

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJson(String id, String body) throws Exception {
        return mvc.perform(patch(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // --- tests ---

    @Test
    void createThenGetReturnsAllFields() throws Exception {
        postJson(TOURIST).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type_id").value("tourist_zombie"));

        mvc.perform(get(URL + "/tourist_zombie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health").value(80))
                .andExpect(jsonPath("$.loot_table_id").value("loot-tourist-01"))
                .andExpect(jsonPath("$.behaviours[0].range_tiles").value(5))
                .andExpect(jsonPath("$.behaviours[0].location").doesNotExist())
                .andExpect(jsonPath("$.behaviours[1].location").value("exam_hall"));
    }

    @Test
    void listReturnsCreatedTypes() throws Exception {
        postJson(TOURIST);
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(get(URL + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Zombie type not found."));
        patchJson("missing", "{}").andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/missing")).andExpect(status().isNotFound());
    }

    @Test
    void duplicateIdReturns409AndKeepsOriginal() throws Exception {
        postJson(TOURIST);
        postJson(TOURIST.replace("\"health\": 80", "\"health\": 90")).andExpect(status().isConflict());
        mvc.perform(get(URL + "/tourist_zombie")).andExpect(jsonPath("$.health").value(80));
    }

    @Test
    void invalidCreateReturns400() throws Exception {
        postJson(TOURIST.replace("\"health\": 80", "\"health\": -1")).andExpect(status().isBadRequest());
        postJson(TOURIST.replace("\"range_tiles\": 5", "\"range_tiles\": -1")).andExpect(status().isBadRequest());
        postJson("{").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void patchChangesOnlySentFields() throws Exception {
        postJson(TOURIST);
        patchJson("tourist_zombie", "{\"health\": 95}").andExpect(status().isOk());

        mvc.perform(get(URL + "/tourist_zombie"))
                .andExpect(jsonPath("$.health").value(95))
                .andExpect(jsonPath("$.damage").value(12))
                .andExpect(jsonPath("$.behaviours.length()").value(2));
    }

    @Test
    void patchBehavioursReplacesTheWholeList() throws Exception {
        postJson(TOURIST);
        patchJson("tourist_zombie", """
                {"behaviours": [{"trigger": "noise", "action": "investigate"}]}
                """).andExpect(status().isOk());
        mvc.perform(get(URL + "/tourist_zombie"))
                .andExpect(jsonPath("$.behaviours.length()").value(1))
                .andExpect(jsonPath("$.behaviours[0].action").value("investigate"));

        patchJson("tourist_zombie", "{\"behaviours\": []}").andExpect(status().isOk());
        mvc.perform(get(URL + "/tourist_zombie")).andExpect(jsonPath("$.behaviours.length()").value(0));
    }

    @Test
    void invalidPatchReturns400AndChangesNothing() throws Exception {
        postJson(TOURIST);
        patchJson("tourist_zombie", "{\"health\": -5}").andExpect(status().isBadRequest());
        patchJson("tourist_zombie", "{\"name\": \" \", \"health\": 99}").andExpect(status().isBadRequest());
        patchJson("tourist_zombie", "{\"type_id\": \"other\"}").andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/tourist_zombie")).andExpect(jsonPath("$.health").value(80));
    }

    @Test
    void deleteRemovesTheType() throws Exception {
        postJson(TOURIST);
        mvc.perform(delete(URL + "/tourist_zombie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        mvc.perform(get(URL + "/tourist_zombie")).andExpect(status().isNotFound());
    }
}
