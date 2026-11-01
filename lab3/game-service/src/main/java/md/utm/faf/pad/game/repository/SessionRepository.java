package md.utm.faf.pad.game.repository;

import md.utm.faf.pad.game.model.GameSession;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for game sessions. */
@Repository
public class SessionRepository {

    private final Map<String, GameSession> sessionsById = new ConcurrentHashMap<>();

    public GameSession save(GameSession session) {
        sessionsById.put(session.getId(), session);
        return session;
    }

    public Optional<GameSession> findById(String id) {
        return Optional.ofNullable(sessionsById.get(id));
    }
}
