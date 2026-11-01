package md.utm.faf.pad.game.client;

import java.util.List;

/**
 * What the Game Service needs from the Resource Service.
 * Contract: "Game Service manages 'the player is scavenging for 5 minutes' while
 * Resource Service owns 'the player received 12 food when the action completed'".
 */
public interface ResourceServiceClient {

    record Reward(String itemId, int count) {}

    /** The reward granted for a completed action. Idempotent per actionId. */
    List<Reward> rewardForCompletedAction(String actionId, String actionType);
}
