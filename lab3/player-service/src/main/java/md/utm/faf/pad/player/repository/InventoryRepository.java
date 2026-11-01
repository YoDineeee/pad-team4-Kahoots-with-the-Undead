package md.utm.faf.pad.player.repository;

import md.utm.faf.pad.player.model.InventoryItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for inventory items. */
@Repository
public class InventoryRepository {

    private final Map<String, InventoryItem> itemsById = new ConcurrentHashMap<>();

    public InventoryItem save(InventoryItem item) {
        itemsById.put(item.getId(), item);
        return item;
    }

    public Optional<InventoryItem> findById(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public List<InventoryItem> findByPlayerId(String playerId) {
        return itemsById.values().stream()
                .filter(item -> item.getPlayerId().equals(playerId))
                .toList();
    }

    public Optional<InventoryItem> findByPlayerIdAndCode(String playerId, String code) {
        return findByPlayerId(playerId).stream()
                .filter(item -> item.getCode().equals(code))
                .findFirst();
    }

    public void deleteByPlayerId(String playerId) {
        itemsById.values().removeIf(item -> item.getPlayerId().equals(playerId));
    }
}
