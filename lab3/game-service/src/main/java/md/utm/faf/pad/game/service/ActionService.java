package md.utm.faf.pad.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.game.client.ResourceServiceClient;
import md.utm.faf.pad.game.client.ResourceServiceClient.Reward;
import md.utm.faf.pad.game.dto.ActionDtos.ActionStartedResponse;
import md.utm.faf.pad.game.dto.ActionDtos.ActionStatusResponse;
import md.utm.faf.pad.game.dto.ActionDtos.StartActionRequest;
import md.utm.faf.pad.game.exception.ApiException;
import md.utm.faf.pad.game.model.GameSession;
import md.utm.faf.pad.game.model.Lobby;
import md.utm.faf.pad.game.model.TimedAction;
import md.utm.faf.pad.game.repository.ActionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Timed player actions: chop benches, scavenge the canteen, barricade rooms ... */
@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    public static final Set<String> ACTION_TYPES = Set.of(
            "chop_bench", "scavenge_canteen", "clear_room", "barricade_room", "repair_base", "upgrade_base");

    private final ActionRepository actionRepository;
    private final LobbyService lobbyService;
    private final SessionService sessionService;
    private final ResourceServiceClient resourceServiceClient;
    private final Clock clock;

    public ActionService(ActionRepository actionRepository,
                         LobbyService lobbyService,
                         SessionService sessionService,
                         ResourceServiceClient resourceServiceClient,
                         Clock clock) {
        this.actionRepository = actionRepository;
        this.lobbyService = lobbyService;
        this.sessionService = sessionService;
        this.resourceServiceClient = resourceServiceClient;
        this.clock = clock;
    }

    /**
     * Starts the action and returns immediately (HTTP 202 Accepted).
     * The client can then poll GET .../actions/{id} (or listen on the WebSocket in a later lab).
     */
    public synchronized ActionStartedResponse startAction(String lobbyId, StartActionRequest request) {
        Lobby lobby = lobbyService.findLobbyOrThrow(lobbyId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        if (!lobby.hasPlayer(request.playerId())) {
            throw ApiException.forbidden("NOT_IN_LOBBY", "Player is not part of this lobby");
        }
        if (!lobby.hasStarted() || !sessionService.findSessionOrThrow(lobby.getSessionId()).isRunning()) {
            throw ApiException.conflict("SESSION_NOT_RUNNING", "Actions can only be started during a running game");
        }
        if (!ACTION_TYPES.contains(request.actionType())) {
            throw ApiException.badRequest("UNKNOWN_ACTION_TYPE", "action_type must be one of " + ACTION_TYPES);
        }
        if (actionRepository.findInProgressByPlayerId(request.playerId(), now).isPresent()) {
            throw ApiException.conflict("ACTION_ALREADY_ACTIVE", "Player already has an active action.");
        }

        TimedAction action = actionRepository.save(new TimedAction("action-" + UUID.randomUUID(), lobbyId,
                request.playerId(), request.actionType(), request.target(), now, request.durationSeconds()));

        log.info("Action started: id={} lobby={} player={} type={} target={} duration={}s", action.getId(), lobbyId, request.playerId(), request.actionType(), request.target(), request.durationSeconds());
        return new ActionStartedResponse(action.getId(), action.statusAt(now).json(),
                action.getStartedAt(), action.getCompletesAt());
    }

    /** Polling fallback for when the WebSocket dropped. */
    public ActionStatusResponse getActionStatus(String sessionId, String actionId) {
        GameSession session = sessionService.findSessionOrThrow(sessionId);
        TimedAction action = actionRepository.findById(actionId)
                .filter(a -> a.getLobbyId().equals(session.getLobbyId()))
                .orElseThrow(() -> ApiException.notFound("ACTION_NOT_FOUND",
                        "Session " + sessionId + " has no action " + actionId));

        Instant now = clock.instant();
        TimedAction.Status status = action.statusAt(now);

        // The reward is decided by the Resource Service; we only report it.
        List<Reward> reward = (status == TimedAction.Status.COMPLETED)
                ? resourceServiceClient.rewardForCompletedAction(action.getId(), action.getActionType())
                : null;

        return new ActionStatusResponse(action.getId(), action.getPlayerId(), action.getActionType(),
                action.getTarget(), status.json(), action.percentAt(now), action.elapsedSecondsAt(now),
                action.getStartedAt(), action.getCompletesAt(), reward);
    }

    public synchronized void cancelAction(String actionId, String callerId) {
        TimedAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> ApiException.notFound("ACTION_NOT_FOUND", "No action with id " + actionId));

        if (!action.getPlayerId().equals(callerId)) {
            throw ApiException.forbidden("NOT_YOUR_ACTION", "You can only cancel your own actions");
        }
        Instant now = clock.instant();
        if (action.statusAt(now) != TimedAction.Status.IN_PROGRESS) {
            throw ApiException.conflict("ACTION_NOT_IN_PROGRESS", "Only an in-progress action can be cancelled");
        }
        action.cancel(now);
        log.info("Action cancelled: id={} by player={}", actionId, callerId);
    }
}
