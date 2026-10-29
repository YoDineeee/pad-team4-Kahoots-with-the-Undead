package md.utm.faf.pad.player.model;

/**
 * A friend request from {@code requesterId} to {@code targetId}.
 * It starts as PENDING; a future "accept" endpoint would move it to ACCEPTED.
 */
public class Friendship {

    public enum Status { PENDING, ACCEPTED }

    private final String id;
    private final String requesterId;
    private final String targetId;
    private Status status = Status.PENDING;

    public Friendship(String id, String requesterId, String targetId) {
        this.id = id;
        this.requesterId = requesterId;
        this.targetId = targetId;
    }

    /** True if the given player is one of the two sides of this friendship. */
    public boolean involves(String playerId) {
        return requesterId.equals(playerId) || targetId.equals(playerId);
    }

    /** Given one side of the friendship, returns the other side. */
    public String otherSide(String playerId) {
        return requesterId.equals(playerId) ? targetId : requesterId;
    }

    public String getId() { return id; }
    public String getRequesterId() { return requesterId; }
    public String getTargetId() { return targetId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
