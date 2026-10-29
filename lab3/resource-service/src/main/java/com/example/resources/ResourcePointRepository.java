package com.example.resources;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for ResourcePoint. The primary key (point_id) is a String.
 * Spring builds the SQL from the method names:
 * findByRoomId(...) -> SELECT ... WHERE room_id = ?
 */
public interface ResourcePointRepository extends JpaRepository<ResourcePoint, String> {

    List<ResourcePoint> findByRoomId(String roomId);

    List<ResourcePoint> findByResource(String resource);

    List<ResourcePoint> findByRoomIdAndResource(String roomId, String resource);
}
