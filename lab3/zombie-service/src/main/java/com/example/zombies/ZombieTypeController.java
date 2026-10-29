package com.example.zombies;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP endpoints. Each method receives the request, calls the service, and returns JSON.
 * @Valid checks the validation annotations before the method runs (400 if they fail).
 */
@RestController
@RequestMapping("/api/zombies/types")
public class ZombieTypeController {

    private final ZombieTypeService service;

    public ZombieTypeController(ZombieTypeService service) {
        this.service = service;
    }
//
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZombieType create(@Valid @RequestBody ZombieType zombieType) {
        return service.create(zombieType);
    }

    @GetMapping
    public List<ZombieType> getAll() {
        return service.getAll();
    }

    @GetMapping("/{type_id}")
    public ZombieType getById(@PathVariable("type_id") String typeId) {
        return service.getById(typeId);
    }

    @PatchMapping("/{type_id}")
    public ZombieType update(@PathVariable("type_id") String typeId,
                             @Valid @RequestBody ZombieTypeUpdate changes) {
        return service.update(typeId, changes);
    }
//
    @DeleteMapping("/{type_id}")
    public Map<String, Object> delete(@PathVariable("type_id") String typeId) {
        service.delete(typeId);
        return Map.of("type_id", typeId, "deleted", true);
    }
}
