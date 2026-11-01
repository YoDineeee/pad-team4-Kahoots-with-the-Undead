package md.utm.faf.pad.player.dto;

import jakarta.validation.constraints.NotBlank;

/** JSON shapes for the friends endpoints. */
public final class FriendDtos {

    private FriendDtos() {}

    /** POST /api/players/{player_id}/friends */
    public record AddFriendRequest(@NotBlank String targetPlayerId) {}

    public record FriendshipResponse(String friendshipId, String targetPlayerId, String status) {}

    /**
     * One element of GET /api/players/{player_id}/friends.
     * "status" is the online presence (online/offline) like in the contract.
     * We also return friendship_id (needed for DELETE) and friendship_status (PENDING/ACCEPTED).
     */
    public record FriendResponse(
            String playerId,
            String username,
            String status,
            String friendshipId,
            String friendshipStatus
    ) {}
}
