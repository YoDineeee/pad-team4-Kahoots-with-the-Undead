package com.example.zombies;

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
public class ZombieTypeService {

    private static final Logger log = LoggerFactory.getLogger(ZombieTypeService.class);

    private final ZombieTypeRepository repository;

    public ZombieTypeService(ZombieTypeRepository repository) {
        this.repository = repository;
    }

    public ZombieType create(ZombieType zombieType) {
        // POST must create, never overwrite an existing type.
        if (repository.existsById(zombieType.getTypeId())) {
            log.warn("Zombie type {} already exists, create rejected", zombieType.getTypeId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Zombie type already exists.");
        }
        // INSERT now: if another instance created the same id a moment ago, the database refuses it -> 409.
        ZombieType saved = repository.saveAndFlush(zombieType);
        log.info("Zombie type created: id={} name={} health={} damage={} speed={}", saved.getTypeId(), saved.getName(), saved.getHealth(), saved.getDamage(), saved.getSpeed());
        return saved;
    }

    public List<ZombieType> getAll() {
        log.debug("Listing all zombie types");
        return repository.findAll();
    }

    public ZombieType getById(String typeId) {
        return repository.findById(typeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zombie type not found."));
    }

    public ZombieType update(String typeId, ZombieTypeUpdate changes) {
        ZombieType zombieType = getById(typeId);

        // Check the text fields first, so nothing changes if the request is invalid.
        if (isBlankText(changes.getName()) || isBlankText(changes.getLootTableId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text fields cannot be blank.");
        }

        // Only copy the fields that were sent.
        if (changes.getName() != null) zombieType.setName(changes.getName());
        if (changes.getHealth() != null) zombieType.setHealth(changes.getHealth());
        if (changes.getDamage() != null) zombieType.setDamage(changes.getDamage());
        if (changes.getSpeed() != null) zombieType.setSpeed(changes.getSpeed());
        if (changes.getLootTableId() != null) zombieType.setLootTableId(changes.getLootTableId());
        if (changes.getBehaviours() != null) zombieType.setBehaviours(changes.getBehaviours());
        log.info("Zombie type updated: id={}", typeId);

        return repository.save(zombieType);
    }

    public void delete(String typeId) {
        if (!repository.existsById(typeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zombie type not found.");
        }
        repository.deleteById(typeId);
        log.info("Zombie type deleted: id={}", typeId);
    }

    // true when a value WAS sent but contains only spaces
    private boolean isBlankText(String value) {
        return value != null && value.isBlank();
    }
}
