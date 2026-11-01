package com.example.resources.observability;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tells the Service Discovery that this instance exists (lab 3).
 *
 *   on startup and every 10 s:  POST   {SERVICE_DISCOVERY_URL}/register  {service, instance_id, url}
 *                               (the repeated calls are the "heartbeat": "I'm still alive")
 *   on shutdown:                DELETE {SERVICE_DISCOVERY_URL}/services/{service}/instances/{instance_id}
 *
 * If this instance crashes, the heartbeats stop and the Service Discovery removes it after 30 s.
 * Nothing happens when SERVICE_DISCOVERY_URL is empty (running on a laptop, unit tests).
 */
@Component
@EnableScheduling // turns on @Scheduled
public class ServiceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrar.class);

    private final HttpClient http = HttpClient.newHttpClient();
    private final InstanceInfo instance;
    private final String discoveryUrl;
    private final String myUrl;

    public ServiceRegistrar(InstanceInfo instance,
                            @Value("${service-discovery.url:}") String discoveryUrl,
                            @Value("${server.port}") int port) {
        this.instance = instance;
        this.discoveryUrl = discoveryUrl;
        this.myUrl = "http://" + instance.ipAddress() + ":" + port;
    }

    /** Runs right after startup, then every service-discovery.heartbeat-seconds (10). */
    @Scheduled(fixedRateString = "${service-discovery.heartbeat-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void sendHeartbeat() {
        if (discoveryUrl.isBlank()) {
            return;
        }
        String body = """
                {"service": "%s", "instance_id": "%s", "url": "%s"}
                """.formatted(instance.serviceName(), instance.instanceId(), myUrl);
        try {
            int status = send(HttpRequest.newBuilder(URI.create(discoveryUrl + "/register"))
                    .POST(BodyPublishers.ofString(body)));
            if (status == 201) {
                log.info("Registered with the Service Discovery as {} at {}", instance.instanceId(), myUrl);
            }
        } catch (Exception e) {
            log.warn("Could not reach the Service Discovery at {} ({}), retrying with the next heartbeat",
                    discoveryUrl, e.getClass().getSimpleName());
        }
    }

    /** Spring calls this when the application stops ("docker stop"). */
    @PreDestroy
    public void deregister() {
        if (discoveryUrl.isBlank()) {
            return;
        }
        String path = "/services/" + instance.serviceName() + "/instances/" + instance.instanceId();
        try {
            send(HttpRequest.newBuilder(URI.create(discoveryUrl + path)).DELETE());
            log.info("Deregistered from the Service Discovery");
        } catch (Exception e) {
            log.warn("Could not deregister: {}", e.getMessage());
        }
    }

    private int send(HttpRequest.Builder request) throws Exception {
        HttpRequest withDefaults = request
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(3))
                .build();
        return http.send(withDefaults, BodyHandlers.discarding()).statusCode();
    }
}
