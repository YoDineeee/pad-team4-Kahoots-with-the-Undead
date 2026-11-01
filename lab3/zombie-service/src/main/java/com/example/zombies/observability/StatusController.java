package com.example.zombies.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /status - is this instance alive? Same answer shape in every service:
 *
 *   { "service": "zombie-service", "instance_id": "zombie-service-3fa1c2", "status": "UP",
 *     "uptime_seconds": 512, "requests_total": 1532 }
 */
@RestController
public class StatusController {

    private final Instant startedAt = Instant.now();
    private final InstanceInfo instance;
    private final RequestLoggingFilter requests;

    public StatusController(InstanceInfo instance, RequestLoggingFilter requests) {
        this.instance = instance;
        this.requests = requests;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "service", instance.serviceName(),
                "instance_id", instance.instanceId(),
                "status", "UP",
                "uptime_seconds", Duration.between(startedAt, Instant.now()).toSeconds(),
                "requests_total", requests.requestsTotal());
    }
}
