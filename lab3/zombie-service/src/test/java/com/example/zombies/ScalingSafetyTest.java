package com.example.zombies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lab 3: things that matter once several instances of the service run at the same time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScalingSafetyTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ZombieTypeRepository repository;

    @BeforeEach
    void emptyDatabase() {
        repository.deleteAll();
    }

    private static ZombieType tourist(String name) {
        ZombieType z = new ZombieType();
        z.setTypeId("tourist_zombie");
        z.setName(name);
        z.setHealth(80);
        z.setDamage(12);
        z.setSpeed(1.5);
        z.setLootTableId("loot-tourist-01");
        return z;
    }

    @Test
    void secondInsertOfTheSameIdIsRefusedByTheDatabaseInsteadOfOverwriting() {
        // What happens when another instance already created the type between our existsById() check and our save()
        repository.saveAndFlush(tourist("created by instance A"));

        assertThatThrownBy(() -> repository.saveAndFlush(tourist("created by instance B")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById("tourist_zombie").orElseThrow().getName()).isEqualTo("created by instance A");
    }

    @Test
    void updatesStillWork() {
        repository.saveAndFlush(tourist("Tourist Zombie"));
        ZombieType loaded = repository.findById("tourist_zombie").orElseThrow();
        loaded.setName("Renamed");
        repository.saveAndFlush(loaded);
        assertThat(repository.findById("tourist_zombie").orElseThrow().getName()).isEqualTo("Renamed");
    }

    @Test
    void statusEndpointSaysWhichInstanceAnswered() throws Exception {
        mvc.perform(get("/api/zombies/types"));
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Instance-Id"))
                .andExpect(jsonPath("$.service").value("zombie-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.instance_id").isNotEmpty())
                .andExpect(jsonPath("$.requests_total").isNumber());
    }
}
