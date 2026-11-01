package md.utm.faf.pad.player.repository;

import md.utm.faf.pad.player.model.Friendship;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for friendships / friend requests. */
@Repository
public class FriendshipRepository {

    private final Map<String, Friendship> friendshipsById = new ConcurrentHashMap<>();

    public Friendship save(Friendship friendship) {
        friendshipsById.put(friendship.getId(), friendship);
        return friendship;
    }

    public Optional<Friendship> findById(String id) {
        return Optional.ofNullable(friendshipsById.get(id));
    }

    /** All friendships where the player is either the requester or the target. */
    public List<Friendship> findAllInvolving(String playerId) {
        return friendshipsById.values().stream()
                .filter(f -> f.involves(playerId))
                .toList();
    }

    public boolean existsBetween(String playerA, String playerB) {
        return friendshipsById.values().stream()
                .anyMatch(f -> f.involves(playerA) && f.involves(playerB));
    }

    public void deleteById(String id) {
        friendshipsById.remove(id);
    }

    public void deleteAllInvolving(String playerId) {
        friendshipsById.values().removeIf(f -> f.involves(playerId));
    }
}
