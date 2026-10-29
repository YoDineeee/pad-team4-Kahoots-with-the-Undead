package md.utm.faf.pad.game.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One running game ("semester") started from a lobby.
 *
 * The day/night cycle is NOT driven by a background timer. Instead we store when the
 * session started and compute the current cycle from the elapsed time whenever someone asks.
 * Simple, and it survives restarts of the timer thread because there is no timer thread.
 *
 *   cycle_duration = 300 s  ->  [0..150) DAY 1, [150..300) NIGHT 1, [300..450) DAY 2, ...
 */
public class GameSession {

    public enum Status { RUNNING, FINISHED }

    private final String id;
    private final String lobbyId;
    private final String mapId;
    private final Instant startedAt;
    private final int cycleDurationSeconds;
    private final int semesterLengthCycles;
    private final List<PlayerState> players;
    private Status status = Status.RUNNING;
    private Instant finishedAt;

    public GameSession(String id, String lobbyId, String mapId, Instant startedAt,
                       int cycleDurationSeconds, int semesterLengthCycles, List<PlayerState> players) {
        this.id = id;
        this.lobbyId = lobbyId;
        this.mapId = mapId;
        this.startedAt = startedAt;
        this.cycleDurationSeconds = cycleDurationSeconds;
        this.semesterLengthCycles = semesterLengthCycles;
        this.players = players;
    }

    // ---------------------------------------------------------------- day/night math

    /** Seconds passed since start (frozen at the moment the session finished). */
    private long elapsedSeconds(Instant now) {
        Instant end = (finishedAt != null) ? finishedAt : now;
        return Duration.between(startedAt, end).toSeconds();
    }

    /** 1 for the first cycle, 2 for the second ... */
    public int cycleNumberAt(Instant now) {
        return (int) (elapsedSeconds(now) / cycleDurationSeconds) + 1;
    }

    public Phase phaseAt(Instant now) {
        long secondsIntoCycle = elapsedSeconds(now) % cycleDurationSeconds;
        return secondsIntoCycle < cycleDurationSeconds / 2 ? Phase.DAY : Phase.NIGHT;
    }

    /** When the current DAY or NIGHT phase ends. */
    public Instant phaseEndsAt(Instant now) {
        long half = cycleDurationSeconds / 2;
        long elapsed = elapsedSeconds(now);
        long phasesCompleted = elapsed / half;
        return startedAt.plusSeconds((phasesCompleted + 1) * half);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    // ---------------------------------------------------------------- getters / setters

    public String getId() { return id; }
    public String getLobbyId() { return lobbyId; }
    public String getMapId() { return mapId; }
    public Instant getStartedAt() { return startedAt; }
    public int getCycleDurationSeconds() { return cycleDurationSeconds; }
    public int getSemesterLengthCycles() { return semesterLengthCycles; }
    public List<PlayerState> getPlayers() { return players; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
