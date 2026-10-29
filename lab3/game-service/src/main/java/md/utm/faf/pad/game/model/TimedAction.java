package md.utm.faf.pad.game.model;

import java.time.Duration;
import java.time.Instant;

/**
 * An asynchronous player action such as "chop bench for 10 minutes".
 *
 * Like the day/night cycle, progress is computed from timestamps:
 * we never need a thread that sleeps for 10 minutes.
 */
public class TimedAction {

    public enum Status {
        IN_PROGRESS, COMPLETED, CANCELLED;

        /** The contract uses lower-case values: "in_progress", "completed", "cancelled". */
        public String json() {
            return name().toLowerCase();
        }
    }

    private final String id;
    private final String lobbyId;
    private final String playerId;
    private final String actionType;
    private final String target;
    private final Instant startedAt;
    private final Instant completesAt;
    private Instant cancelledAt; // null = not cancelled

    public TimedAction(String id, String lobbyId, String playerId, String actionType,
                       String target, Instant startedAt, int durationSeconds) {
        this.id = id;
        this.lobbyId = lobbyId;
        this.playerId = playerId;
        this.actionType = actionType;
        this.target = target;
        this.startedAt = startedAt;
        this.completesAt = startedAt.plusSeconds(durationSeconds);
    }

    public Status statusAt(Instant now) {
        if (cancelledAt != null) {
            return Status.CANCELLED;
        }
        return now.isBefore(completesAt) ? Status.IN_PROGRESS : Status.COMPLETED;
    }

    public long elapsedSecondsAt(Instant now) {
        // Progress stops at whichever comes first: now, completion or cancellation.
        Instant end = now.isBefore(completesAt) ? now : completesAt;
        if (cancelledAt != null && cancelledAt.isBefore(end)) {
            end = cancelledAt;
        }
        return Duration.between(startedAt, end).toSeconds();
    }

    public int percentAt(Instant now) {
        long total = Duration.between(startedAt, completesAt).toSeconds();
        return (int) (elapsedSecondsAt(now) * 100 / total);
    }

    public void cancel(Instant at) {
        this.cancelledAt = at;
    }

    public String getId() { return id; }
    public String getLobbyId() { return lobbyId; }
    public String getPlayerId() { return playerId; }
    public String getActionType() { return actionType; }
    public String getTarget() { return target; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletesAt() { return completesAt; }
}
