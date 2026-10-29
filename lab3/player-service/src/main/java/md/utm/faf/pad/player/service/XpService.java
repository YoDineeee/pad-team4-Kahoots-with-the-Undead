package md.utm.faf.pad.player.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateXpRequest;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateXpResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.XpResponse;
import md.utm.faf.pad.player.model.Player;
import md.utm.faf.pad.player.model.Progression;
import md.utm.faf.pad.player.repository.IdempotencyStore;
import md.utm.faf.pad.player.repository.PlayerRepository;
import org.springframework.stereotype.Service;

/**
 * XP and levels. XP is changed by OTHER services:
 *   Game Service (kills), Exam Service (passed exams), Tourist Zombies (stealing XP).
 */
@Service
public class XpService {

    private static final Logger log = LoggerFactory.getLogger(XpService.class);

    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final IdempotencyStore idempotencyStore;

    public XpService(PlayerService playerService, PlayerRepository playerRepository,
                     IdempotencyStore idempotencyStore) {
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.idempotencyStore = idempotencyStore;
    }

    public XpResponse getXp(String playerId) {
        Player player = playerService.findPlayerOrThrow(playerId);
        int xp = player.getXp();
        return new XpResponse(player.getId(), xp, player.getLevel(), Progression.xpToNextLevel(xp));
    }

    /**
     * Adds (delta > 0) or removes (delta < 0) XP.
     * Idempotent: the same operation_id sent twice changes the XP only once.
     * synchronized: two XP updates for the same moment can't overwrite each other.
     */
    public synchronized UpdateXpResponse updateXp(String playerId, UpdateXpRequest request) {
        playerService.findPlayerOrThrow(playerId); // 404 before anything else

        return idempotencyStore.executeOnce("xp:" + request.operationId(), () -> {
            Player player = playerService.findPlayerOrThrow(playerId);
            int oldLevel = player.getLevel();

            // XP never goes below 0, even if a Tourist Zombie steals more than the player has.
            int newXp = Math.max(0, player.getXp() + request.delta());
            player.setXp(newXp);
            playerRepository.save(player);

            boolean leveledUp = player.getLevel() > oldLevel;
            log.info("XP updated: player={} delta={} xp={} level {} -> {} operation={}", playerId, request.delta(), newXp, oldLevel, player.getLevel(), request.operationId());
            return new UpdateXpResponse(player.getId(), newXp, player.getLevel(), leveledUp);
        });
    }
}
