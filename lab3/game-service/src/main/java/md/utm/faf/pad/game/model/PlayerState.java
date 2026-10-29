package md.utm.faf.pad.game.model;

/** The short-lived, in-game state of one player inside a session (not their global profile). */
public class PlayerState {

    public static final int MAX_HEALTH = 100;
    public static final String STARTING_ROOM = "room-faf-cab"; // everybody wakes up in FAF Cab

    private final String playerId;
    private int health = MAX_HEALTH;
    private String currentRoomId = STARTING_ROOM;

    public PlayerState(String playerId) {
        this.playerId = playerId;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public String getPlayerId() { return playerId; }

    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }

    public String getCurrentRoomId() { return currentRoomId; }
    public void setCurrentRoomId(String currentRoomId) { this.currentRoomId = currentRoomId; }
}
