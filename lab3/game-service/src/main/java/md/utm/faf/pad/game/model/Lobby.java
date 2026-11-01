package md.utm.faf.pad.game.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A waiting room where players gather before the host starts a game session.
 * Once started, {@code sessionId} points to the running {@link GameSession}.
 */
public class Lobby {

    private final String id;
    private final String name;
    private final String university;
    private final int maxPlayers;
    private final List<String> playerIds = new ArrayList<>();
    private String hostId;
    private String sessionId; // null until the host starts the game

    public Lobby(String id, String name, String hostId, String university, int maxPlayers) {
        this.id = id;
        this.name = name;
        this.hostId = hostId;
        this.university = university;
        this.maxPlayers = maxPlayers;
        this.playerIds.add(hostId); // the host is the first player in the lobby
    }

    public boolean isFull() {
        return playerIds.size() >= maxPlayers;
    }

    public boolean hasPlayer(String playerId) {
        return playerIds.contains(playerId);
    }

    public boolean isHost(String playerId) {
        return hostId.equals(playerId);
    }

    public boolean hasStarted() {
        return sessionId != null;
    }

    public void addPlayer(String playerId) {
        playerIds.add(playerId);
    }

    /** Removes the player; if the host leaves, the next player in the list becomes host. */
    public void removePlayer(String playerId) {
        playerIds.remove(playerId);
        if (isHost(playerId) && !playerIds.isEmpty()) {
            hostId = playerIds.get(0);
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUniversity() { return university; }
    public int getMaxPlayers() { return maxPlayers; }
    public String getHostId() { return hostId; }

    /** Returns a copy so nobody can modify the lobby's list from outside. */
    public List<String> getPlayerIds() { return List.copyOf(playerIds); }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
