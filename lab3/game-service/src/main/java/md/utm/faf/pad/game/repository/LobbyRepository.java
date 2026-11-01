package md.utm.faf.pad.game.repository;

import md.utm.faf.pad.game.model.Lobby;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for lobbies. */
@Repository
public class LobbyRepository {

    private final Map<String, Lobby> lobbiesById = new ConcurrentHashMap<>();

    public Lobby save(Lobby lobby) {
        lobbiesById.put(lobby.getId(), lobby);
        return lobby;
    }

    public Optional<Lobby> findById(String id) {
        return Optional.ofNullable(lobbiesById.get(id));
    }

    public void deleteById(String id) {
        lobbiesById.remove(id);
    }
}
