package md.utm.faf.pad.game.security;

import md.utm.faf.pad.game.exception.ApiException;

/** Helper for "you can only act as YOURSELF" checks (player_id in the body must match the JWT). */
public final class Ownership {

    private Ownership() {}

    public static void requireSelf(String callerId, String playerId) {
        if (!callerId.equals(playerId)) {
            throw ApiException.forbidden("NOT_YOUR_PLAYER",
                    "The JWT belongs to " + callerId + ", but the request acts as " + playerId);
        }
    }
}
