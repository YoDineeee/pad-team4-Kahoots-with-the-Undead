package com.example.resources;

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
@RequestMapping("/api/resources/points")
public class ResourcePointController {

    private final ResourcePointService service;

    public ResourcePointController(ResourcePointService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourcePoint create(@Valid @RequestBody ResourcePoint point) {
        return service.create(point);
    }

    // Optional filters: /api/resources/points?room_id=lab-204&resource=metal-01
    @GetMapping
    public List<ResourcePoint> getAll(@RequestParam(name = "room_id", required = false) String roomId,
                                      @RequestParam(name = "resource", required = false) String resource) {
        return service.getAll(roomId, resource);
    }

    @GetMapping("/{point_id}")
    public ResourcePoint getById(@PathVariable("point_id") String pointId) {
        return service.getById(pointId);
    }

    @PatchMapping("/{point_id}")
    public ResourcePoint update(@PathVariable("point_id") String pointId,
                                @Valid @RequestBody ResourcePointUpdate changes) {
        return service.update(pointId, changes);
    }

    @DeleteMapping("/{point_id}")
    public Map<String, Object> delete(@PathVariable("point_id") String pointId) {
        service.delete(pointId);
        return Map.of("point_id", pointId, "deleted", true);
    }
}
