package md.utm.faf.pad.player.repository;

import md.utm.faf.pad.player.model.Player;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for players (allowed for this lab grade).
 *
 * The method names follow Spring Data conventions (findById, existsBy..., deleteById),
 * so later this class can be swapped for a JPA interface without touching the services.
 * ConcurrentHashMap = safe when several HTTP requests arrive at the same time.
 */
@Repository
public class PlayerRepository {

    private final Map<String, Player> playersById = new ConcurrentHashMap<>();

    public Player save(Player player) {
        playersById.put(player.getId(), player);
        return player;
    }

    public Optional<Player> findById(String id) {
        return Optional.ofNullable(playersById.get(id));
    }

    public Optional<Player> findByEmail(String email) {
        return playersById.values().stream()
                .filter(p -> p.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public boolean existsByUsername(String username) {
        return playersById.values().stream()
                .anyMatch(p -> p.getUsername().equalsIgnoreCase(username));
    }

    public void deleteById(String id) {
        playersById.remove(id);
    }
}
