package com.example.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The business rules. The controller only handles HTTP; the repository only talks to the database.
 * Throwing a ResponseStatusException turns into an error response (see ApiExceptionHandler).
 */
@Service
public class ResourcePointService {

    private static final Logger log = LoggerFactory.getLogger(ResourcePointService.class);

    private final ResourcePointRepository repository;

    public ResourcePointService(ResourcePointRepository repository) {
        this.repository = repository;
    }

    public ResourcePoint create(ResourcePoint point) {
        checkAmountFits(point.getAmount(), point.getMaxAmount());
        ResourcePoint saved = repository.save(point);
        log.info("Resource point created: id={} room={} resource={} amount={}/{}", saved.getPointId(), saved.getRoomId(), saved.getResource(), saved.getAmount(), saved.getMaxAmount());
        return saved;
    }

    // Both filters are optional: null means "do not filter by this".
    public List<ResourcePoint> getAll(String roomId, String resource) {
        log.debug("Listing resource points room={} resource={}", roomId, resource);
        if (roomId != null && resource != null) return repository.findByRoomIdAndResource(roomId, resource);
        if (roomId != null) return repository.findByRoomId(roomId);
        if (resource != null) return repository.findByResource(resource);
        return repository.findAll();
    }

    public ResourcePoint getById(String pointId) {
        return repository.findById(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource point not found."));
    }

    public ResourcePoint update(String pointId, ResourcePointUpdate changes) {
        ResourcePoint point = getById(pointId);

        // Check everything first, so nothing changes if the request is invalid.
        if (changes.getResource() != null && changes.getResource().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource cannot be blank.");
        }
        // why not amount changes
        if (changes.getMaxAmount() != null) {
            checkAmountFits(point.getAmount(), changes.getMaxAmount());
        }

        // Only copy the fields that were sent.
        if (changes.getResource() != null) point.setResource(changes.getResource());
        if (changes.getMaxAmount() != null) point.setMaxAmount(changes.getMaxAmount());
        log.info("Resource point updated: id={} resource={} max_amount={}", pointId, point.getResource(), point.getMaxAmount());

        return repository.save(point);
    }

    public void delete(String pointId) {
        if (!repository.existsById(pointId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource point not found.");
        }
        repository.deleteById(pointId);
        log.info("Resource point deleted: id={}", pointId);
    }

    // A point can never hold more than its maximum.
    private void checkAmountFits(int amount, int maxAmount) {
        if (amount > maxAmount) {
            log.warn("Amount {} does not fit max_amount {}", amount, maxAmount);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be between zero and max_amount.");
        }
    }
}
