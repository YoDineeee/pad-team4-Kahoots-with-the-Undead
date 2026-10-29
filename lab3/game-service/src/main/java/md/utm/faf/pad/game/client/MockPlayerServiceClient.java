package md.utm.faf.pad.game.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fake Player Service: only logs the XP that WOULD be sent. */
@Component
public class MockPlayerServiceClient implements PlayerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MockPlayerServiceClient.class);

    @Override
    public void addXp(String playerId, int delta, String reason, String operationId) {
        log.info("[MOCK Player Service] PATCH /api/players/{}/xp delta={} reason={} operation_id={}",
                playerId, delta, reason, operationId);
    }
}
