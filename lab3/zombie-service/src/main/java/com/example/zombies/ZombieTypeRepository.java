package com.example.zombies;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for ZombieType. The primary key (type_id) is a String.
 * Spring writes the implementation for us: save, findById, findAll, existsById, deleteById, ...
 */
public interface ZombieTypeRepository extends JpaRepository<ZombieType, String> {
}
