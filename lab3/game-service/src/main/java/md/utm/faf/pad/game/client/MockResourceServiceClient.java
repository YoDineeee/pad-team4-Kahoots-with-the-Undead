package md.utm.faf.pad.game.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Fake Resource Service: a fixed reward table per action type. */
@Component
public class MockResourceServiceClient implements ResourceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MockResourceServiceClient.class);

    private static final Map<String, List<Reward>> REWARDS = Map.of(
            "chop_bench", List.of(new Reward("wood-01", 4)),
            "scavenge_canteen", List.of(new Reward("food-01", 12)),
            "clear_room", List.of(new Reward("metal-01", 2), new Reward("paper-01", 3)),
            "barricade_room", List.of(),
            "repair_base", List.of(),
            "upgrade_base", List.of()
    );

    @Override
    public List<Reward> rewardForCompletedAction(String actionId, String actionType) {
        log.info("[MOCK Resource Service] reward for action {} ({}): {}", actionId, actionType, REWARDS.getOrDefault(actionType, List.of()));
        return REWARDS.getOrDefault(actionType, List.of());
    }
}
