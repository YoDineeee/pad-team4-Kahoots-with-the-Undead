package md.utm.faf.pad.game.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Fake World Service: pretends a map was generated and returns a random map id. */
@Component
public class MockWorldServiceClient implements WorldServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MockWorldServiceClient.class);

    @Override
    public String fetchMapForSession(String lobbyId, String university) {
        String mapId = "map-" + UUID.randomUUID();
        log.info("[MOCK World Service] map {} for lobby {} ({})", mapId, lobbyId, university);
        return mapId;
    }
}
