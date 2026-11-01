package md.utm.faf.pad.game.client;

/** What the Game Service needs from the Player Service. */
public interface PlayerServiceClient {

    /**
     * PATCH /api/players/{player_id}/xp on the Player Service.
     * The operationId makes the call safe to retry (the Player Service is idempotent).
     */
    void addXp(String playerId, int delta, String reason, String operationId);
}
