package md.utm.faf.pad.game.repository;

import md.utm.faf.pad.game.model.TimedAction;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for timed actions. */
@Repository
public class ActionRepository {

    private final Map<String, TimedAction> actionsById = new ConcurrentHashMap<>();

    public TimedAction save(TimedAction action) {
        actionsById.put(action.getId(), action);
        return action;
    }

    public Optional<TimedAction> findById(String id) {
        return Optional.ofNullable(actionsById.get(id));
    }

    /** The action the player is busy with right now, if any. */
    public Optional<TimedAction> findInProgressByPlayerId(String playerId, Instant now) {
        return actionsById.values().stream()
                .filter(action -> action.getPlayerId().equals(playerId))
                .filter(action -> action.statusAt(now) == TimedAction.Status.IN_PROGRESS)
                .findFirst();
    }
}
