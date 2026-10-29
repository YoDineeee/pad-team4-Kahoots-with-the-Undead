package md.utm.faf.pad.player.model;

/**
 * The levelling rule, kept in ONE place.
 *
 * Rule: every level costs 500 XP.
 *   0    .. 499  XP -> level 1
 *   500  .. 999  XP -> level 2
 *   3420 XP        -> level 7 (80 XP left until level 8 at 3500)
 *   3420 + 250 XP  -> 3670 XP -> level 8, leveled_up = true   (matches the PATCH /xp contract example)
 *
 * Note: the contract's GET /xp example says "xp_to_next_level": 580, but no rule can give
 * both that AND level 8 at 3670 XP, so we follow the PATCH example.
 */
public final class Progression {

    public static final int XP_PER_LEVEL = 500;

    private Progression() {
        // utility class, no instances
    }

    public static int levelFor(int xp) {
        return xp / XP_PER_LEVEL + 1;
    }

    public static int xpToNextLevel(int xp) {
        int xpNeededForNextLevel = levelFor(xp) * XP_PER_LEVEL;
        return xpNeededForNextLevel - xp;
    }
}
