package md.utm.faf.pad.player.security;

import md.utm.faf.pad.player.exception.ApiException;

/** Helper for "you can only change YOUR OWN account" checks. */
public final class Ownership {

    private Ownership() {}

    public static void requireSelf(String callerId, String playerId) {
        if (!callerId.equals(playerId)) {
            throw ApiException.forbidden("NOT_YOUR_ACCOUNT",
                    "The JWT belongs to " + callerId + ", not to " + playerId);
        }
    }
}
