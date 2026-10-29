package md.utm.faf.pad.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.game.client.PlayerServiceClient;
import md.utm.faf.pad.game.client.WorldServiceClient;
import md.utm.faf.pad.game.dto.SessionDtos.EndSessionResponse;
import md.utm.faf.pad.game.dto.SessionDtos.PlayerStateResponse;
import md.utm.faf.pad.game.dto.SessionDtos.SessionStateResponse;
import md.utm.faf.pad.game.dto.SessionDtos.StartSessionRequest;
import md.utm.faf.pad.game.dto.SessionDtos.StartSessionResponse;
import md.utm.faf.pad.game.exception.ApiException;
import md.utm.faf.pad.game.model.GameSession;
import md.utm.faf.pad.game.model.Lobby;
import md.utm.faf.pad.game.model.Phase;
import md.utm.faf.pad.game.model.PlayerState;
import md.utm.faf.pad.game.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Starting, inspecting and ending a game session (one "semester"). */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final int XP_PER_SURVIVED_CYCLE = 100;

    private final SessionRepository sessionRepository;
    private final LobbyService lobbyService;
    private final WorldServiceClient worldServiceClient;
    private final PlayerServiceClient playerServiceClient;
    private final Clock clock;
    private final int minPlayersToStart;

    public SessionService(SessionRepository sessionRepository,
                          LobbyService lobbyService,
                          WorldServiceClient worldServiceClient,
                          PlayerServiceClient playerServiceClient,
                          Clock clock,
                          @Value("${game.min-players-to-start}") int minPlayersToStart) {
        this.sessionRepository = sessionRepository;
        this.lobbyService = lobbyService;
        this.worldServiceClient = worldServiceClient;
        this.playerServiceClient = playerServiceClient;
        this.clock = clock;
        this.minPlayersToStart = minPlayersToStart;
    }

    public synchronized StartSessionResponse startSession(String lobbyId, String callerId, StartSessionRequest request) {
        Lobby lobby = lobbyService.findLobbyOrThrow(lobbyId);

        if (!lobby.isHost(callerId)) {
            throw ApiException.forbidden("NOT_LOBBY_HOST", "Only the host can start the game");
        }
        if (lobby.hasStarted()) {
            throw ApiException.conflict("SESSION_ALREADY_STARTED", "This lobby already has a session");
        }
        if (lobby.getPlayerIds().size() < minPlayersToStart) {
            throw ApiException.conflict("NOT_ENOUGH_PLAYERS",
                    "At least " + minPlayersToStart + " players are needed to start");
        }

        // Ask the (mocked) World Service for the map. Zombie configs would be fetched here too.
        String mapId = worldServiceClient.fetchMapForSession(lobbyId, lobby.getUniversity());

        List<PlayerState> players = lobby.getPlayerIds().stream()
                .map(PlayerState::new)
                .toList();

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        GameSession session = new GameSession("session-" + UUID.randomUUID(), lobbyId, mapId, now,
                request.cycleDurationSeconds(), request.semesterLengthCycles(), players);
        sessionRepository.save(session);
        lobby.setSessionId(session.getId());
        log.info("Session started: id={} lobby={} map={} players={} cycle={}s semester={} cycles", session.getId(), lobbyId, mapId, players.size(), request.cycleDurationSeconds(), request.semesterLengthCycles());

        return new StartSessionResponse(session.getId(), lobbyId, Phase.DAY.name(), 1, mapId, now);
    }

    public SessionStateResponse getSessionState(String sessionId) {
        GameSession session = findSessionOrThrow(sessionId);
        Instant now = clock.instant();

        List<PlayerStateResponse> players = session.getPlayers().stream()
                .map(p -> new PlayerStateResponse(p.getPlayerId(), p.getHealth(), p.getCurrentRoomId()))
                .toList();

        return new SessionStateResponse(
                session.getId(),
                session.getStatus().name(),
                session.phaseAt(now).name(),
                session.cycleNumberAt(now),
                session.phaseEndsAt(now).truncatedTo(ChronoUnit.SECONDS),
                aliveZombies(session, now),
                players);
    }

    /** Ends the semester: survivors get XP in the Player Service (mocked). */
    public synchronized EndSessionResponse endSession(String sessionId, String callerId) {
        GameSession session = findSessionOrThrow(sessionId);
        Lobby lobby = lobbyService.findLobbyOrThrow(session.getLobbyId());

        if (!lobby.isHost(callerId)) {
            throw ApiException.forbidden("NOT_LOBBY_HOST", "Only the host can end the game");
        }
        if (!session.isRunning()) {
            throw ApiException.conflict("SESSION_ALREADY_FINISHED", "This session has already ended");
        }

        Instant now = clock.instant();
        session.setStatus(GameSession.Status.FINISHED);
        session.setFinishedAt(now);

        int cyclesSurvived = Math.min(session.cycleNumberAt(now), session.getSemesterLengthCycles());
        List<String> survivors = session.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .map(PlayerState::getPlayerId)
                .toList();

        // The operation id is built from the session and player ids, so if this call is ever
        // retried the Player Service recognises it and does not give the XP twice.
        for (String playerId : survivors) {
            playerServiceClient.addXp(playerId, cyclesSurvived * XP_PER_SURVIVED_CYCLE,
                    "SEMESTER_SURVIVED", "session-end-" + sessionId + "-" + playerId);
        }
        // TODO (next labs): send the results to the Exam Service as well.
        log.info("Session ended: id={} by={} cycles_survived={} survivors={}", sessionId, callerId, cyclesSurvived, survivors);

        return new EndSessionResponse(sessionId, session.getStatus().name(), survivors, cyclesSurvived);
    }

    public GameSession findSessionOrThrow(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "No session with id " + sessionId));
    }

    /**
     * Placeholder until the Zombie Service exists: a few stragglers by day,
     * a horde that grows every night.
     */
    private int aliveZombies(GameSession session, Instant now) {
        if (!session.isRunning()) {
            return 0;
        }
        int cycle = session.cycleNumberAt(now);
        return session.phaseAt(now) == Phase.NIGHT ? cycle * 3 : cycle - 1;
    }
}
